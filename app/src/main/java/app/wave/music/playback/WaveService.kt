package app.wave.music.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import app.wave.music.data.WaveSettings
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.wave.music.MainActivity
import com.metrolist.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Background playback: ExoPlayer + a media session (lock screen, notification, Bluetooth, Pixel media controls). */
class WaveService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private class ResolvedStream(
        val url: String,
        val headers: Map<String, String>,
        val boundedChunk: Long,
        val expiresAt: Long,
    )

    private val streams = ConcurrentHashMap<String, ResolvedStream>()
    private val reported = ConcurrentHashMap.newKeySet<String>()

    override fun onCreate() {
        super.onCreate()
        StreamResolver.initialize(this)
        runBlocking { WaveSettings.load(this@WaveService) }

        val http = OkHttpDataSource.Factory(OkHttpClient.Builder().build())
        val cached = CacheDataSource.Factory()
            .setCache(cache(this))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(this, http))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val resolving = ResolvingDataSource.Factory(cached, ::resolve)

        // Measures loudness and beats on the way to the speaker, for "Move with the music".
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setAudioProcessorChain(DefaultAudioSink.DefaultAudioProcessorChain(BeatMeter()))
                    .build()
        }
        val player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    resolving,
                    ExtractorsFactory { arrayOf(MatroskaExtractor(), FragmentedMp4Extractor(), Mp4Extractor()) },
                ),
            )
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        var lastQuality = WaveSettings.state.value.audioQuality
        scope.launch(Dispatchers.Main) {
            WaveSettings.state.collect { settings ->
                player.skipSilenceEnabled = settings.skipSilence
                if (settings.audioQuality != lastQuality) {
                    lastQuality = settings.audioQuality
                    streams.clear() // new quality applies from the next song
                }
            }
        }

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // A stream URL can expire or get rejected: drop it and retry this song once.
                player.currentMediaItem?.mediaId?.let(streams::remove)
                scope.launch { StreamResolver.refreshAfterStreamRejection() }
                player.prepare()
            }
        })

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        session = MediaSession.Builder(this, player).setSessionActivity(openApp).build()
    }

    /** Turns a song id into a playable YouTube audio URL, just before ExoPlayer reads it. */
    private fun resolve(spec: DataSpec): DataSpec {
        val videoId = spec.key ?: spec.uri.toString()
        val cache = cache(this)
        if (cache.isCached(videoId, spec.position, if (spec.length >= 0) spec.length else 1)) return spec

        val now = System.currentTimeMillis()
        val stream = streams[videoId]?.takeIf { it.expiresAt > now } ?: runBlocking {
            val connectivity = getSystemService(ConnectivityManager::class.java)
            val data = StreamResolver.playerResponseForPlayback(
                videoId = videoId,
                audioQuality = WaveSettings.state.value.audioQuality,
                connectivityManager = connectivity,
            ).getOrThrow()
            data.playbackTracking?.videostatsPlaybackUrl?.baseUrl?.let { tracking ->
                // Count the play on YouTube Music so history and recommendations stay accurate.
                if (reported.add(videoId)) scope.launch { YouTube.registerPlayback(playbackTracking = tracking) }
            }
            ResolvedStream(
                url = data.streamUrl,
                headers = data.streamHeaders,
                boundedChunk = if (data.requireBoundedRange || data.useRangeChunks) data.rangeChunkSizeBytes else 0L,
                expiresAt = now + data.streamExpiresInSeconds * 1000L - 30_000L,
            ).also { streams[videoId] = it }
        }

        val resolved = spec.withUri(stream.url.toUri()).withRequestHeaders(spec.httpRequestHeaders + stream.headers)
        if (stream.boundedChunk <= 0L) return resolved
        val length = if (spec.length == C.LENGTH_UNSET.toLong()) stream.boundedChunk else minOf(spec.length, stream.boundedChunk)
        return resolved.subrange(0, length)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        @Volatile private var simpleCache: SimpleCache? = null

        fun cache(context: Context): SimpleCache = simpleCache ?: synchronized(this) {
            simpleCache ?: SimpleCache(
                File(context.cacheDir, "audio"),
                LeastRecentlyUsedCacheEvictor(512L * 1024 * 1024),
                StandaloneDatabaseProvider(context),
            ).also { simpleCache = it }
        }

        fun mediaItem(
            videoId: String,
            title: String,
            artist: String?,
            artworkUrl: String?,
            album: String? = null,
            durationMs: Long? = null,
        ): MediaItem = MediaItem.Builder()
            .setMediaId(videoId)
            .setUri(videoId)
            .setCustomCacheKey(videoId)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(artworkUrl?.toUri())
                    .setAlbumTitle(album)
                    .apply { durationMs?.let { setDurationMs(it) } }
                    .build(),
            )
            .build()
    }
}
