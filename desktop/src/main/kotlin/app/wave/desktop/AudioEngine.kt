package app.wave.desktop

import app.wave.music.data.WaveSettings
import app.wave.music.playback.BeatBus
import app.wave.music.ui.components.AppFocus
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameFilter
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FrameGrabber
import java.nio.ShortBuffer
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.math.sqrt

/**
 * One song playing on this computer. FFmpeg reads and decodes whatever YouTube sends (Opus, AAC),
 * resampled to 48 kHz stereo 16-bit; Wave applies its own volume, even-volume gain and crossfade
 * fades to the samples, measures the beat, and writes them to the system's audio output.
 * Two voices can play at once while one crossfades into the next.
 */
class Voice(
    private val stream: Stream,
    private val startMs: Long,
    /** Even volume for this song (1 = untouched). */
    private val songGain: Float,
    /** Called on the audio thread when the song plays to its end (not when stopped). */
    private val onEnded: () -> Unit,
    /** Called on the audio thread if the stream can't be played. */
    private val onError: (String) -> Unit,
) {
    @Volatile var paused = false
    @Volatile var positionMs = startMs
        private set
    @Volatile var durationMs = 0L
        private set
    @Volatile var started = false
        private set
    /** Measures the beat (only the main voice does). */
    @Volatile var metering = true
    @Volatile private var stopped = false
    @Volatile private var seekTo: Long? = null
    @Volatile private var speed = 1f
    @Volatile private var speedChanged = false

    // Crossfade: the voice's own fade, from [fadeFrom] to [fadeTo] over [fadeMs] starting at [fadeStart].
    @Volatile private var fadeFrom = 1f
    @Volatile private var fadeTo = 1f
    @Volatile private var fadeStart = 0L
    @Volatile private var fadeMs = 0L
    @Volatile private var stopAfterFade = false

    fun start(initialSpeed: Float) {
        speed = initialSpeed
        Thread(::run, "wave-voice").apply { isDaemon = true }.start()
    }

    fun stop() {
        stopped = true
    }

    fun seek(ms: Long) {
        seekTo = ms
        positionMs = ms
    }

    fun setSpeed(value: Float) {
        if (value != speed) {
            speed = value
            speedChanged = true
        }
    }

    /** Fades from silent up to full over [ms]. */
    fun fadeIn(ms: Long) = fade(0f, 1f, ms, stopAtEnd = false)

    /** Fades out over [ms], then stops. */
    fun fadeOut(ms: Long) = fade(currentFade(), 0f, ms, stopAtEnd = true)

    private fun fade(from: Float, to: Float, ms: Long, stopAtEnd: Boolean) {
        fadeFrom = from
        fadeTo = to
        fadeMs = ms.coerceAtLeast(1)
        fadeStart = System.currentTimeMillis()
        stopAfterFade = stopAtEnd
    }

    private fun currentFade(): Float {
        if (fadeMs == 0L) return fadeTo
        val p = ((System.currentTimeMillis() - fadeStart).toFloat() / fadeMs).coerceIn(0f, 1f)
        // Equal-power curves, so the overlap doesn't dip in loudness.
        val t = if (fadeTo > fadeFrom) kotlin.math.sin(p * Math.PI / 2).toFloat() else kotlin.math.cos(p * Math.PI / 2).toFloat()
        val v = if (fadeTo > fadeFrom) fadeFrom + (fadeTo - fadeFrom) * t else fadeTo + (fadeFrom - fadeTo) * t
        if (p >= 1f && stopAfterFade) stopped = true
        return v
    }

    private fun run() {
        var grabber: FFmpegFrameGrabber? = null
        var filter: FFmpegFrameFilter? = null
        var line: SourceDataLine? = null
        try {
            grabber = FFmpegFrameGrabber(stream.url).apply {
                if (stream.headers.isNotEmpty()) {
                    setOption("headers", stream.headers.entries.joinToString("") { "${it.key}: ${it.value}\r\n" })
                }
                setOption("reconnect", "1")
                setOption("reconnect_streamed", "1")
                setOption("reconnect_delay_max", "4")
                sampleMode = FrameGrabber.SampleMode.SHORT
                sampleRate = RATE
                audioChannels = 2
                start()
            }
            if (startMs > 0) grabber.setTimestamp(startMs * 1000)
            durationMs = grabber.lengthInTime / 1000
            val format = AudioFormat(RATE.toFloat(), 16, 2, true, false)
            line = AudioSystem.getSourceDataLine(format).apply {
                open(format, RATE * 4 / 5) // ~200 ms: quick to react to pause and seek
                start()
            }
            started = true
            var bytes = ByteArray(0)
            val meter = BeatMeter()
            while (!stopped) {
                seekTo?.let { target ->
                    seekTo = null
                    line.flush()
                    grabber.setTimestamp(target * 1000)
                    filter?.let { runCatching { it.stop(); it.release() } }
                    filter = null
                    speedChanged = true
                }
                if (paused) {
                    if (line.isRunning) line.stop()
                    Thread.sleep(25)
                    continue
                }
                if (!line.isRunning) line.start()
                if (speedChanged) {
                    speedChanged = false
                    filter?.let { runCatching { it.stop(); it.release() } }
                    filter = if (speed == 1f) null else FFmpegFrameFilter("atempo=$speed", 2).apply {
                        sampleRate = RATE
                        sampleFormat = avutil.AV_SAMPLE_FMT_S16
                        start()
                    }
                }
                val decoded = grabber.grabSamples() ?: break
                positionMs = grabber.timestamp / 1000
                val frame: Frame = filter?.let { f ->
                    f.push(decoded)
                    f.pullSamples() ?: continue
                } ?: decoded
                val samples = frame.samples?.firstOrNull() as? ShortBuffer ?: continue
                val count = samples.remaining()
                if (bytes.size < count * 2) bytes = ByteArray(count * 2)
                // Wave's volume slider is on a curve, so the middle of the slider sounds like the middle.
                val user = WaveSettings.state.value.appVolume.coerceIn(0f, 1f).let { it * it }
                val gain = user * songGain * currentFade()
                for (i in 0 until count) {
                    val s = (samples.get(i) * gain).toInt().coerceIn(-32768, 32767)
                    bytes[i * 2] = s.toByte()
                    bytes[i * 2 + 1] = (s shr 8).toByte()
                }
                if (metering) meter.measure(samples, count)
                line.write(bytes, 0, count * 2)
            }
            if (!stopped) {
                line.drain()
                onEnded()
            }
        } catch (e: Exception) {
            if (!stopped) onError(e.message ?: e.javaClass.simpleName)
        } finally {
            runCatching { line?.stop(); line?.close() }
            runCatching { filter?.stop(); filter?.release() }
            runCatching { grabber?.release() }
        }
    }

    companion object {
        const val RATE = 48_000

        init {
            avutil.av_log_set_level(avutil.AV_LOG_ERROR)
        }
    }
}

/**
 * Live loudness and beats for "Move with the music", measured on the way to the speakers (the
 * same detector as Android's): per-buffer energy, a running average, and a jump well above it
 * (at most every ~280 ms) is a beat. Results are delayed to line up with what you hear.
 */
private class BeatMeter {
    private var average = 0.0
    private var lastBeat = 0L
    private var lastPost = 0L

    fun measure(samples: ShortBuffer, count: Int) {
        if (!BeatBus.visible || !AppFocus.active.value || !WaveSettings.state.value.moveWithMusic) {
            average = 0.0
            return
        }
        var sum = 0.0
        var i = 0
        while (i < count) {
            val v = samples.get(i) / 32768.0
            sum += v * v
            i += 4
        }
        val energy = sqrt(sum / (count / 4).coerceAtLeast(1))
        average = if (average == 0.0) energy else average * 0.96 + energy * 0.04
        val now = System.currentTimeMillis()
        val isBeat = energy > average * 1.32 && energy > 0.04 && now - lastBeat > 280
        if (isBeat) lastBeat = now
        val level = (energy / (average * 2.2).coerceAtLeast(0.0001)).toFloat().coerceIn(0f, 1f)
        if (isBeat || now - lastPost > 33) {
            lastPost = now
            delayed.schedule({
                BeatBus.levelSink.value = level
                if (isBeat) BeatBus.beatSink.value = System.currentTimeMillis()
            }, OUTPUT_LATENCY_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        }
    }

    companion object {
        private const val OUTPUT_LATENCY_MS = 200L
        private val delayed = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "wave-beat").apply { isDaemon = true } }
    }
}
