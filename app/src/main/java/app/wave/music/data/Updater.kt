package app.wave.music.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.wave.music.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/**
 * Wave updates itself from its GitHub releases: once a day (if allowed in settings) or when you
 * tap "Check for updates". A newer release's APK downloads in-app and Android's installer takes
 * over. Releases must be signed with the same key as the installed app, or Android refuses them.
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
        data class Ready(val release: Release, val apk: File) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()
    private val http by lazy { OkHttpClient() }

    /** The daily check (skipped if it ran in the last day, unless [force]). */
    suspend fun check(context: Context, force: Boolean = false) {
        val prefs = context.getSharedPreferences("updates", Context.MODE_PRIVATE)
        if (!force && System.currentTimeMillis() - prefs.getLong("last_check", 0L) < DAY_MS) {
            // Still tell the app about an update found earlier today.
            if (_state.value == State.Idle) prefs.getString("found", null)?.let { restore(it) }
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
                    val list = org.json.JSONArray(requireNotNull(response.body).string())
                    // Newest release (betas included) that has an APK attached.
                    (0 until list.length()).map { list.getJSONObject(it) }
                        .filterNot { it.optBoolean("draft") }
                        .mapNotNull(::toRelease)
                        .maxWithOrNull { a, b -> compareVersions(a.version, b.version) }
                }
            }
        }
        prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()
        release.onFailure { _state.value = State.Failed }
        release.onSuccess { latest ->
            if (latest != null && compareVersions(latest.version, BuildConfig.VERSION_NAME) > 0) {
                prefs.edit().putString("found", JSONObject().apply {
                    put("version", latest.version); put("notes", latest.notes); put("apk", latest.apkUrl); put("page", latest.pageUrl)
                }.toString()).apply()
                _state.value = State.Available(latest)
            } else {
                prefs.edit().remove("found").apply()
                _state.value = State.UpToDate
            }
        }
    }

    private fun restore(json: String) {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return
        val release = Release(o.optString("version"), o.optString("notes"), o.optString("apk"), o.optString("page"))
        if (compareVersions(release.version, BuildConfig.VERSION_NAME) > 0) _state.value = State.Available(release)
    }

    private fun toRelease(o: JSONObject): Release? {
        val assets = o.optJSONArray("assets") ?: return null
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk") } ?: return null
        return Release(
            version = o.optString("tag_name").removePrefix("v"),
            notes = o.optString("body"),
            apkUrl = apk.optString("browser_download_url"),
            pageUrl = o.optString("html_url"),
        )
    }

    /** Downloads the release's APK (with progress), then opens Android's installer. */
    suspend fun download(context: Context, release: Release) {
        _state.value = State.Downloading(release, 0f)
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, "Wave-${release.version}.apk")
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
        install(context, file)
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

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
