package app.wave.music.video

import android.content.pm.ActivityInfo
import android.net.ConnectivityManager
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import app.wave.music.playback.StreamResolver
import app.wave.music.playback.VideoEngine
import app.wave.music.playback.VideoState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/**
 * Music videos on Android: their own ExoPlayer (the music player is left alone), playing YouTube's
 * HLS playlist, which switches picture size by itself as your connection changes.
 */
class AndroidVideo(private val activity: ComponentActivity, private val big: () -> Boolean) : VideoEngine {
    private val scope = MainScope()
    private val _state = MutableStateFlow(VideoState())
    override val state: StateFlow<VideoState> = _state
    private var loading: Job? = null
    private val http = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build()

    private var exo: ExoPlayer? = null
    private val player: ExoPlayer
        get() = exo ?: ExoPlayer.Builder(activity).build().also { p ->
            exo = p
            p.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            p.setHandleAudioBecomingNoisy(true)
            p.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = publish()
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        _state.value = _state.value.copy(width = videoSize.width, height = videoSize.height, showing = videoSize.height)
                    }
                }
                override fun onTracksChanged(tracks: Tracks) {
                    val heights = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
                        .flatMap { g -> (0 until g.length).map { g.getTrackFormat(it).height } }
                        .filter { it > 0 }.distinct().sortedDescending()
                    if (heights.isNotEmpty()) _state.value = _state.value.copy(heights = heights)
                }
                override fun onPlayerError(error: PlaybackException) {
                    _state.value = _state.value.copy(error = "This video stopped. Tap to try again.", playing = false, buffering = false)
                }
            })
        }

    init {
        // Leaving Wave pauses the video (it isn't background music).
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) { exo?.pause() }
            override fun onDestroy(owner: LifecycleOwner) { exo?.release(); exo = null }
        })
    }

    private fun publish() {
        val p = exo ?: return
        _state.value = _state.value.copy(
            playing = p.isPlaying,
            buffering = p.playbackState == Player.STATE_BUFFERING,
            ended = p.playbackState == Player.STATE_ENDED,
            durationMs = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: _state.value.durationMs,
        )
    }

    override val positionMs: Long get() = exo?.currentPosition ?: 0L

    /** Auto: 1080p on Wi-Fi, 480p on mobile data. */
    private fun applyCap(cap: Int?) {
        val connectivity = activity.getSystemService(ConnectivityManager::class.java)
        val max = cap ?: if (connectivity.isActiveNetworkMetered) 480 else 1080
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setMaxVideoSize(Int.MAX_VALUE, max)
            .setMinVideoSize(0, if (cap != null) cap else 0)
            .build()
    }

    override fun load(videoId: String, startMs: Long) {
        loading?.cancel()
        exo?.stop()
        val keep = _state.value
        _state.value = VideoState(videoId = videoId, buffering = true, cap = keep.cap)
        val connectivity = activity.getSystemService(ConnectivityManager::class.java)
        loading = scope.launch {
            val result = withContext(Dispatchers.IO) { StreamResolver.videoFor(videoId, connectivity) }
            val s = result.getOrElse {
                _state.value = _state.value.copy(buffering = false, error = it.message ?: "This video can't play right now")
                return@launch
            }
            val source = HlsMediaSource.Factory(OkHttpDataSource.Factory(http).setDefaultRequestProperties(s.headers))
                .createMediaSource(MediaItem.Builder().setUri(s.manifestUrl).setMimeType(MimeTypes.APPLICATION_M3U8).build())
            applyCap(_state.value.cap)
            player.setMediaSource(source, startMs)
            player.prepare()
            player.play()
        }
    }

    override fun togglePlay() {
        val p = exo ?: return
        val s = _state.value
        when {
            s.error != null -> s.videoId?.let { load(it, p.currentPosition) }
            p.playbackState == Player.STATE_ENDED -> { p.seekTo(0); p.play() }
            p.isPlaying -> p.pause()
            else -> p.play()
        }
    }

    override fun seekTo(ms: Long) { exo?.seekTo(ms) }

    override fun setQuality(height: Int?) {
        _state.value = _state.value.copy(cap = height)
        applyCap(height)
    }

    override fun stop() {
        loading?.cancel()
        exo?.stop()
        exo?.clearMediaItems()
        _state.value = VideoState(cap = _state.value.cap)
    }

    override fun setBarsHidden(hidden: Boolean) {
        val window = activity.window
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        if (hidden) {
            bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(WindowInsetsCompat.Type.systemBars())
        } else bars.show(WindowInsetsCompat.Type.systemBars())
    }

    override fun holdOrientation(mode: Int) {
        if (big() && mode != 0) return
        activity.requestedOrientation = when (mode) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            2 -> ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    @Composable
    override fun Surface(modifier: Modifier) {
        AndroidView(
            factory = { context -> TextureView(context).also { player.setVideoTextureView(it) } },
            modifier = modifier,
            onRelease = { exo?.clearVideoTextureView(it) },
        )
    }
}
