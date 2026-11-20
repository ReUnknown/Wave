package app.wave.music

import app.wave.music.playback.WaveService
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.webkit.CookieManager
import androidx.core.content.FileProvider
import app.wave.music.platform.Platform
import app.wave.music.platform.WavePlatform
import app.wave.music.playback.AndroidPlayer
import app.wave.music.playback.PlayerConnection
import app.wave.music.widget.WaveWidget
import java.io.File

/**
 * Sets Wave up before anything else runs (the app, the playback service, the widget): the
 * Android platform pieces and the Media3 player behind [PlayerConnection].
 */
class WaveApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WavePlatform.current = AndroidPlatform(this)
        PlayerConnection.backend = AndroidPlayer
    }
}

private class AndroidPlatform(private val app: Application) : Platform {
    override val dataDir: File get() = app.filesDir
    override val isComputer: Boolean get() = isDesktop(app)
    override val hasKeyboard: Boolean get() = app.resources.configuration.keyboard == Configuration.KEYBOARD_QWERTY
    override val appVersion: String = BuildConfig.VERSION_NAME
    override val debug: Boolean = BuildConfig.DEBUG
    override val updateAssetSuffix = ".apk"

    override fun share(title: String, url: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, url)
            .putExtra(Intent.EXTRA_TITLE, title)
        app.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun openUrl(url: String) {
        app.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun forgetAccount() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WaveWidget.update(app, null)
    }

    override fun storageUsage(): Pair<Long, Long> {
        val songs = runCatching { WaveService.cache(app).cacheSpace }.getOrDefault(0L)
        val covers = runCatching { coil3.SingletonImageLoader.get(app).diskCache?.size ?: 0L }.getOrDefault(0L)
        return songs to covers
    }

    override fun clearSongCache() {
        runCatching {
            val cache = WaveService.cache(app)
            cache.keys.toList().forEach { cache.removeResource(it) }
        }
    }

    override fun clearCoverCache() {
        runCatching {
            val loader = coil3.SingletonImageLoader.get(app)
            loader.diskCache?.clear()
            loader.memoryCache?.clear()
        }
    }

    // Battery saver, kept up to date as the phone turns it on and off.
    private val saver = kotlinx.coroutines.flow.MutableStateFlow(false).also { flow ->
        val power = app.getSystemService(android.os.PowerManager::class.java)
        flow.value = power?.isPowerSaveMode == true
        app.registerReceiver(
            object : android.content.BroadcastReceiver() {
                override fun onReceive(context: android.content.Context, intent: Intent) {
                    flow.value = power?.isPowerSaveMode == true
                }
            },
            android.content.IntentFilter(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
        )
    }
    override val powerSave: kotlinx.coroutines.flow.StateFlow<Boolean> get() = saver

    override fun installUpdate(file: File) {
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", file)
        app.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/**
 * Desktop-class Android: Googlebook OS (Android + ChromeOS), ChromeOS, and anything Android itself
 * reports as a PC.
 */
fun isDesktop(context: Context): Boolean {
    val pm = context.packageManager
    return pm.hasSystemFeature(PackageManager.FEATURE_PC) ||
        pm.hasSystemFeature("org.chromium.arc") ||
        pm.hasSystemFeature("org.chromium.arc.device_management")
}
