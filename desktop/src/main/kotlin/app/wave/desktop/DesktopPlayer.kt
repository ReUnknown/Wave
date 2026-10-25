package app.wave.desktop

import app.wave.music.data.Likes
import app.wave.music.data.WaveSettings
import app.wave.music.playback.NowPlaying
import app.wave.music.playback.PlayRequest
import app.wave.music.playback.PlaybackContext
import app.wave.music.playback.PlaybackSupport
import app.wave.music.playback.PlayerBackend
import app.wave.music.playback.PlayerUiState
import app.wave.music.playback.RepeatMode
import app.wave.music.playback.ResumeState
import app.wave.music.playback.ResumeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * The computer's player: the queue, shuffle order and play modes, on top of [Voice]s from the
 * FFmpeg engine. Works like the Android player: crossfade, even volume, autoplay when the queue
 * runs out, plays counted on YouTube, "resume where you left off", and the queue is kept between
 * launches. Everything runs on the UI thread except decoding.
 */
object DesktopPlayer : PlayerBackend {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)
    private val _state = MutableStateFlow(PlayerUiState())
    override val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var queue = mutableListOf<NowPlaying>()
    /** Queue indices in the order they play (shuffled or not); the current song is in here too. */
    private var order = mutableListOf<Int>()
    private var index = -1
    private var context: PlaybackContext? = null
    private var shuffle = false
    private var repeat = RepeatMode.OFF
    private var speed = 1f
    /** Whether you want music playing (paused otherwise). */
    private var wantsPlay = false
    private var buffering = false

    private var voice: Voice? = null
    private var fadingOut: Voice? = null
    private var loadJob: Job? = null
    private var reportJob: Job? = null
    private var failures = 0
    private var autoplayFrom: String? = null

    /** Resolved links, reused while they're valid (5 minutes) so replays and skips back are instant. */
    private val links = ConcurrentHashMap<String, Pair<Stream, Long>>()

    override val position: Long get() = voice?.positionMs ?: restoredPosition

    private var restoredPosition = 0L

    init {
        // Ticks: crossfade timing, and the saved position every few seconds.
        scope.launch {
            var ticks = 0
            while (true) {
                delay(200)
                checkCrossfade()
                if (++ticks % 75 == 0 && wantsPlay) save()
            }
        }
        scope.launch { Likes.ids.collect { publish() } }
    }

    // ---- Loading songs ----

    private fun load(at: Int, startMs: Long = 0, fadeInMs: Long = 0) {
        val song = queue.getOrNull(at) ?: return
        index = at
        restoredPosition = 0
        loadJob?.cancel()
        reportJob?.cancel()
        if (fadeInMs == 0L) voice?.stop()
        voice = null
        buffering = true
        publish()
        loadJob = scope.launch {
            val stream = runCatching { resolve(song.videoId) }.getOrElse {
                onFailed(song, it.message ?: "No stream")
                return@launch
            }
            val gain = evenGain(stream.loudnessDb)
            lateinit var created: Voice
            created = Voice(
                stream, startMs, gain,
                onEnded = { scope.launch { if (voice === created) advance(auto = true) } },
                onError = { message -> scope.launch { if (voice === created) onFailed(song, message) } },
            )
            created.paused = !wantsPlay
            if (fadeInMs > 0) created.fadeIn(fadeInMs)
            voice = created
            created.start(speed)
            while (!created.started && voice === created) delay(30)
            if (voice !== created) return@launch
            buffering = false
            failures = 0
            publish()
            countPlay(song, created)
            order.getOrNull(order.indexOf(index) + 1)?.let { queue.getOrNull(it) }?.let { next ->
                PlaybackSupport.prefetch(next)
                scope.launch(Dispatchers.IO) { runCatching { resolve(next.videoId) } }
            }
            PlaybackSupport.prefetch(song)
            maybeAutoplay()
            save()
        }
    }

    private suspend fun resolve(videoId: String): Stream {
        links[videoId]?.takeIf { it.second > System.currentTimeMillis() }?.let { return it.first }
        val stream = withContext(Dispatchers.IO) { Streams.resolve(videoId) }
        links[videoId] = stream to System.currentTimeMillis() + 5 * 60_000
        return stream
    }

    /** A stream that won't play gets one fresh try, then Wave moves on instead of going silent. */
    private fun onFailed(song: NowPlaying, message: String) {
        System.err.println("Wave: ${song.title} failed: $message")
        links.remove(song.videoId)
        if (++failures <= 1) {
            load(index, position)
        } else {
            failures = 0
            buffering = false
            if (nextIndex() != null) advance(auto = false) else { wantsPlay = false; publish() }
        }
    }

    /** Even volume: songs mastered louder than YouTube's reference are turned down to it. */
    private fun evenGain(loudnessDb: Double?): Float {
        if (!WaveSettings.state.value.evenVolume || loudnessDb == null || loudnessDb <= 0.0) return 1f
        return Math.pow(10.0, -loudnessDb / 20.0).toFloat().coerceIn(0.2f, 1f)
    }

    /** Like YouTube, a song counts once it has played for a few seconds. */
    private fun countPlay(song: NowPlaying, playing: Voice) {
        reportJob = scope.launch {
            var heard = 0
            while (heard < 4_000) {
                delay(500)
                if (voice !== playing) return@launch
                if (wantsPlay) heard += 500
            }
            PlaybackSupport.reportPlay(song)
        }
    }

    // ---- Moving through the queue ----

    private fun nextIndex(): Int? = order.getOrNull(order.indexOf(index) + 1)
    private fun previousIndex(): Int? = order.getOrNull(order.indexOf(index) - 1)

    private fun advance(auto: Boolean) {
        if (auto && repeat == RepeatMode.ONE) return load(index)
        val next = nextIndex() ?: if (repeat == RepeatMode.ALL) order.firstOrNull() else null
        if (next == null) {
            // The end of the queue.
            wantsPlay = false
            voice?.stop()
            voice = null
            publish()
            return
        }
        load(next)
    }

    /** Crossfade: near the end of a song, the next one starts quietly and the two overlap. */
    private fun checkCrossfade() {
        val seconds = WaveSettings.state.value.crossfadeSeconds
        val current = voice ?: return
        if (seconds <= 0 || !wantsPlay || !current.started || repeat == RepeatMode.ONE || fadingOut != null) return
        val duration = current.durationMs.takeIf { it > 0 } ?: return
        if (duration < seconds * 2_000L + 10_000) return
        val next = nextIndex() ?: return
        if (duration - current.positionMs > seconds * 1000L) return
        current.metering = false
        current.fadeOut(seconds * 1000L)
        fadingOut = current
        scope.launch {
            delay(seconds * 1000L + 500)
            current.stop()
            if (fadingOut === current) fadingOut = null
        }
        // Hand over without stopping the old voice.
        voice = null
        load(next, fadeInMs = seconds * 1000L)
    }

    /** Autoplay: when you reach the last song (repeat off), add songs like it. */
    private fun maybeAutoplay() {
        if (!WaveSettings.state.value.autoplay || repeat != RepeatMode.OFF || queue.isEmpty()) return
        if (order.size - order.indexOf(index) - 1 > 1) return
        val seed = queue.getOrNull(order.last())?.videoId ?: return
        if (autoplayFrom == seed) return
        autoplayFrom = seed
        scope.launch {
            val fresh = PlaybackSupport.similar(seed, queue.map { it.videoId }.toSet())
            if (fresh.isNotEmpty()) append(fresh)
        }
    }

    private fun append(items: List<NowPlaying>) {
        val start = queue.size
        queue.addAll(items)
        val added = (start until queue.size).toList()
        if (shuffle) {
            val at = order.indexOf(index).coerceAtLeast(0)
            for (i in added.shuffled()) order.add(kotlin.random.Random.nextInt(at + 1, order.size + 1), i)
        } else {
            order.addAll(added)
        }
        publish()
        save()
    }

    private fun buildOrder(current: Int) {
        val all = queue.indices.toList()
        order = if (shuffle && queue.isNotEmpty()) {
            (listOf(current) + all.filter { it != current }.shuffled()).toMutableList()
        } else {
            all.toMutableList()
        }
    }

    // ---- PlayerBackend ----

    override fun play(request: PlayRequest, startPositionMs: Long) {
        queue = request.items.toMutableList()
        context = request.context
        shuffle = request.shuffle
        autoplayFrom = null
        val start = request.startIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
        buildOrder(start)
        wantsPlay = true
        load(start, startPositionMs)
        request.more?.let { more ->
            val contextId = request.context.id
            scope.launch {
                val extra = withContext(Dispatchers.IO) { runCatching { more() }.getOrDefault(emptyList()) }
                val have = queue.map { it.videoId }.toSet()
                val fresh = extra.filter { it.videoId !in have }
                if (fresh.isNotEmpty() && context?.id == contextId) append(fresh)
            }
        }
    }

    override fun playNext(request: PlayRequest) {
        if (queue.isEmpty()) return play(request, 0)
        val at = index + 1
        queue.addAll(at, request.items)
        val count = request.items.size
        order = order.map { if (it >= at) it + count else it }.toMutableList()
        val spot = order.indexOf(index) + 1
        order.addAll(spot, (at until at + count).toList())
        publish()
        save()
    }

    override fun addToQueue(request: PlayRequest) {
        if (queue.isEmpty()) return play(request, 0)
        append(request.items)
    }

    override fun play() {
        if (wantsPlay) return
        togglePlay()
    }

    override fun togglePlay() {
        if (queue.isEmpty()) return
        wantsPlay = !wantsPlay
        val v = voice
        if (v == null && wantsPlay && loadJob?.isActive != true) {
            load(index.coerceAtLeast(0), restoredPosition)
            return
        }
        v?.paused = !wantsPlay
        fadingOut?.paused = !wantsPlay
        publish()
        save()
    }

    override fun next() = nextSong()

    override fun previous() {
        if (position > 3_000) seekTo(0) else previousSong()
    }

    override fun nextSong() {
        wantsPlay = true
        advance(auto = false)
    }

    override fun previousSong() {
        val previous = previousIndex()
        wantsPlay = true
        if (previous == null) seekTo(0) else load(previous)
    }

    override fun seekTo(ms: Long) {
        val v = voice
        if (v == null) restoredPosition = ms else v.seek(ms)
    }

    override fun skipTo(index: Int) {
        if (index !in queue.indices) return
        wantsPlay = true
        load(index)
    }

    override fun removeAt(index: Int) {
        if (index !in queue.indices) return
        val wasCurrent = index == this.index
        val nextBefore = if (wasCurrent) nextIndex() else null
        queue.removeAt(index)
        order = order.filter { it != index }.map { if (it > index) it - 1 else it }.toMutableList()
        if (this.index > index) this.index--
        if (wasCurrent) {
            val target = nextBefore?.let { if (it > index) it - 1 else it }
            if (target != null && target in queue.indices) load(target) else { voice?.stop(); voice = null; wantsPlay = false }
        }
        publish()
        save()
    }

    override fun move(from: Int, to: Int) {
        if (from !in queue.indices || to !in queue.indices) return
        val song = queue.removeAt(from)
        queue.add(to, song)
        val current = queue[if (index == from) to else index]
        index = queue.indexOf(current).takeIf { it >= 0 } ?: index
        if (!shuffle) order = queue.indices.toMutableList()
        publish()
        save()
    }

    override fun setSpeed(speed: Float) {
        this.speed = speed
        voice?.setSpeed(speed)
        publish()
    }

    override fun toggleShuffle() {
        shuffle = !shuffle
        buildOrder(index.coerceAtLeast(0))
        publish()
        save()
    }

    override fun cycleRepeat() {
        repeat = when (repeat) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            else -> RepeatMode.OFF
        }
        publish()
        save()
    }

    override fun reset() {
        loadJob?.cancel()
        voice?.stop()
        fadingOut?.stop()
        voice = null
        fadingOut = null
        queue.clear()
        order.clear()
        index = -1
        context = null
        wantsPlay = false
        QueueFile.clear()
        publish()
    }

    private fun publish() {
        val current = queue.getOrNull(index)
        val spot = order.indexOf(index)
        _state.value = PlayerUiState(
            current = current,
            isPlaying = wantsPlay && current != null,
            isBuffering = buffering,
            durationMs = voice?.durationMs?.takeIf { it > 0 } ?: current?.durationMs ?: 0,
            shuffle = shuffle,
            repeatMode = repeat,
            index = index.coerceAtLeast(0),
            queue = queue.toList(),
            context = context,
            liked = current != null && Likes.isLiked(current.videoId),
            upcoming = if (spot >= 0) order.subList(spot + 1, order.size).toList() else emptyList(),
            playOrder = order.toList(),
            speed = speed,
        )
    }

    // ---- Keeping your place ----

    /** Wave is closing: remember where you are (right now, not in the background) and stop the audio. */
    fun shutdown() {
        if (queue.isNotEmpty()) QueueFile.write(snapshot())
        fadingOut?.stop()
        voice?.stop()
    }

    /** Saves the queue and where you are, and "resume where you left off" for playlists and albums. */
    private fun save() {
        if (queue.isEmpty()) return
        val snapshot = snapshot()
        scope.launch(Dispatchers.IO) { QueueFile.write(snapshot) }
        val ctx = context ?: return
        val list = ctx.playlistId ?: return
        if (!ctx.isCollection) return
        val state = ResumeState(ctx.id, ctx.title, ctx.artworkUrl, list, index, queue.size, position, queue.getOrNull(index)?.title, System.currentTimeMillis())
        scope.launch(Dispatchers.IO) { ResumeStore.save(state) }
    }

    private fun snapshot() = QueueFile.Saved(
            items = queue.map { QueueFile.Item(it.videoId, it.title, it.artist, it.artworkUrl, it.album, it.durationMs) },
            index = index,
            positionMs = position,
            shuffle = shuffle,
            repeat = repeat,
            order = order.toList(),
            context = context?.let { QueueFile.Context(it.id, it.title, it.artworkUrl, it.playlistId, it.isCollection) },
        )

    /** Puts the last queue back, paused, when Wave starts with nothing playing. */
    fun restore() {
        val saved = QueueFile.read() ?: return
        queue = saved.items.map { NowPlaying(it.id, it.title, it.artist, it.art, it.album, it.durationMs) }.toMutableList()
        index = saved.index.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
        shuffle = saved.shuffle
        repeat = saved.repeat
        order = saved.order.takeIf { it.size == queue.size && it.toSet() == queue.indices.toSet() }?.toMutableList()
            ?: queue.indices.toMutableList()
        context = saved.context?.let { PlaybackContext(it.id, it.title, it.art, it.playlistId, it.collection) }
        restoredPosition = saved.positionMs
        publish()
    }
}

/** The queue between launches, as JSON in Wave's data folder. */
private object QueueFile {
    @Serializable data class Item(val id: String, val title: String, val artist: String, val art: String?, val album: String?, val durationMs: Long?)
    @Serializable data class Context(val id: String, val title: String, val art: String?, val playlistId: String?, val collection: Boolean)
    @Serializable data class Saved(
        val items: List<Item>,
        val index: Int,
        val positionMs: Long,
        val shuffle: Boolean,
        val repeat: Int,
        val order: List<Int>,
        val context: Context?,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val file get() = java.io.File(AppDirs.data, "queue.json")

    fun write(saved: Saved) = runCatching { file.writeText(json.encodeToString(Saved.serializer(), saved)) }
    fun read(): Saved? = runCatching { json.decodeFromString(Saved.serializer(), file.readText()) }.getOrNull()
    fun clear() = runCatching { file.delete() }
}
