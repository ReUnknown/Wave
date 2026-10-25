package app.wave.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import java.nio.ShortBuffer
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

data class EngineState(
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val error: String? = null,
)

/**
 * Plays a stream on this computer: FFmpeg reads and decodes whatever YouTube sends (Opus, AAC),
 * resampled to 48 kHz stereo 16-bit, into the system's audio output. Having the raw samples in
 * hand is what makes Wave's own volume, even volume and crossfade possible here too.
 */
object AudioEngine {
    private const val RATE = 48_000

    private val _state = MutableStateFlow(EngineState())
    val state: StateFlow<EngineState> = _state.asStateFlow()

    @Volatile private var session = 0
    @Volatile private var paused = false
    @Volatile private var seekToMs: Long? = null
    @Volatile var volume = 1f

    init {
        avutil.av_log_set_level(avutil.AV_LOG_ERROR)
    }

    fun play(stream: Stream) {
        val id = ++session
        paused = false
        seekToMs = null
        _state.value = EngineState(buffering = true)
        Thread({ run(id, stream) }, "wave-audio").apply { isDaemon = true }.start()
    }

    fun togglePause() {
        paused = !paused
        _state.value = _state.value.copy(playing = !paused)
    }

    fun seekTo(ms: Long) {
        seekToMs = ms
    }

    fun stop() {
        session++
        _state.value = EngineState()
    }

    private fun run(id: Int, stream: Stream) {
        var grabber: FFmpegFrameGrabber? = null
        var line: SourceDataLine? = null
        try {
            grabber = FFmpegFrameGrabber(stream.url).apply {
                if (stream.headers.isNotEmpty()) {
                    setOption("headers", stream.headers.entries.joinToString("") { "${it.key}: ${it.value}\r\n" })
                }
                setOption("reconnect", "1")
                setOption("reconnect_streamed", "1")
                sampleMode = FrameGrabber.SampleMode.SHORT
                sampleRate = RATE
                audioChannels = 2
                start()
            }
            val format = AudioFormat(RATE.toFloat(), 16, 2, true, false)
            line = AudioSystem.getSourceDataLine(format).apply {
                open(format, RATE * 4 / 4) // ~250 ms of buffer: quick to react to pause and seek
                start()
            }
            val durationMs = grabber.lengthInTime / 1000
            var bytes = ByteArray(0)
            while (session == id) {
                seekToMs?.let { target ->
                    seekToMs = null
                    line.flush()
                    grabber.setTimestamp(target * 1000)
                }
                if (paused) {
                    if (line.isRunning) line.stop()
                    Thread.sleep(30)
                    continue
                }
                if (!line.isRunning) line.start()
                val frame = grabber.grabSamples() ?: break
                val samples = frame.samples?.firstOrNull() as? ShortBuffer ?: continue
                val count = samples.remaining()
                if (bytes.size < count * 2) bytes = ByteArray(count * 2)
                val gain = volume
                for (i in 0 until count) {
                    val s = (samples.get(i) * gain).toInt().coerceIn(-32768, 32767)
                    bytes[i * 2] = s.toByte()
                    bytes[i * 2 + 1] = (s shr 8).toByte()
                }
                line.write(bytes, 0, count * 2)
                _state.value = EngineState(playing = true, positionMs = grabber.timestamp / 1000, durationMs = durationMs)
            }
            if (session == id) {
                line.drain()
                _state.value = _state.value.copy(playing = false, positionMs = durationMs)
            }
        } catch (e: Exception) {
            if (session == id) _state.value = EngineState(error = e.message ?: e.javaClass.simpleName)
        } finally {
            runCatching { line?.close() }
            runCatching { grabber?.release() }
        }
    }
}
