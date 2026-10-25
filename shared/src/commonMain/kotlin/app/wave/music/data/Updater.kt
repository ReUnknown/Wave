package app.wave.music.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.wave.music.platform.WavePlatform
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Wave updates itself from its GitHub releases: once a day (if allowed in settings) or when you
 * tap "Check for updates". A newer release's installer for this platform (the APK on Android,
 * the .dmg / .msi / .deb on computers) downloads in-app and the system's installer takes over.
 * Android releases must be signed with the same key as the installed app, or Android refuses them.
 */
object Updater {
    /** Where releases are published. Must be readable without signing in (a public repo). */
    private const val REPO = "ReUnknown/Wave"
    private const val DAY_MS = 24 * 60 * 60_000L

    data class Release(val version: String, val notes: String, val apkUrl: String, val pageUrl: String)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data object Failed : State
        data class Available(val release: Release) : State
        data class Downloading(val release: Release, val progress: Float) : State
        data class Ready(val release: Release, val file: File) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()
    private val http by lazy { OkHttpClient() }

    /** The daily check (skipped if it ran in the last day, unless [force]). */
    private val store get() = Stores.prefs("updates")
    private val lastCheck = longPreferencesKey("last_check")
    private val found = stringPreferencesKey("found")
    private val version get() = WavePlatform.current.appVersion

    suspend fun check(force: Boolean = false) {
        val prefs = store.data.first()
        if (!force && System.currentTimeMillis() - (prefs[lastCheck] ?: 0L) < DAY_MS) {
            // Still tell the app about an update found earlier today.
            if (_state.value == State.Idle) prefs[found]?.let { restore(it) }
            return
        }
        _state.value = State.Checking
        val release = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("https://api.github.com/repos/$REPO/releases?per_page=10")
                    .header("Accept", "application/vnd.github+json")
                    .build()
                http.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    val list = JSONArray(requireNotNull(response.body).string())
                    // Newest release (betas included) with an installer for this platform attached.
                    (0 until list.length()).map { list.getJSONObject(it) }
                        .filterNot { it.optBoolean("draft") }
                        .mapNotNull(::toRelease)
                        .maxWithOrNull { a, b -> compareVersions(a.version, b.version) }
                }
            }
        }
        store.edit { it[lastCheck] = System.currentTimeMillis() }
        release.onFailure { _state.value = State.Failed }
        val latest = release.getOrNull()
        if (release.isSuccess && latest != null && compareVersions(latest.version, version) > 0) {
            val saved = kotlinx.serialization.json.buildJsonObject {
                put("version", kotlinx.serialization.json.JsonPrimitive(latest.version))
                put("notes", kotlinx.serialization.json.JsonPrimitive(latest.notes))
                put("apk", kotlinx.serialization.json.JsonPrimitive(latest.apkUrl))
                put("page", kotlinx.serialization.json.JsonPrimitive(latest.pageUrl))
            }.toString()
            store.edit { it[found] = saved }
            _state.value = State.Available(latest)
        } else if (release.isSuccess) {
            store.edit { it.remove(found) }
            _state.value = State.UpToDate
        }
    }

    private fun restore(json: String) {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return
        val release = Release(o.optString("version"), o.optString("notes"), o.optString("apk"), o.optString("page"))
        if (compareVersions(release.version, version) > 0) _state.value = State.Available(release)
    }

    private fun toRelease(o: JSONObject): Release? {
        val assets = o.optJSONArray("assets") ?: return null
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(WavePlatform.current.updateAssetSuffix) } ?: return null
        return Release(
            version = o.optString("tag_name").removePrefix("v"),
            notes = o.optString("body"),
            apkUrl = apk.optString("browser_download_url"),
            pageUrl = o.optString("html_url"),
        )
    }

    /** Downloads the release's installer (with progress), then hands it to the system. */
    suspend fun download(release: Release) {
        _state.value = State.Downloading(release, 0f)
        val dir = Stores.file("updates").apply { mkdirs() }
        val file = File(dir, "Wave-${release.version}${WavePlatform.current.updateAssetSuffix}")
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                dir.listFiles()?.forEach { if (it != file) it.delete() }
                http.newCall(Request.Builder().url(release.apkUrl).build()).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                    val body = requireNotNull(response.body)
                    val total = body.contentLength().takeIf { it > 0 } ?: -1L
                    file.outputStream().use { out ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var done = 0L
                            var lastShown = 0f
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                                done += n
                                if (total > 0) {
                                    val p = done.toFloat() / total
                                    if (p - lastShown > 0.01f) {
                                        lastShown = p
                                        _state.value = State.Downloading(release, p)
                                    }
                                }
                            }
                        }
                    }
                }
            }.isSuccess
        }
        if (!ok) {
            _state.value = State.Available(release)
            return
        }
        _state.value = State.Ready(release, file)
        install(file)
    }

    fun install(file: File) = WavePlatform.current.installUpdate(file)

    /**
     * Compares versions like "1.0.0", "1.0.0-beta" and "1.0.0-beta.2": numbers first; for the same
     * numbers a final release beats a pre-release, and pre-releases compare part by part.
     */
    fun compareVersions(a: String, b: String): Int {
        fun split(v: String) = v.trim().removePrefix("v").split('-', limit = 2).let { it[0] to it.getOrNull(1) }
        val (na, pa) = split(a)
        val (nb, pb) = split(b)
        val xa = na.split('.').map { it.toIntOrNull() ?: 0 }
        val xb = nb.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(xa.size, xb.size)) {
            val d = (xa.getOrNull(i) ?: 0).compareTo(xb.getOrNull(i) ?: 0)
            if (d != 0) return d
        }
        if (pa == null && pb == null) return 0
        if (pa == null) return 1
        if (pb == null) return -1
        val qa = pa.split('.')
        val qb = pb.split('.')
        for (i in 0 until maxOf(qa.size, qb.size)) {
            val sa = qa.getOrNull(i) ?: return -1
            val sb = qb.getOrNull(i) ?: return 1
            val ia = sa.toIntOrNull()
            val ib = sb.toIntOrNull()
            val d = if (ia != null && ib != null) ia.compareTo(ib) else sa.compareTo(sb)
            if (d != 0) return d
        }
        return 0
    }
}
