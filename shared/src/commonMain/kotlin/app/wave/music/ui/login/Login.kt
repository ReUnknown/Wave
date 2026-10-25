package app.wave.music.ui.login

import androidx.compose.runtime.Composable

/**
 * Google sign-in: a web page where you sign in to YouTube Music, after which Wave keeps the
 * session. Android shows it in a WebView; computers in an embedded browser.
 */
@Composable
expect fun LoginScreen(onDone: () -> Unit, onCancel: () -> Unit)
