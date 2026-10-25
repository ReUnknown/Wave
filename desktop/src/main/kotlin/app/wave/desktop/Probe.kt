package app.wave.desktop

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.runBlocking
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import java.nio.ShortBuffer

/** Development check: search, resolve a stream and decode a few seconds of it, without any UI. */
fun main(args: Array<String>) = runBlocking {
    val query = args.firstOrNull() ?: "rolling in the deep adele"
    val song = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrThrow().items.filterIsInstance<SongItem>().first()
    println("Song: ${song.title} (${song.id})")
    val t0 = System.currentTimeMillis()
    val stream = Streams.resolve(song.id)
    println("Stream in ${System.currentTimeMillis() - t0} ms, loudness=${stream.loudnessDb}, headers=${stream.headers.keys}")
    val grabber = FFmpegFrameGrabber(stream.url).apply {
        if (stream.headers.isNotEmpty()) setOption("headers", stream.headers.entries.joinToString("") { "${it.key}: ${it.value}\r\n" })
        sampleMode = FrameGrabber.SampleMode.SHORT
        sampleRate = 48_000
        audioChannels = 2
        start()
    }
    println("Format ${grabber.format}, codec ${grabber.audioCodecName}, length ${grabber.lengthInTime / 1_000_000}s")
    var samples = 0L
    var peak = 0
    while (samples < 48_000L * 2 * 10) {
        val frame = grabber.grabSamples() ?: break
        val buf = frame.samples?.firstOrNull() as? ShortBuffer ?: continue
        for (i in 0 until buf.remaining()) peak = maxOf(peak, kotlin.math.abs(buf.get(i).toInt()))
        samples += buf.remaining()
    }
    println("Decoded ${samples / 2 / 48_000.0}s of audio, peak $peak")
    grabber.release()
}
