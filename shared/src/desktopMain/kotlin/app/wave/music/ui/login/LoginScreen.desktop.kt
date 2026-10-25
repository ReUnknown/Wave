package app.wave.music.ui.login

import androidx.compose.runtime.Composable

@Composable
actual fun LoginScreen(onDone: () -> Unit, onCancel: () -> Unit) {
    DesktopLogin(onDone, onCancel)
}
