package app.wave.music.playback

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Crossfade between songs. ExoPlayer plays one song at a time, so a second, lightweight [tail]
 * player carries the last few seconds of the ending song while the main player has already
 * moved on to the next one: the old song fades out as the new one fades in. The tail reads the
 * ending song from the cache, so it's ready instantly. Everything else (queue, notification,
 * lock screen) only ever sees the main player.
 *
 * Driven by [tick] on the main thread; [gainFor] is each song's even-volume level.
 */
class Crossfader(
    private val main: ExoPlayer,
    private val tail: ExoPlayer,
    private val gainFor: (String?) -> Float,
) {
    /** Overlap length; 0 turns crossfade off. */
    var seconds = 0

    private var armedFor: String? = null
    private var armedAt = 0L
    private var fading = false
    private var fadeStart = 0L
    private var fadeMs = 1L
    private var tailGain = 1f

    val isFading get() = fading

    /** How soon to tick again: often near a fade, rarely otherwise. */
    fun tick(): Long {
        val now = SystemClock.elapsedRealtime()
        if (fading) {
            if (!main.playWhenReady) {
                finish() // paused mid-fade: just finish it
                return 200
            }
            val p = ((now - fadeStart).toFloat() / fadeMs).coerceIn(0f, 1f)
            // Equal-power curves, so the overlap doesn't dip in loudness.
            main.volume = gainFor(main.currentMediaItem?.mediaId) * kotlin.math.sin(p * Math.PI / 2).toFloat()
            tail.volume = tailGain * kotlin.math.cos(p * Math.PI / 2).toFloat()
            if (p >= 1f) finish()
            return 30
        }
        val overlap = seconds * 1000L
        val id = main.currentMediaItem?.mediaId
        val duration = main.duration
        if (overlap <= 0 || !main.isPlaying || !main.hasNextMediaItem() || main.repeatMode == Player.REPEAT_MODE_ONE ||
            duration == C.TIME_UNSET || duration < overlap * 2 + 10_000
        ) {
            disarm()
            return if (overlap <= 0 || !main.isPlaying) 1_000 else 250
        }
        val start = duration - overlap
        val position = main.currentPosition
        if (armedFor != null && (armedFor != id || position < armedAt - 6_000)) disarm() // skipped or seeked away
        if (armedFor == null && position in (start - 5_000) until start) {
            // Get the ending song ready in the tail player, silent, a few seconds early.
            tail.setMediaItem(main.currentMediaItem ?: return 250, start)
            tail.volume = 0f
            tail.playWhenReady = false
            tail.prepare()
            armedFor = id
            armedAt = start
        }
        if (armedFor == id && position >= armedAt) {
            if (tail.playbackState == Player.STATE_READY) begin(now, position) else disarm()
            return 30
        }
        val left = armedAt - position
        return if (armedFor != null && left < 600) 15 else 200
    }

    private fun begin(now: Long, position: Long) {
        tailGain = gainFor(armedFor)
        if (position - armedAt > 40) tail.seekTo(position)
        tail.volume = tailGain
        tail.play()
        // The main player moves on right away; the new song fades in from silence.
        main.volume = 0f
        main.seekToNextMediaItem()
        fading = true
        fadeStart = now
        fadeMs = (seconds * 1000L).coerceAtLeast(1L)
        armedFor = null
    }

    private fun finish() {
        fading = false
        tail.stop()
        tail.clearMediaItems()
        main.volume = gainFor(main.currentMediaItem?.mediaId)
    }

    private fun disarm() {
        if (armedFor == null) return
        armedFor = null
        tail.stop()
        tail.clearMediaItems()
    }

    fun release() = tail.release()
}
