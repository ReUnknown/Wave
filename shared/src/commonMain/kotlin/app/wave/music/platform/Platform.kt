package app.wave.music.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import java.io.File

/**
 * What only the platform Wave runs on can do. Android and the computer app each install their own
 * at startup; everything else in Wave is shared.
 */
interface Platform {
    /** Folder for Wave's own files: settings, session, likes, caches. */
    val dataDir: File
    /** A computer (Googlebook/ChromeOS, Windows, macOS, Linux): dropdown menus, shortcuts, no swipes. */
    val isComputer: Boolean
    /** A hardware keyboard is attached. */
    val hasKeyboard: Boolean
    /** This version of Wave, e.g. "1.0.0". */
    val appVersion: String
    /** Development build (enables the guest preview). */
    val debug: Boolean
    /** What a release asset for this platform ends with (".apk", ".dmg", ".msi", ".deb"). */
    val updateAssetSuffix: String

    /** The system's share sheet (phones), or copying the link (computers). */
    fun share(title: String, url: String)
    fun openUrl(url: String)
    /** Sign-out: forget the web sign-in (cookies) and anything the platform shows of the account (widget). */
    fun forgetAccount()
    /** Hands a downloaded update to the system installer. */
    fun installUpdate(file: File)
}

object WavePlatform {
    lateinit var current: Platform
}

/** The window's size in dp, like Android's configuration (also on computers, where it's the window). */
class WindowDp(val screenWidthDp: Int, val screenHeightDp: Int) {
    val smallestScreenWidthDp: Int get() = minOf(screenWidthDp, screenHeightDp)
}

@Composable
fun currentWindowDp(): WindowDp {
    val size = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current.density
    return WindowDp((size.width / density).toInt(), (size.height / density).toInt())
}
