package app.wave.music.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.StateFlow

/** What the video player is doing. */
data class VideoState(
    val videoId: String? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val ended: Boolean = false,
    val durationMs: Long = 0,
    val width: Int = 16,
    val height: Int = 9,
    /** The heights this video comes in (1080, 720...). */
    val heights: List<Int> = emptyList(),
    /** The most it may use, picked by you (null: Wave picks for your connection). */
    val cap: Int? = null,
    /** What's showing right now. */
    val showing: Int? = null,
    val error: String? = null,
    /** The first picture is on screen (until then, it's still loading). */
    val firstFrame: Boolean = false,
    /** Playback speed (1 is normal). */
    val speed: Float = 1f,
)

/**
 * Plays music videos: picture and sound, separately from the music player. Android installs one at
 * startup; elsewhere there's none, and Wave says videos play in the Android app.
 */
interface VideoEngine {
    val state: StateFlow<VideoState>
    /** Read while drawing (the progress line), so only the line redraws as the video plays. */
    val positionMs: Long
    fun load(videoId: String, startMs: Long = 0)
    fun togglePlay()
    fun seekTo(ms: Long)
    fun setSpeed(speed: Float) {}
    /** A tiny copy of the picture showing now (for ambient mode), or null when there isn't one. */
    fun framePixels(): IntArray? = null
    /** Looks a video up ahead of time, so it starts right away when it's opened. */
    fun prefetch(videoId: String) {}
    /** The most picture it may use (null: Auto). */
    fun setQuality(height: Int?)
    fun stop()
    /** Full screen hides the status and navigation bars. */
    fun setBarsHidden(hidden: Boolean)
    /** Phones: 1 holds it sideways, 2 upright, 0 follows how you hold it. Tablets and computers ignore it. */
    fun holdOrientation(mode: Int)

    @Composable
    fun Surface(modifier: Modifier)
}

/** A video to watch: a song's video or a music video. */
data class VideoTarget(
    val videoId: String,
    val title: String,
    val artist: String,
    val thumbnail: String?,
    /** The song this is the video of (its own id and album cover), when it came from one. */
    val songId: String? = null,
    val songArt: String? = null,
)

object Video {
    var engine: VideoEngine? = null

    /** Plays a song as music (set by home: the song starts with more like it after). */
    var playSong: (com.metrolist.innertube.models.SongItem) -> Unit = {}

    /** What's open in the video player (null: closed). */
    var open by mutableStateOf<VideoTarget?>(null)
    /** Shrunk to the mini player (still playing) while you use the rest of Wave. */
    var minimized by mutableStateOf(false)
    /** What plays after it. */
    var upNext by mutableStateOf<List<VideoTarget>>(emptyList())
    /** Where to start (picking up a song where the music was). */
    var startAt = 0L

    fun thumbnail(videoId: String) = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

    /** Opens [target] full, or (when [keepSize]) the way the player already is: mini stays mini. */
    fun watch(target: VideoTarget, next: List<VideoTarget> = emptyList(), startMs: Long = 0, keepSize: Boolean = false) {
        startAt = startMs
        upNext = next.filter { it.videoId != target.videoId }
        if (open == null || !keepSize) minimized = false
        open = target
    }
}
