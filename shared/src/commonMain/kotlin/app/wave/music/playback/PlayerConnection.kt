package app.wave.music.playback

import app.wave.music.data.Likes
import app.wave.music.data.WaveSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class NowPlaying(
    val videoId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val album: String? = null,
    val durationMs: Long? = null,
)

/** Repeat modes (the same numbers Android's Media3 uses). */
object RepeatMode {
    const val OFF = 0
    const val ONE = 1
    const val ALL = 2
}

data class PlayerUiState(
    val current: NowPlaying? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = RepeatMode.OFF,
    val index: Int = 0,
    val queue: List<NowPlaying> = emptyList(),
    val context: PlaybackContext? = null,
    val liked: Boolean = false,
    /** Queue indices in the order they'll actually play next (respects shuffle). */
    val upcoming: List<Int> = emptyList(),
    /** Every queue index in actual play order (respects shuffle); the current song is in here too. */
    val playOrder: List<Int> = emptyList(),
    /** Playback speed (1 = normal). */
    val speed: Float = 1f,
)

/**
 * The player on this platform: Media3 in a background service on Android, Wave's own FFmpeg
 * engine on computers. Each keeps the queue, shuffle order and modes, and reports [state].
 */
interface PlayerBackend {
    val state: StateFlow<PlayerUiState>
    /** Where the current song is, right now (ms). */
    val position: Long
    fun play(request: PlayRequest, startPositionMs: Long)
    /** Puts songs right after the current one (or starts playing if nothing is). */
    fun playNext(request: PlayRequest)
    /** Adds songs to the end of the queue (or starts playing if nothing is). */
    fun addToQueue(request: PlayRequest)
    fun play()
    fun togglePlay()
    /** Next song; previous restarts the song first if it's a few seconds in. */
    fun next()
    fun previous()
    /** Straight to the next / previous song in play order. */
    fun nextSong()
    fun previousSong()
    fun seekTo(ms: Long)
    fun skipTo(index: Int)
    fun removeAt(index: Int)
    fun move(from: Int, to: Int)
    fun setSpeed(speed: Float)
    fun toggleShuffle()
    fun cycleRepeat()
    /** Stops and forgets the queue (sign-out, or the mini player swiped away). */
    fun reset()
}

/** The app's handle on the player. Screens read [state] and call these; the platform's backend does the work. */
object PlayerConnection {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    lateinit var backend: PlayerBackend

    val state: StateFlow<PlayerUiState> get() = backend.state
    val position: Long get() = backend.position

    fun play(request: PlayRequest, startPositionMs: Long = 0) = backend.play(request, startPositionMs)
    fun playNext(request: PlayRequest) = backend.playNext(request)
    fun addToQueue(request: PlayRequest) = backend.addToQueue(request)
    fun play() = backend.play()
    fun togglePlay() = backend.togglePlay()
    fun next() = backend.next()
    fun previous() = backend.previous()
    fun nextSong() = backend.nextSong()
    fun previousSong() = backend.previousSong()
    fun seekTo(ms: Long) = backend.seekTo(ms)
    fun skipTo(index: Int) = backend.skipTo(index)
    fun removeAt(index: Int) = backend.removeAt(index)
    fun move(from: Int, to: Int) = backend.move(from, to)
    fun setSpeed(speed: Float) = backend.setSpeed(speed)
    fun toggleShuffle() = backend.toggleShuffle()
    fun cycleRepeat() = backend.cycleRepeat()
    fun reset() = backend.reset()

    /** Swiped the mini player away: stop the music and clear the queue. */
    fun dismiss() = reset()

    /** Wave's own volume (not the device's). Saved when [done], i.e. when you let go of the slider. */
    fun setVolume(level: Float, done: Boolean) {
        WaveSettings.preview { it.copy(appVolume = level.coerceIn(0f, 1f)) }
        if (done) scope.launch { WaveSettings.update { it } }
    }

    fun toggleLike() {
        val id = state.value.current?.videoId ?: return
        setLiked(id, !Likes.isLiked(id))
    }

    fun isLiked(videoId: String) = Likes.isLiked(videoId)
    fun setLiked(videoId: String, like: Boolean) = Likes.set(videoId, like)

    /** Learns which songs you've already liked (see [Likes]). */
    suspend fun loadLikes() = Likes.load()
}
