package app.wave.music

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
