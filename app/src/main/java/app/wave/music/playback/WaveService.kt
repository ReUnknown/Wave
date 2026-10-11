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
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.ContentMetadataMutations
import kotlinx.coroutines.delay
import timber.log.Timber
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
        /** The file's true size in bytes, or -1 if YouTube didn't say. */
        val contentLength: Long,
        val expiresAt: Long,
    )

    private val streams = ConcurrentHashMap<String, ResolvedStream>()
    private val reported = ConcurrentHashMap.newKeySet<String>()

    override fun onCreate() {
        super.onCreate()
        StreamResolver.initialize(this)
        // Before 0.9.5 the cache could record songs as shorter than they are, which cut them off
        // on every replay. That cache is thrown away; the new one always stores true sizes.
        scope.launch {
            val old = File(cacheDir, "audio")
            if (old.exists()) runCatching { SimpleCache.delete(old, StandaloneDatabaseProvider(this@WaveService)) }
        }
        runBlocking { WaveSettings.load(this@WaveService) }

        val http = OkHttpDataSource.Factory(OkHttpClient.Builder().build())
        val cached = CacheDataSource.Factory()
            .setCache(cache(this))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(this, StreamDataSource.Factory(http)))
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
            private var failures = 0

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                failures = 0
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) recoverIfCutShort(player, ended = false)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) recoverIfCutShort(player, ended = true)
            }

            override fun onPlayerError(error: PlaybackException) {
                // A stream URL can expire or get rejected: get a fresh one and pick up where it
                // stopped. If a song keeps failing, move on instead of going silent.
                player.currentMediaItem?.mediaId?.let(streams::remove)
                val attempt = ++failures
                scope.launch {
                    if (attempt <= 2) runCatching { StreamResolver.refreshAfterStreamRejection() }
                    launch(Dispatchers.Main) {
                        if (attempt > 2 && player.hasNextMediaItem()) player.seekToNextMediaItem()
                        player.prepare()
                    }
                }
            }
        })

        // Remember where each song is, so a song that ends early can be caught (see recoverIfCutShort).
        scope.launch(Dispatchers.Main) {
            while (true) {
                player.currentMediaItem?.let {
                    watchedId = it.mediaId
                    watchedPosition = player.currentPosition
                    watchedDuration = player.duration
                }
                delay(500)
            }
        }

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        session = MediaSession.Builder(this, ShufflingPlayer(player)).setSessionActivity(openApp).build()
    }

    private var watchedId: String? = null
    private var watchedPosition = 0L
    private var watchedDuration = C.TIME_UNSET
    private val cutShortFixes = HashMap<String, Int>()

    /**
     * Last line of defense for "the song stopped before the end": if a song finishes well before
     * its real length, its saved copy is thrown away and it picks up again, freshly downloaded,
     * from where it cut off.
     */
    private fun recoverIfCutShort(player: ExoPlayer, ended: Boolean) {
        val id = watchedId ?: return
        val duration = watchedDuration
        val position = watchedPosition
        if (duration == C.TIME_UNSET || duration <= 0 || position >= duration - 5_000) return
        val attempts = cutShortFixes.merge(id, 1, Int::plus) ?: 1
        if (attempts > 2) return
        val index = if (ended) {
            player.currentMediaItemIndex
        } else {
            player.previousMediaItemIndex.takeIf { it != C.INDEX_UNSET && player.getMediaItemAt(it).mediaId == id }
                ?: (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == id }
                ?: return
        }
        Timber.w("%s ended at %d of %d ms; reloading it", id, position, duration)
        streams.remove(id)
        scope.launch {
            runCatching { cache(this@WaveService).removeResource(id) }
            launch(Dispatchers.Main) {
                player.seekTo(index, position)
                player.prepare()
                player.play()
            }
        }
    }

    /** Turns a song id into a playable YouTube audio URL, just before ExoPlayer reads it. */
    private fun resolve(spec: DataSpec): DataSpec {
        val videoId = spec.key ?: spec.uri.toString()
        val cache = cache(this)
        // Only skip resolving when everything asked for is already saved. A partly saved song
        // still needs its real URL for the rest, or playback stops where the saved part ends.
        val total = ContentMetadata.getContentLength(cache.getContentMetadata(videoId))
        val wanted = if (spec.length >= 0) spec.length else if (total > 0) total - spec.position else -1L
        if (wanted > 0 && cache.isCached(videoId, spec.position, wanted)) return spec

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
                contentLength = data.format.contentLength ?: -1L,
                expiresAt = now + data.streamExpiresInSeconds * 1000L - 30_000L,
            ).also { streams[videoId] = it }
        }

        // Make sure the cache knows the song's true size, so it never stops reading early.
        if (stream.contentLength > 0) {
            val known = ContentMetadata.getContentLength(cache.getContentMetadata(videoId))
            if (known != stream.contentLength) {
                if (known > 0) runCatching { cache.removeResource(videoId) }
                runCatching {
                    cache.applyContentMetadataMutations(
                        videoId,
                        ContentMetadataMutations().also { ContentMetadataMutations.setContentLength(it, stream.contentLength) },
                    )
                }
            }
        }

        // StreamDataSource uses these to read the whole song, chunk by chunk when required.
        val hints = buildMap {
            if (stream.boundedChunk > 0L) put(StreamDataSource.CHUNK, stream.boundedChunk.toString())
            if (stream.contentLength > 0L) put(StreamDataSource.LENGTH, stream.contentLength.toString())
        }
        return spec.withUri(stream.url.toUri()).withRequestHeaders(spec.httpRequestHeaders + stream.headers + hints)
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
                File(context.cacheDir, "audio-v2"),
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
