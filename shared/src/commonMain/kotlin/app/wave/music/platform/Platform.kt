package app.wave.music.platform

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    /** The computer app (Windows, macOS, Linux), where Wave sets its own interface size. */
    val scalesInterface: Boolean get() = false
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

/**
 * Extra room at the top of the window for the platform's own title bar when Wave draws under it
 * (macOS: the traffic-light buttons float over Wave's colors). Zero on phones and tablets.
 */
val LocalTitleBarInset = androidx.compose.runtime.compositionLocalOf { 0.dp }

/** Keeps content below the status bar (phones) or the title bar's buttons (computers). */
fun Modifier.topBarPadding(): Modifier = composed {
    statusBarsPadding().padding(top = LocalTitleBarInset.current)
}

/** How far down content starts: the status bar (phones) or title bar (computers). */
@Composable
fun topInset(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + LocalTitleBarInset.current
