package app.wave.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.wave.music.data.SessionRepository
import app.wave.music.data.WaveSettings
import app.wave.music.playback.PlayerConnection
import app.wave.music.ui.home.HomeScreen
import app.wave.music.ui.login.LoginScreen
import app.wave.music.ui.theme.WaveTheme
import app.wave.music.ui.welcome.WelcomeScreen
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG && Timber.treeCount == 0) Timber.plant(Timber.DebugTree())
        PlayerConnection.connect(this)
        setContent {
            WaveTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val session by SessionRepository.session.collectAsState()
                val restored by SessionRepository.restored.collectAsState()
                var signingIn by rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    WaveSettings.load(context.applicationContext)
                    if (!SessionRepository.restored.value) SessionRepository.restore(context.applicationContext)
                }

                if (signingIn) {
                    LoginScreen(onDone = { signingIn = false }, onCancel = { signingIn = false })
                } else if (restored) {
                    val current = session
                    if (current == null) {
                        WelcomeScreen(onSignIn = { signingIn = true })
                    } else {
                        HomeScreen(
                            session = current,
                            onSignOut = { scope.launch { SessionRepository.signOut(context.applicationContext) } },
                        )
                    }
                }
            }
        }
    }
}
