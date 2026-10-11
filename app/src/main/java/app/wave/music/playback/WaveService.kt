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
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.common.TrackSelectionParameters
import android.os.Bundle
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.distinctUntilChanged
import app.wave.music.MainActivity
import app.wave.music.widget.WaveWidget
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
    /** Each song's loudness vs. YouTube's reference (dB), for even volume. */
    private val loudness = ConcurrentHashMap<String, Double>()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var player: ExoPlayer
    private lateinit var shuffling: ShufflingPlayer
    private lateinit var crossfader: Crossfader

    /**
     * Even volume: songs mastered louder than the reference are turned down to it (quieter ones
     * are left alone; boosting would clip). Works the same with low-power playback.
     */
    private fun gainFor(id: String?): Float {
        // Wave's own volume slider, on a curve so the middle of the slider sounds like the middle.
        val user = WaveSettings.state.value.appVolume.coerceIn(0f, 1f).let { it * it }
        return evenGain(id) * user * duck
    }

    private fun evenGain(id: String?): Float {
        if (id == null || !WaveSettings.state.value.evenVolume) return 1f
        val db = loudness[id] ?: return 1f
        if (db <= 0.0) return 1f
        return Math.pow(10.0, -db / 20.0).toFloat().coerceIn(0.2f, 1f)
    }

    /** Briefly dips to 0 while the audio path switches (see [switchOffload]), so there's no click. */
    private var duck = 1f

    private fun applyGain() {
        if (::crossfader.isInitialized && crossfader.isFading) return
        player.volume = gainFor(player.currentMediaItem?.mediaId)
    }

    private val likeCommand = SessionCommand(ACTION_LIKE, Bundle.EMPTY)
    private val shuffleCommand = SessionCommand(ACTION_SHUFFLE, Bundle.EMPTY)

    /** Like and shuffle on the lock screen, notification and Pixel's media controls. */
    private fun updateButtons() {
        val id = player.currentMediaItem?.mediaId
        val liked = id != null && app.wave.music.data.Likes.isLiked(id)
        val like = CommandButton.Builder(if (liked) CommandButton.ICON_THUMB_UP_FILLED else CommandButton.ICON_THUMB_UP_UNFILLED)
            .setDisplayName(if (liked) "Liked" else "Like")
            .setSessionCommand(likeCommand)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()
        val shuffle = CommandButton.Builder(if (player.shuffleModeEnabled) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF)
            .setDisplayName(if (player.shuffleModeEnabled) "Shuffle on" else "Shuffle off")
            .setSessionCommand(shuffleCommand)
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()
        session?.setMediaButtonPreferences(listOf(like, shuffle))
    }

    private val callback = object : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(likeCommand)
                .add(shuffleCommand)
                .add(setOrderCommand)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(commands).build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_LIKE -> player.currentMediaItem?.mediaId?.let { id ->
                    app.wave.music.data.Likes.set(this@WaveService, id, !app.wave.music.data.Likes.isLiked(id))
                }
                ACTION_SHUFFLE -> shuffling.shuffleModeEnabled = !player.shuffleModeEnabled
                ACTION_SET_ORDER -> args.getIntArray(KEY_ORDER)?.let { shuffling.restoreOrder(it.toList()) }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        /** Play pressed on the widget, lock screen or Pixel's media controls with nothing loaded: bring back the last queue. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val saved = QueueStore.read(this@WaveService)
                ?: return Futures.immediateFailedFuture(UnsupportedOperationException("Nothing to resume"))
            main.post {
                player.repeatMode = saved.repeatMode
                if (saved.shuffle) {
                    shuffling.shuffleModeEnabled = true
                    saved.order?.let(shuffling::restoreOrder)
                }
            }
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(saved.items, saved.index, saved.positionMs))
        }
    }


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
        val sources = DefaultMediaSourceFactory(
            resolving,
            ExtractorsFactory { arrayOf(MatroskaExtractor(), FragmentedMp4Extractor(), Mp4Extractor()) },
        )
        val musicAttributes = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build()

        // Measures loudness and beats on the way to the speaker, for "Move with the music".
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setAudioProcessorChain(DefaultAudioSink.DefaultAudioProcessorChain(BeatMeter()))
                    .build()
        }
        val player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(sources)
            .setAudioAttributes(musicAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        this.player = player
        // The crossfade's second voice: no audio focus of its own, it only ever plays a fading tail.
        val tail = ExoPlayer.Builder(this)
            .setMediaSourceFactory(sources)
            .setAudioAttributes(musicAttributes, false)
            .build()
        crossfader = Crossfader(player, tail, ::gainFor)
        scope.launch(Dispatchers.Main) {
            while (true) delay(crossfader.tick())
        }

        var lastQuality = WaveSettings.state.value.audioQuality
        scope.launch(Dispatchers.Main) {
            WaveSettings.state.collect { settings ->
                player.skipSilenceEnabled = settings.skipSilence
                crossfader.seconds = settings.crossfadeSeconds
                applyGain()
                if (settings.audioQuality != lastQuality) {
                    lastQuality = settings.audioQuality
                    streams.clear() // new quality applies from the next song
                }
            }
        }

        // Low-power playback: while Wave isn't on screen, the phone's audio chip decodes the music
        // (Media3 audio offload) and the main processor can sleep. On screen, audio goes through
        // the normal path so "Move with the music" can listen to it. Skip silence needs the normal
        // path too. Phones that can't offload a format just keep using the normal path.
        // Switching paths restarts the audio output, so it never happens mid-song unannounced:
        // going to the background waits for the next song or a pause; coming back to the screen
        // switches right away under a quick fade, so you don't hear a gap or a click.
        scope.launch(Dispatchers.Main) {
            kotlinx.coroutines.flow.combine(WaveSettings.state, BeatBus.visibleFlow) { s, visible ->
                s.lowPowerPlayback && !visible && !s.skipSilence
            }.distinctUntilChanged().collect { want ->
                offloadWanted = want
                when {
                    !want -> switchOffload(false, fade = true)
                    !player.isPlaying -> switchOffload(true, fade = false)
                }
            }
        }
        scope.launch(Dispatchers.Main) {
            app.wave.music.data.Likes.ids.collect {
                if (session != null) updateButtons()
                WaveWidget.update(this@WaveService, player)
            }
        }

        player.addListener(object : Player.Listener {
            private var failures = 0

            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) updateButtons()
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_MEDIA_METADATA_CHANGED, Player.EVENT_TIMELINE_CHANGED,
                    )
                ) WaveWidget.update(this@WaveService, this@WaveService.player)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                failures = 0
                applyGain()
                reportPlay(mediaItem)
                // A song boundary is the seamless moment to move to the audio chip.
                if (offloadWanted != offloadOn && !crossfader.isFading) switchOffload(offloadWanted, fade = false)
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) recoverIfCutShort(player, ended = false)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying && offloadWanted != offloadOn) switchOffload(offloadWanted, fade = false)
                // Same song played again from the start (repeat one) counts again too.
                if (isPlaying && reportJob?.isActive != true && reportedId != player.currentMediaItem?.mediaId) reportPlay(player.currentMediaItem)
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
        shuffling = ShufflingPlayer(player)
        session = MediaSession.Builder(this, shuffling)
            .setSessionActivity(openApp)
            .setCallback(callback)
            .build()
        updateButtons()
    }

    private var offloadWanted = false
    private var offloadOn = false
    private var switchJob: kotlinx.coroutines.Job? = null

    /** Moves playback onto (or off) the audio chip; [fade] dips the volume around the switch. */
    private fun switchOffload(on: Boolean, fade: Boolean) {
        if (on == offloadOn && switchJob?.isActive != true) return
        switchJob?.cancel()
        switchJob = scope.launch(Dispatchers.Main) {
            val audible = fade && player.isPlaying
            if (audible) rampDuck(0f, 120)
            offloadOn = on
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setAudioOffloadPreferences(
                    TrackSelectionParameters.AudioOffloadPreferences.Builder()
                        .setAudioOffloadMode(
                            if (on) TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                            else TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED,
                        )
                        .build(),
                )
                .build()
            Timber.d("Low-power playback %s", if (on) "on" else "off")
            if (audible) {
                // Let the new output start before bringing the sound back up.
                var waited = 0
                while (player.playbackState == Player.STATE_BUFFERING && waited < 1_500) { delay(20); waited += 20 }
                delay(60)
                rampDuck(1f, 280)
            } else {
                duck = 1f
                applyGain()
            }
        }
    }

    private suspend fun rampDuck(to: Float, ms: Int) {
        val from = duck
        val steps = (ms / 16).coerceAtLeast(1)
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            duck = from + (to - from) * t * t * (3 - 2 * t)
            applyGain()
            delay(16)
        }
    }

    private var reportedId: String? = null
    private var reportJob: kotlinx.coroutines.Job? = null

    /**
     * Counts a play on your YouTube Music account, so it shows in History, "Recently played" and
     * shapes recommendations. Like YouTube, a song counts once it has played for a few seconds.
     * The report needs a playback link made for your account (the audio itself may come from a
     * signed-out client, or straight from the saved copy), so it asks for one as YouTube Music
     * on the web does.
     */
    private fun reportPlay(item: MediaItem?) {
        reportJob?.cancel()
        val id = item?.mediaId ?: return
        reportJob = scope.launch(Dispatchers.Main) {
            var heard = 0
            while (heard < 4_000) {
                delay(500)
                if (player.currentMediaItem?.mediaId != id) return@launch
                if (player.isPlaying) heard += 500
            }
            reportedId = id
            val meta = item.mediaMetadata
            app.wave.music.data.RecentPlays.add(
                id, meta.title?.toString().orEmpty(), meta.artist?.toString().orEmpty(),
                meta.artworkUri?.toString(), meta.albumTitle?.toString(), meta.durationMs,
            )
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                // Started from the widget or lock screen without the app: load the saved sign-in first.
                if (!app.wave.music.data.SessionRepository.restored.value) app.wave.music.data.SessionRepository.restore(this@WaveService)
                if (YouTube.cookie.isNullOrEmpty()) return@withContext
                val tracking = YouTube.player(id, client = com.metrolist.innertube.models.YouTubeClient.WEB_REMIX).getOrNull()
                    ?.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                if (tracking == null) {
                    Timber.w("No playback link to count %s", id)
                    return@withContext
                }
                YouTube.registerPlayback(playbackTracking = tracking)
                    .onSuccess { app.wave.music.data.HomeRepository.clear() }
                    .onFailure { Timber.w(it, "Couldn't count the play of %s", id) }
            }
        }
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
        if (wanted > 0 && cache.isCached(videoId, spec.position, wanted)) {
            // Fully saved: its loudness was saved alongside it.
            if (!loudness.containsKey(videoId)) {
                cache.getContentMetadata(videoId).get(LOUDNESS_KEY, null as String?)?.toDoubleOrNull()?.let {
                    loudness[videoId] = it
                    main.post { if (player.currentMediaItem?.mediaId == videoId) applyGain() }
                }
            }
            return spec
        }

        val now = System.currentTimeMillis()
        val stream = streams[videoId]?.takeIf { it.expiresAt > now } ?: runBlocking {
            val connectivity = getSystemService(ConnectivityManager::class.java)
            val data = StreamResolver.playerResponseForPlayback(
                videoId = videoId,
                audioQuality = WaveSettings.state.value.audioQuality,
                connectivityManager = connectivity,
            ).getOrThrow()
            data.format.loudnessDb?.let { db ->
                loudness[videoId] = db
                runCatching {
                    cache.applyContentMetadataMutations(videoId, ContentMetadataMutations().set(LOUDNESS_KEY, db.toString()))
                }
                main.post { if (player.currentMediaItem?.mediaId == videoId) applyGain() }
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
        if (::crossfader.isInitialized) crossfader.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_LIKE = "wave.like"
        private const val ACTION_SHUFFLE = "wave.shuffle"
        private const val ACTION_SET_ORDER = "wave.set_order"
        private const val KEY_ORDER = "order"
        private const val LOUDNESS_KEY = "wave_loudness"

        /** From the app: put back a restored queue's shuffled order. */
        val setOrderCommand = SessionCommand(ACTION_SET_ORDER, Bundle.EMPTY)
        fun orderArgs(order: List<Int>) = Bundle().apply { putIntArray(KEY_ORDER, order.toIntArray()) }

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
