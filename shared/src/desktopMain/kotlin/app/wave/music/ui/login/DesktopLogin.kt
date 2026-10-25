package app.wave.music.ui.login

import androidx.compose.runtime.Composable

/** Computers' Google sign-in (an embedded browser); filled in by the desktop app. */
@Composable
internal fun DesktopLogin(onDone: () -> Unit, onCancel: () -> Unit) {
    DesktopLoginHost.content?.invoke(onDone, onCancel)
}

/** The desktop app installs its sign-in screen here (it owns the embedded browser). */
object DesktopLoginHost {
    var content: (@Composable (onDone: () -> Unit, onCancel: () -> Unit) -> Unit)? = null
}
