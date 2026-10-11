package app.wave.music.playback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Live loudness and beats of what's playing, for "Move with the music". */
object BeatBus {
    internal val _level = MutableStateFlow(0f)
    /** 0..1 loudness relative to the song's recent average. */
    val level: StateFlow<Float> = _level.asStateFlow()

    /** Wave is on screen. Off screen (or with "Move with the music" off) nothing is measured. */
    @Volatile var visible = false
        set(value) {
            field = value
            _visibleFlow.value = value
        }
    private val _visibleFlow = MutableStateFlow(false)
    /** Same as [visible], to react to (the service switches low-power playback on it). */
    val visibleFlow: StateFlow<Boolean> = _visibleFlow.asStateFlow()

    internal val _beat = MutableStateFlow(0L)
    /** Changes (to the time it happened) on every detected beat. */
    val beat: StateFlow<Long> = _beat.asStateFlow()
}

/**
 * Passes audio through untouched while measuring it: per-buffer energy, a running average,
 * and a simple onset detector for beats (a jump well above the recent average, at most every
 * ~280 ms). Results are released after [OUTPUT_LATENCY_MS] so they line up with what you hear,
 * since this runs before the audio output buffer.
 */
class BeatMeter : BaseAudioProcessor() {
    private val main = Handler(Looper.getMainLooper())
    private var average = 0.0
    private var lastBeat = 0L
    private var lastLevelPost = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        if (!BeatBus.visible || !app.wave.music.data.WaveSettings.state.value.moveWithMusic) {
            // Nobody's watching: pass the audio straight through without measuring it.
            average = 0.0
            val out = replaceOutputBuffer(remaining)
            out.put(inputBuffer)
            out.flip()
            return
        }
        val shorts = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        var sum = 0.0
        val count = shorts.remaining()
        // Sample every 4th value: plenty for loudness, cheap on the audio thread.
        var i = 0
        while (i < count) {
            val v = shorts.get(i) / 32768.0
            sum += v * v
            i += 4
        }
        val energy = sqrt(sum / (count / 4).coerceAtLeast(1))

        average = if (average == 0.0) energy else average * 0.96 + energy * 0.04
        val now = SystemClock.uptimeMillis()
        val isBeat = energy > average * 1.32 && energy > 0.04 && now - lastBeat > 280
        if (isBeat) lastBeat = now
        val level = (energy / (average * 2.2).coerceAtLeast(0.0001)).toFloat().coerceIn(0f, 1f)

        if (isBeat || now - lastLevelPost > 33) {
            lastLevelPost = now
            main.postDelayed({
                BeatBus._level.value = level
                if (isBeat) BeatBus._beat.value = SystemClock.uptimeMillis()
            }, OUTPUT_LATENCY_MS)
        }

        val out = replaceOutputBuffer(remaining)
        out.put(inputBuffer)
        out.flip()
    }

    override fun onFlush() {
        average = 0.0
    }

    private companion object {
        const val OUTPUT_LATENCY_MS = 260L
    }
}
