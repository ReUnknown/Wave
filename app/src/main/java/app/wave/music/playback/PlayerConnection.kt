package app.wave.music.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.metrolist.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NowPlaying(
    val videoId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val album: String? = null,
    val durationMs: Long? = null,
)

data class PlayerUiState(
    val current: NowPlaying? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val index: Int = 0,
    val queue: List<NowPlaying> = emptyList(),
    val context: PlaybackContext? = null,
    val liked: Boolean = false,
    /** Queue indices in the order they'll actually play next (respects shuffle). */
    val upcoming: List<Int> = emptyList(),
    /** Every queue index in actual play order (respects shuffle); the current song is in here too. */
    val playOrder: List<Int> = emptyList(),
)

/** The app's handle on the playback service. UI reads [state] and calls these actions. */
object PlayerConnection {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null
    private lateinit var appContext: Context
    private val liked = mutableSetOf<String>()

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var playbackContext: PlaybackContext? = null

    fun connect(context: Context) {
        if (controller != null) return
        appContext = context.applicationContext
        val token = SessionToken(appContext, ComponentName(appContext, WaveService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            controller = future.get().also { c ->
                if (c.mediaItemCount == 0) restoreQueue(c)
                c.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
                            orderStale = true
                            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) {
                                queueStale = true
                                itemsSaved = false
                            }
                        }
                        if (events.containsAny(
                                Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_TIMELINE_CHANGED,
                                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED, Player.EVENT_REPEAT_MODE_CHANGED,
                            )
                        ) saveQueue()
                        publish()
                        if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) || events.contains(Player.EVENT_IS_PLAYING_CHANGED)) saveResume()
                        if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) prefetchLyrics()
                        if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_REPEAT_MODE_CHANGED)) maybeAutoplay()
                    }
                })
            }
            publish()
            // Keep the saved position fresh while playing, in case Android closes the app.
            scope.launch {
                while (true) {
                    kotlinx.coroutines.delay(15_000)
                    if (controller?.isPlaying == true) saveQueue()
                }
            }
        }, MoreExecutors.directExecutor())
    }

    private val queueFile get() = java.io.File(appContext.filesDir, "queue.json")
    private val queueStateFile get() = java.io.File(appContext.filesDir, "queue-state.json")

    /** The song list only needs rewriting when it changed; position saves are tiny. */
    private var itemsSaved = false

    /** Saves the whole queue, where you are in it, and its modes, so it survives the app closing. */
    private fun saveQueue() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return
        val state = org.json.JSONObject().apply {
            put("index", c.currentMediaItemIndex)
            put("position", c.currentPosition)
            put("shuffle", c.shuffleModeEnabled)
            put("repeat", c.repeatMode)
        }.toString()
        if (itemsSaved) {
            scope.launch(Dispatchers.IO) { runCatching { queueStateFile.writeText(state) } }
            return
        }
        itemsSaved = true
        val json = org.json.JSONObject().apply {
            playbackContext?.let { ctx ->
                put("context", org.json.JSONObject().apply {
                    put("id", ctx.id); put("title", ctx.title); put("art", ctx.artworkUrl ?: "")
                    put("playlistId", ctx.playlistId ?: ""); put("collection", ctx.isCollection)
                })
            }
            put("items", org.json.JSONArray().apply {
                for (i in 0 until c.mediaItemCount) {
                    val song = c.getMediaItemAt(i).toNowPlaying()
                    put(org.json.JSONObject().apply {
                        put("id", song.videoId); put("title", song.title); put("artist", song.artist)
                        put("art", song.artworkUrl ?: ""); put("album", song.album ?: ""); put("duration", song.durationMs ?: -1L)
                    })
                }
            })
        }.toString()
        scope.launch(Dispatchers.IO) {
            runCatching {
                queueFile.writeText(json)
                queueStateFile.writeText(state)
            }
        }
    }

    /** Puts the last queue back (paused) when the app starts with nothing playing. */
    private fun restoreQueue(c: MediaController) {
        val json = runCatching { org.json.JSONObject(queueFile.readText()) }.getOrNull() ?: return
        // Where you were lives in its own small file (older saves kept it in queue.json).
        val spot = runCatching { org.json.JSONObject(queueStateFile.readText()) }.getOrNull() ?: json
        val array = json.optJSONArray("items") ?: return
        val items = (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            WaveService.mediaItem(
                videoId = o.getString("id"),
                title = o.optString("title"),
                artist = o.optString("artist"),
                artworkUrl = o.optString("art").ifBlank { null },
                album = o.optString("album").ifBlank { null },
                durationMs = o.optLong("duration", -1L).takeIf { it > 0 },
            )
        }
        if (items.isEmpty()) return
        json.optJSONObject("context")?.let { o ->
            playbackContext = PlaybackContext(
                id = o.optString("id"),
                title = o.optString("title"),
                artworkUrl = o.optString("art").ifBlank { null },
                playlistId = o.optString("playlistId").ifBlank { null },
                isCollection = o.optBoolean("collection"),
            )
        }
        c.shuffleModeEnabled = spot.optBoolean("shuffle")
        c.repeatMode = spot.optInt("repeat")
        c.setMediaItems(items, spot.optInt("index").coerceIn(0, items.size - 1), spot.optLong("position"))
        itemsSaved = true
        c.prepare()
    }

    val position: Long get() = controller?.currentPosition ?: 0L

    private fun MediaItem.toNowPlaying(): NowPlaying = mediaMetadata.toNowPlaying(mediaId)

    private fun MediaMetadata.toNowPlaying(id: String) = NowPlaying(
        videoId = id,
        title = title?.toString().orEmpty(),
        artist = artist?.toString().orEmpty(),
        artworkUrl = artworkUri?.toString(),
        album = albumTitle?.toString(),
        durationMs = durationMs,
    )

    /** The queue as the UI sees it, rebuilt only when the song list changes (it can be thousands long). */
    private var queueCache: List<NowPlaying> = emptyList()
    private var queueStale = true
    /** Play order, rebuilt only when the list or shuffle changes. */
    private var orderCache: List<Int> = emptyList()
    private var orderStale = true

    private fun publish() {
        val c = controller ?: return
        if (queueStale || queueCache.size != c.mediaItemCount) {
            queueCache = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).toNowPlaying() }
            queueStale = false
            orderStale = true
        }
        if (orderStale) {
            orderCache = fullOrder(c)
            orderStale = false
        }
        val index = c.currentMediaItemIndex
        val spot = orderCache.indexOf(index)
        val current = queueCache.getOrNull(index)
        _state.value = PlayerUiState(
            current = current,
            isPlaying = c.isPlaying,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            durationMs = c.duration.coerceAtLeast(0),
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            index = c.currentMediaItemIndex,
            queue = queueCache,
            context = playbackContext,
            liked = current?.videoId in liked,
            upcoming = if (spot >= 0) orderCache.subList(spot + 1, orderCache.size) else upcomingOrder(c),
            playOrder = orderCache,
        )
    }

    private fun fullOrder(c: MediaController): List<Int> {
        val timeline = c.currentTimeline
        if (timeline.isEmpty) return emptyList()
        val order = mutableListOf<Int>()
        var i = timeline.getFirstWindowIndex(c.shuffleModeEnabled)
        while (i != androidx.media3.common.C.INDEX_UNSET && order.size < c.mediaItemCount) {
            order += i
            i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
        }
        return order
    }

    private fun upcomingOrder(c: MediaController): List<Int> {
        val timeline = c.currentTimeline
        if (timeline.isEmpty) return emptyList()
        val order = mutableListOf<Int>()
        var i = c.currentMediaItemIndex
        while (order.size < c.mediaItemCount) {
            i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
            if (i == androidx.media3.common.C.INDEX_UNSET) break
            order += i
        }
        return order
    }

    /** The song the queue was last extended from, so it's only done once per ending. */
    private var autoplayFrom: String? = null

    /**
     * Autoplay: when you reach the last song of the queue (with repeat off), add songs like it,
     * the way YouTube Music keeps going after a playlist ends.
     */
    private fun maybeAutoplay() {
        val c = controller ?: return
        if (!app.wave.music.data.WaveSettings.state.value.autoplay) return
        if (c.repeatMode != Player.REPEAT_MODE_OFF || c.mediaItemCount == 0) return
        if (upcomingOrder(c).size > 1) return
        val last = fullOrder(c).lastOrNull() ?: return
        val seed = c.getMediaItemAt(last).mediaId
        if (autoplayFrom == seed) return
        autoplayFrom = seed
        scope.launch {
            val songs = withContext(Dispatchers.IO) {
                runCatching {
                    YouTube.next(com.metrolist.innertube.models.WatchEndpoint(videoId = seed, playlistId = "RDAMVM$seed")).getOrThrow().items
                }.getOrNull()
            } ?: return@launch
            val ctl = controller ?: return@launch
            val have = (0 until ctl.mediaItemCount).map { ctl.getMediaItemAt(it).mediaId }.toSet()
            val fresh = songs
                .filter { it.id !in have && !it.isVideoSong && !it.isEpisode }
                .distinctBy { it.id }
                .take(25)
                .map { with(QueueBuilder) { it.toMediaItem() } }
            if (fresh.isNotEmpty()) {
                ctl.addMediaItems(fresh)
                queueChanged()
            }
        }
    }

    /** Fetch lyrics for this song and the next one ahead of time, so the lyrics view opens instantly. */
    private fun prefetchLyrics() {
        val c = controller ?: return
        val upcoming = listOfNotNull(c.currentMediaItem, c.nextMediaItemIndex.takeIf { it >= 0 }?.let { c.getMediaItemAt(it) })
        upcoming.forEach { item ->
            val song = item.toNowPlaying()
            scope.launch(Dispatchers.IO) {
                app.wave.music.data.LyricsRepository.load(song.videoId, song.title, song.artist, song.durationMs ?: 0L, song.album)
            }
        }
    }

    /** Learns which songs you've already liked, from your Liked music playlist. */
    private val likesFile get() = java.io.File(appContext.filesDir, "likes.txt")
    private const val LIKES_FRESH_MS = 6 * 60 * 60_000L

    /**
     * Learns which songs you've already liked. The list is kept on the phone, so it's there
     * instantly, and only re-read from YouTube (up to 50 pages) when it's more than 6 hours old.
     */
    suspend fun loadLikes() {
        val file = likesFile
        val saved = withContext(Dispatchers.IO) { runCatching { file.readLines().filter { it.isNotBlank() } }.getOrNull() }
        if (saved != null) {
            liked += saved
            publish()
            if (System.currentTimeMillis() - file.lastModified() < LIKES_FRESH_MS) return
        }
        val ids = withContext(Dispatchers.IO) {
            runCatching {
                val first = YouTube.playlist("LM").getOrThrow()
                val all = first.songs.map { it.id }.toMutableList()
                var next = first.songsContinuation ?: first.continuation
                var pages = 0
                while (next != null && pages++ < 50) {
                    val more = YouTube.playlistContinuation(next).getOrNull() ?: break
                    all += more.songs.map { it.id }
                    next = more.continuation
                }
                all
            }.getOrNull()
        } ?: return
        liked.clear()
        liked += ids
        publish()
        saveLikes()
    }

    private fun saveLikes() {
        val snapshot = liked.toList()
        scope.launch(Dispatchers.IO) { runCatching { likesFile.writeText(snapshot.joinToString("\n")) } }
    }

    private fun saveResume() {
        val c = controller ?: return
        val ctx = playbackContext ?: return
        val list = ctx.playlistId ?: return
        if (!ctx.isCollection || c.mediaItemCount == 0) return
        val state = ResumeState(
            contextId = ctx.id,
            title = ctx.title,
            artworkUrl = ctx.artworkUrl,
            playlistId = list,
            index = c.currentMediaItemIndex,
            total = c.mediaItemCount,
            positionMs = c.currentPosition,
            currentSong = c.currentMediaItem?.mediaMetadata?.title?.toString(),
            updatedAt = System.currentTimeMillis(),
        )
        scope.launch(Dispatchers.IO) { ResumeStore.save(appContext, state) }
    }

    private fun queueChanged() {
        queueStale = true
        itemsSaved = false
        publish()
    }

    fun play(request: PlayRequest, startPositionMs: Long = 0) {
        val c = controller ?: return
        playbackContext = request.context
        c.shuffleModeEnabled = request.shuffle
        c.setMediaItems(request.items, request.startIndex, startPositionMs)
        c.prepare()
        c.play()
        queueChanged()
        request.more?.let { loadMore ->
            val contextId = request.context.id
            scope.launch {
                val extra = withContext(Dispatchers.IO) { runCatching { loadMore() }.getOrDefault(emptyList()) }
                // Only append if you're still listening to the same thing.
                if (extra.isNotEmpty() && playbackContext?.id == contextId) {
                    controller?.addMediaItems(extra)
                    queueChanged()
                }
            }
        }
    }

    /** Puts songs right after the current one (or starts playing if nothing is). */
    fun playNext(request: PlayRequest) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            play(request)
            return
        }
        c.addMediaItems(c.currentMediaItemIndex + 1, request.items)
        queueChanged()
    }

    /** Adds songs to the end of the queue (or starts playing if nothing is). */
    fun addToQueue(request: PlayRequest) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            play(request)
            return
        }
        c.addMediaItems(request.items)
        queueChanged()
    }

    fun play() = controller?.play()

    fun togglePlay() = controller?.run { if (isPlaying) pause() else play() }
    fun next() = controller?.seekToNext()
    fun previous() = controller?.seekToPrevious()
    fun seekTo(ms: Long) = controller?.seekTo(ms)
    fun skipTo(index: Int) = controller?.seekToDefaultPosition(index)
    fun removeAt(index: Int) = controller?.removeMediaItem(index)
    fun move(from: Int, to: Int) = controller?.moveMediaItem(from, to)

    fun toggleShuffle() = controller?.run { shuffleModeEnabled = !shuffleModeEnabled }

    fun cycleRepeat() = controller?.run {
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggleLike() {
        val id = _state.value.current?.videoId ?: return
        setLiked(id, id !in liked)
    }

    fun isLiked(videoId: String) = videoId in liked

    fun setLiked(videoId: String, like: Boolean) {
        if (like) liked += videoId else liked -= videoId
        publish()
        saveLikes()
        scope.launch(Dispatchers.IO) { YouTube.likeVideo(videoId, like) }
    }
}
