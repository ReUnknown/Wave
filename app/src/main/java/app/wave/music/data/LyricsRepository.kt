package app.wave.music.data

import android.util.Xml
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.WatchEndpoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class LyricWord(val startMs: Long, val endMs: Long, val text: String, val spaceAfter: Boolean)

data class LyricLine(
    val startMs: Long,
    val endMs: Long,
    val words: List<LyricWord>,
    /** Backing vocals, shown smaller under the main line. */
    val background: List<LyricWord> = emptyList(),
) {
    val text: String get() = words.joinToString("") { it.text + if (it.spaceAfter) " " else "" }.trim()
}

/**
 * [wordSynced]: real per-word timing (Apple Music style). Otherwise lines are timed and words
 * are spread across each line by length, or there's no timing at all ([synced] false).
 */
data class Lyrics(val lines: List<LyricLine>, val synced: Boolean, val wordSynced: Boolean, val source: String)

/**
 * Lyrics, best first: word-timed lyrics from Better Lyrics (Apple Music's TTML), then
 * line-timed from LRCLIB, then YouTube Music's plain lyrics. Cached per song.
 */
object LyricsRepository {
    private val http = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build()
    private val cache = ConcurrentHashMap<String, Lyrics>()
    private val lrcStamp = Regex("""\[(\d+):(\d+)(?:[.:](\d{1,3}))?]""")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<String, Deferred<Lyrics?>>()

    /**
     * Word-timed and line-timed sources are asked at the same time and the best answer wins,
     * so a slow source never holds things up. Requests already in flight (e.g. from prefetching
     * when the song started) are shared instead of repeated.
     */
    suspend fun load(videoId: String, title: String, artist: String, durationMs: Long, album: String? = null): Lyrics? {
        cache[videoId]?.let { return it }
        val job = inFlight.getOrPut(videoId) {
            scope.async {
                coroutineScope {
                    val words = async { runCatching { fromBetterLyrics(title, artist, durationMs, album) }.getOrNull() }
                    val lines = async { runCatching { fromLrcLib(title, artist, durationMs) }.getOrNull() }
                    words.await()?.takeIf { it.wordSynced }
                        ?: lines.await()?.takeIf { it.synced }
                        ?: words.await()
                        ?: lines.await()
                        ?: runCatching { fromYouTube(videoId) }.getOrNull()
                }.also { found ->
                    if (found != null) cache[videoId] = found
                    inFlight.remove(videoId)
                }
            }
        }
        return job.await()
    }

    private fun cleanTitle(title: String) = title
        .replace(Regex("""\s*[(\[](feat|ft|with|prod)\.?[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\s*-\s*(remaster(ed)?|live|radio edit).*$""", RegexOption.IGNORE_CASE), "")
        .trim()

    private fun mainArtist(artist: String) = artist.split(",", "&", " x ", " feat").first().trim()

    private fun get(url: okhttp3.HttpUrl): String? {
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android) Wave")
            .header("Accept", "application/json")
            .build()
        http.newCall(request).execute().use { response ->
            return if (response.isSuccessful) response.body?.string() else null
        }
    }

    // ---- Better Lyrics: Apple Music TTML with per-word timing ----

    private fun fromBetterLyrics(title: String, artist: String, durationMs: Long, album: String?): Lyrics? {
        val url = "https://lyrics-api.boidu.dev/getLyrics".toHttpUrl().newBuilder()
            .addQueryParameter("s", cleanTitle(title))
            .addQueryParameter("a", mainArtist(artist))
            .apply { if (durationMs > 0) addQueryParameter("d", (durationMs / 1000).toString()) }
            .apply { if (!album.isNullOrBlank()) addQueryParameter("al", album) }
            .build()
        val ttml = get(url)?.let { JSONObject(it).optString("ttml") }?.takeIf { it.isNotBlank() } ?: return null
        val lines = parseTtml(ttml)
        if (lines.isEmpty()) return null
        val wordTimed = lines.any { it.words.size > 1 }
        return Lyrics(if (wordTimed) lines else lines.map(::spreadWords), synced = true, wordSynced = wordTimed, source = "Apple Music via Better Lyrics")
    }

    private fun parseTime(value: String?): Long {
        if (value.isNullOrBlank()) return -1
        val parts = value.trim().removeSuffix("s").split(":")
        val seconds = parts.last().toDouble()
        val minutes = parts.getOrNull(parts.size - 2)?.toLong() ?: 0
        val hours = parts.getOrNull(parts.size - 3)?.toLong() ?: 0
        return ((hours * 3600 + minutes * 60) * 1000 + seconds * 1000).toLong()
    }

    private fun parseTtml(ttml: String): List<LyricLine> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(StringReader(ttml))
        }
        val lines = mutableListOf<LyricLine>()
        var lineStart = -1L
        var lineEnd = -1L
        var words = mutableListOf<LyricWord>()
        var background = mutableListOf<LyricWord>()
        var inP = false
        var bgDepth = 0
        var spanStart = -1L
        var spanEnd = -1L
        var spanText = StringBuilder()
        var inWordSpan = false

        fun target() = if (bgDepth > 0) background else words
        fun markSpace() {
            val list = target()
            if (list.isNotEmpty()) list[list.lastIndex] = list.last().copy(spaceAfter = true)
        }

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "p" -> {
                        inP = true
                        lineStart = parseTime(parser.getAttributeValue(null, "begin"))
                        lineEnd = parseTime(parser.getAttributeValue(null, "end"))
                        words = mutableListOf()
                        background = mutableListOf()
                    }
                    "span" -> if (inP) {
                        val role = parser.getAttributeValue(null, "ttm:role")
                        if (role == "x-bg") {
                            bgDepth++
                        } else {
                            spanStart = parseTime(parser.getAttributeValue(null, "begin"))
                            spanEnd = parseTime(parser.getAttributeValue(null, "end"))
                            spanText = StringBuilder()
                            inWordSpan = spanStart >= 0
                        }
                    }
                }
                XmlPullParser.TEXT -> if (inP) {
                    val text = parser.text
                    if (inWordSpan) {
                        spanText.append(text)
                    } else if (text.isNotEmpty() && text.isBlank()) {
                        markSpace()
                    } else if (text.isNotBlank()) {
                        // Untimed text directly in the line: treat the line as one timed chunk.
                        target() += LyricWord(lineStart, lineEnd, text.trim(), text.endsWith(" "))
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "span" -> if (inWordSpan) {
                        val raw = spanText.toString()
                        if (raw.isNotBlank()) {
                            target() += LyricWord(spanStart, spanEnd, raw.trim().removeSurrounding("(", ")"), raw.endsWith(" "))
                        }
                        inWordSpan = false
                    } else if (bgDepth > 0) {
                        bgDepth--
                    }
                    "p" -> {
                        inP = false
                        if (words.isNotEmpty() || background.isNotEmpty()) {
                            lines += LyricLine(lineStart, lineEnd, words.toList(), background.toList())
                        }
                    }
                }
            }
        }
        return lines.sortedBy { it.startMs }
    }

    // ---- LRCLIB: line timing ----

    private fun fromLrcLib(title: String, artist: String, durationMs: Long): Lyrics? {
        val track = cleanTitle(title)
        val name = mainArtist(artist)
        val exact = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", track)
            .addQueryParameter("artist_name", name)
            .apply { if (durationMs > 0) addQueryParameter("duration", (durationMs / 1000).toString()) }
            .build()
        val candidates = mutableListOf<JSONObject>()
        get(exact)?.let { candidates += JSONObject(it) }
        if (candidates.none { it.optString("syncedLyrics").isNotBlank() }) {
            val search = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", track)
                .addQueryParameter("artist_name", name)
                .build()
            get(search)?.let { body ->
                val array = JSONArray(body)
                for (i in 0 until array.length()) candidates += array.getJSONObject(i)
            }
        }
        val durationSec = durationMs / 1000.0
        val best = candidates
            .filter { durationMs <= 0 || kotlin.math.abs(it.optDouble("duration", durationSec) - durationSec) < 6 }
            .sortedByDescending { it.optString("syncedLyrics").isNotBlank() }
            .firstOrNull() ?: return null

        best.optString("syncedLyrics").takeIf { it.isNotBlank() }?.let { lrc ->
            val lines = parseLrc(lrc, durationMs)
            if (lines.isNotEmpty()) return Lyrics(lines, synced = true, wordSynced = false, source = "LRCLIB")
        }
        return best.optString("plainLyrics").takeIf { it.isNotBlank() }?.let { plain -> plainLyrics(plain, "LRCLIB") }
    }

    private fun parseLrc(lrc: String, durationMs: Long): List<LyricLine> {
        val timed = lrc.lines().flatMap { raw ->
            val stamps = lrcStamp.findAll(raw).toList()
            if (stamps.isEmpty()) return@flatMap emptyList()
            val text = raw.substring(stamps.last().range.last + 1).trim()
            stamps.map { m ->
                val (min, sec, frac) = m.destructured
                val ms = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.take(3).toLong()
                }
                (min.toLong() * 60_000 + sec.toLong() * 1000 + ms) to text
            }
        }.sortedBy { it.first }
        return timed.mapIndexed { i, (start, text) ->
            val next = timed.getOrNull(i + 1)?.first ?: (if (durationMs > 0) durationMs else start + 5_000)
            // Lines usually finish a little before the next one starts.
            val end = minOf(next, start + maxOf(1_500, ((next - start) * 0.9).toLong()))
            spreadWords(LyricLine(start, end, listOf(LyricWord(start, end, text, false))))
        }
    }

    /** Line-timed only: estimate word timing by spreading the line's time over its words by length. */
    private fun spreadWords(line: LyricLine): LyricLine {
        val tokens = line.text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.size <= 1) return line
        val total = tokens.sumOf { it.length + 1 }.toDouble()
        val span = (line.endMs - line.startMs).coerceAtLeast(tokens.size * 120L)
        var t = line.startMs.toDouble()
        val words = tokens.mapIndexed { i, token ->
            val d = span * (token.length + 1) / total
            LyricWord(t.toLong(), (t + d).toLong(), token, i < tokens.lastIndex).also { t += d }
        }
        return line.copy(words = words)
    }

    // ---- YouTube Music: plain text ----

    private suspend fun fromYouTube(videoId: String): Lyrics? {
        val endpoint = YouTube.next(WatchEndpoint(videoId = videoId)).getOrNull()?.lyricsEndpoint ?: return null
        val text = YouTube.lyrics(endpoint).getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return plainLyrics(text, "YouTube Music")
    }

    private fun plainLyrics(text: String, source: String) = Lyrics(
        lines = text.lines().map { LyricLine(-1, -1, listOf(LyricWord(-1, -1, it, false))) },
        synced = false,
        wordSynced = false,
        source = source,
    )
}
