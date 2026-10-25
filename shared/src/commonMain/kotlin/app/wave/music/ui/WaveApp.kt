package app.wave.music.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.wave.music.data.SessionRepository
import app.wave.music.data.WaveSettings
import app.wave.music.data.signOutAndForget
import app.wave.music.playback.PlayerConnection
import app.wave.music.ui.components.LocalDesktop
import app.wave.music.ui.home.HomeScreen
import app.wave.music.ui.login.LoginScreen
import app.wave.music.ui.theme.WaveTheme
import app.wave.music.ui.welcome.WelcomeScreen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Wave, on any screen: restores your session, then shows sign-in, the welcome page or home.
 * [computer] switches on the computer patterns (dropdowns, shortcuts, no swipes);
 * [onPlayingChanged] lets the platform keep the screen awake while music plays;
 * [preview] (development builds) browses as a guest without signing in.
 */
@Composable
fun WaveApp(computer: Boolean, preview: Boolean = false, onPlayingChanged: (Boolean) -> Unit = {}) {
    CompositionLocalProvider(LocalDesktop provides computer) {
        WaveTheme {
          // Text and icons default to the theme's color (black on a dark window otherwise).
          CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides androidx.compose.material3.MaterialTheme.colorScheme.onBackground) {
            val scope = rememberCoroutineScope()
            val session by SessionRepository.session.collectAsState()
            val restored by SessionRepository.restored.collectAsState()
            var signingIn by rememberSaveable { mutableStateOf(false) }

            val playing by remember { PlayerConnection.state.map { it.isPlaying }.distinctUntilChanged() }.collectAsState(false)
            DisposableEffect(playing) {
                onPlayingChanged(playing)
                onDispose { onPlayingChanged(false) }
            }

            LaunchedEffect(Unit) {
                WaveSettings.load()
                if (!SessionRepository.restored.value) SessionRepository.restore()
                if (preview) SessionRepository.startPreview()
            }

            if (signingIn) {
                LoginScreen(onDone = { signingIn = false }, onCancel = { signingIn = false })
            } else if (restored) {
                val current = session
                if (current == null) {
                    WelcomeScreen(onSignIn = { signingIn = true })
                } else {
                    HomeScreen(session = current, onSignOut = { scope.launch { signOutAndForget() } })
                }
            }
          }
        }
    }
}
