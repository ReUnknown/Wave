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
        val body = get("$API/url/$uuid".toHttpUrl())
        val url = runCatching { JSONObject(body ?: "").optString("url") }.getOrNull()
        return url?.takeIf { it.startsWith("https://") } ?: byId[uuid]?.streamUrl
    }
}
