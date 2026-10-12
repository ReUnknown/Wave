package app.wave.music

import android.os.Bundle
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.crossfade
import okio.Path.Companion.toPath
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
import app.wave.music.ui.components.isDesktop
import app.wave.music.ui.login.LoginScreen
import app.wave.music.ui.theme.WaveTheme
import app.wave.music.ui.welcome.WelcomeScreen
import kotlinx.coroutines.launch
import timber.log.Timber

class MainActivity : ComponentActivity() {
    /** Googlebook / ChromeOS, or forced on in debug builds for testing. */
    private val desktopMode = androidx.compose.runtime.mutableStateOf(false)

    override fun onStart() {
        super.onStart()
        app.wave.music.playback.BeatBus.visible = true
        app.wave.music.ui.components.AppFocus.onStarted(true)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        app.wave.music.data.Links.receive(intent)
        // Debug builds: a preview launch can arrive while Wave is already open.
        if (BuildConfig.DEBUG && intent.getBooleanExtra("wave_preview", false)) SessionRepository.startPreview()
        if (BuildConfig.DEBUG && intent.getBooleanExtra("wave_desktop", false)) desktopMode.value = true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        app.wave.music.ui.components.AppFocus.onFocus(hasFocus)
    }

    /**
     * Keyboard shortcuts (Googlebook, ChromeOS, tablets with keyboards): Space plays/pauses,
     * Ctrl+→/← skip, Ctrl+F or / search, Ctrl+L likes, Esc goes back. Ignored while typing.
     */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
            !app.wave.music.ui.components.Shortcuts.typing
        ) {
            val handled = when {
                event.keyCode == android.view.KeyEvent.KEYCODE_SPACE && !event.isCtrlPressed -> { PlayerConnection.togglePlay(); true }
                event.isCtrlPressed && event.keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> { PlayerConnection.next(); true }
                event.isCtrlPressed && event.keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT -> { PlayerConnection.previous(); true }
                event.isCtrlPressed && event.keyCode == android.view.KeyEvent.KEYCODE_L -> { PlayerConnection.toggleLike(); true }
                (event.isCtrlPressed && event.keyCode == android.view.KeyEvent.KEYCODE_F) ||
                    event.keyCode == android.view.KeyEvent.KEYCODE_SLASH -> { app.wave.music.ui.components.Shortcuts.requestSearch(); true }
                else -> false
            }
            if (handled) return true
        }
        if (event.action == android.view.KeyEvent.ACTION_UP && event.keyCode == android.view.KeyEvent.KEYCODE_ESCAPE) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        app.wave.music.playback.BeatBus.visible = false
        app.wave.music.ui.components.AppFocus.onStarted(false)
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG && Timber.treeCount == 0) Timber.plant(Timber.DebugTree())
        PlayerConnection.connect(this)
        // Opened from a YouTube Music link (or one shared to Wave).
        if (savedInstanceState == null) app.wave.music.data.Links.receive(intent)
        // Covers fade in as they load instead of popping in.
        // Covers are saved for reuse, but never more than 200 MB of them.
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .crossfade(200)
                .diskCache {
                    coil3.disk.DiskCache.Builder()
                        .directory(context.cacheDir.resolve("image_cache").absolutePath.toPath())
                        .maxSizeBytes(200L * 1024 * 1024)
                        .build()
                }
                .build()
        }
        desktopMode.value = isDesktop(this) || (BuildConfig.DEBUG && intent.getBooleanExtra("wave_desktop", false))
        setContent {
            // Googlebook / ChromeOS (or, in debug builds, `--ez wave_desktop true` to try it anywhere).
            androidx.compose.runtime.CompositionLocalProvider(app.wave.music.ui.components.LocalDesktop provides desktopMode.value) {
            WaveTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val session by SessionRepository.session.collectAsState()
                val restored by SessionRepository.restored.collectAsState()
                var signingIn by rememberSaveable { mutableStateOf(false) }

                // Keep the screen awake while music plays and Wave is open (lyrics, the player).
                val view = LocalView.current
                val playing by remember { PlayerConnection.state.map { it.isPlaying }.distinctUntilChanged() }.collectAsState(false)
                DisposableEffect(playing) {
                    view.keepScreenOn = playing
                    onDispose { view.keepScreenOn = false }
                }

                LaunchedEffect(Unit) {
                    WaveSettings.load(context.applicationContext)
                    if (!SessionRepository.restored.value) SessionRepository.restore(context.applicationContext)
                    // Debug builds: `adb shell am start ... --ez wave_preview true` browses as a guest.
                    if (BuildConfig.DEBUG && intent.getBooleanExtra("wave_preview", false)) SessionRepository.startPreview()
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
                            onSignOut = { scope.launch { app.wave.music.data.signOutAndForget(context.applicationContext) } },
                        )
                    }
                }
            }
            }
        }
    }
}
