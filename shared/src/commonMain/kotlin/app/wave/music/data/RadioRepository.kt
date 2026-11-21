package app.wave.music.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** A live radio station from Radio Browser's public directory. */
data class Station(
    val uuid: String,
    val name: String,
    val streamUrl: String,
    val favicon: String?,
    val country: String,
    val tags: List<String>,
    val codec: String,
    val bitrate: Int,
) {
    /** How it's known to the player: stations play as "radio:<uuid>". */
    val mediaId: String get() = "radio:$uuid"

    /** One short line about it: country and a couple of genres. */
    val about: String get() = listOfNotNull(country.takeIf { it.isNotBlank() }, tags.take(2).joinToString(", ").takeIf { it.isNotBlank() }).joinToString(" · ")
}

/** Whether a player id is a live radio station rather than a song. */
fun isRadioId(id: String?): Boolean = id?.startsWith("radio:") == true

/**
 * Live radio from Radio Browser (radio-browser.info), a free, open directory of stations around
 * the world. Only streams Wave can play directly (MP3, AAC and Ogg, no playlists) are listed.
 */
object RadioRepository {
    private const val API = "https://de1.api.radio-browser.info/json"
    private val http = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()
    private val byId = ConcurrentHashMap<String, Station>()
    private val playable = setOf("MP3", "AAC", "AAC+", "OGG", "OPUS")

    private fun get(url: okhttp3.HttpUrl): String? = runCatching {
        // Radio Browser asks apps to say who they are.
        http.newCall(Request.Builder().url(url).header("User-Agent", "Wave/1.3").build()).execute().use { r ->
            if (r.isSuccessful) r.body?.string() else null
        }
    }.getOrNull()

    private fun parse(o: JSONObject): Station? {
        if (o.optDouble("hls", 0.0).toInt() == 1) return null
        val codec = o.optString("codec").uppercase()
        if (codec.isNotBlank() && codec !in playable) return null
        // Secure streams only: Android blocks plain http, and Wave keeps that protection on.
        val url = o.optString("url_resolved").ifBlank { o.optString("url") }
        if (!url.startsWith("https://")) return null
        val name = o.optString("name").trim().ifBlank { return null }
        return Station(
            uuid = o.optString("stationuuid"),
            name = name,
            streamUrl = url,
            favicon = o.optString("favicon").takeIf { it.startsWith("http") },
            country = o.optString("country"),
            tags = o.optString("tags").split(",").map { it.trim() }.filter { it.isNotBlank() },
            codec = codec,
            bitrate = o.optDouble("bitrate", 0.0).toInt(),
        ).also { byId[it.uuid] = it }
    }

    private fun list(body: String?): List<Station> = runCatching {
        val array = JSONArray(body ?: return emptyList())
        (0 until array.length()).mapNotNull { parse(array.getJSONObject(it)) }.distinctBy { it.name.lowercase() }
    }.getOrDefault(emptyList())

    private fun search(params: Map<String, String>): List<Station> {
        val url = "$API/stations/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("hidebroken", "true")
            addQueryParameter("is_https", "true")
            addQueryParameter("order", "clickcount")
            addQueryParameter("reverse", "true")
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        return list(get(url))
    }

    /** What people listen to most in [countryCode] (e.g. "US"), or worldwide when null. */
    suspend fun popular(countryCode: String? = null, limit: Int = 30): List<Station> = withContext(Dispatchers.IO) {
        search(buildMap {
            put("limit", limit.toString())
            if (!countryCode.isNullOrBlank()) put("countrycode", countryCode)
        })
    }

    /** Popular stations for each genre in [tags], taking turns so no one genre fills the row. */
    suspend fun forTags(tags: List<String>, perTag: Int = 6): List<Station> = withContext(Dispatchers.IO) {
        val lists = tags.map { tag -> search(mapOf("tag" to tag, "limit" to (perTag * 2).toString())).take(perTag) }
        buildList {
            for (i in 0 until perTag) lists.forEach { l -> l.getOrNull(i)?.let { if (none { s -> s.uuid == it.uuid }) add(it) } }
        }
    }

    /**
     * The genres someone's into, from the words around their music (home's section and playlist
     * names, songs and artists they played), as Radio Browser tags, most-mentioned first.
     */
    fun tasteTags(words: List<String>, max: Int = 3): List<String> {
        val genres = linkedMapOf(
            "hiphop" to listOf("hip hop", "hip-hop", "hiphop", "rap", "trap", "drill"),
            "pop" to listOf("pop", "hits", "top 40", "chart"),
            "rnb" to listOf("r&b", "rnb", "r & b", "neo soul"),
            "rock" to listOf("rock", "alternative", "grunge", "punk"),
            "country" to listOf("country", "americana", "bluegrass"),
            "jazz" to listOf("jazz", "swing", "bossa"),
            "classical" to listOf("classical", "orchestra", "piano", "symphony"),
            "chillout" to listOf("chill", "lofi", "lo-fi", "relax", "sleep", "study", "ambient"),
            "electronic" to listOf("electronic", "edm", "dance", "house", "techno", "phonk", "trance", "dubstep"),
            "latin" to listOf("latin", "reggaeton", "salsa", "bachata", "cumbia"),
            "kpop" to listOf("k-pop", "kpop"),
            "indie" to listOf("indie"),
            "metal" to listOf("metal"),
            "soul" to listOf("soul", "motown", "funk"),
            "christian" to listOf("christian", "worship", "gospel", "praise"),
            "80s" to listOf("80s", "'80s", "eighties"),
            "90s" to listOf("90s", "'90s", "nineties"),
            "oldies" to listOf("oldies", "throwback", "classics", "golden"),
            "reggae" to listOf("reggae", "dancehall"),
            "afrobeats" to listOf("afrobeat", "afro", "amapiano"),
        )
        val text = words.joinToString(" | ") { it.lowercase() }
        return genres.map { (tag, keys) -> tag to keys.sumOf { k -> Regex("(?<![a-z])" + Regex.escape(k) + "(?![a-z])").findAll(text).count() } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(max)
            .map { it.first }
    }

    /** Stations whose name, or genre, matches [query]. */
    suspend fun find(query: String, limit: Int = 40): List<Station> = withContext(Dispatchers.IO) {
        val q = query.trim().replace(Regex("(?i)\\bradio\\b"), "").trim().ifBlank { return@withContext popular(limit = limit) }
        val byName = search(mapOf("name" to q, "limit" to limit.toString()))
        if (byName.size >= 10) byName
        else (byName + search(mapOf("tag" to q.lowercase(), "limit" to limit.toString()))).distinctBy { it.uuid }
    }

    /**
     * The address to play a station at, right now. Asking through /url also counts the listen, as
     * Radio Browser asks apps to do. Runs on the player's loading thread.
     */
    fun streamFor(uuid: String): String? {
        // Known already (from the list you tapped it in): play at once, and count the listen on the side.
        byId[uuid]?.streamUrl?.let { known ->
            Thread { get("$API/url/$uuid".toHttpUrl()) }.start()
            return known
        }
        val body = get("$API/url/$uuid".toHttpUrl())
        val url = runCatching { JSONObject(body ?: "").optString("url") }.getOrNull()
        return url?.takeIf { it.startsWith("https://") } ?: byId[uuid]?.streamUrl
    }
}
