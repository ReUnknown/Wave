package app.wave.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.platform.LocalView
import app.wave.music.data.SessionRepository
import app.wave.music.playback.AndroidPlayer
import app.wave.music.playback.PlayerConnection
import app.wave.music.ui.WaveApp
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.crossfade
import okio.Path.Companion.toPath
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
        receiveLink(intent)
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

    /** A YouTube Music link opened in Wave, or text shared to it. */
    private fun receiveLink(intent: android.content.Intent?) {
        when (intent?.action) {
            android.content.Intent.ACTION_VIEW -> intent.dataString?.let(app.wave.music.data.Links::offer)
            android.content.Intent.ACTION_SEND -> intent.getStringExtra(android.content.Intent.EXTRA_TEXT)?.let(app.wave.music.data.Links::offerText)
        }
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
        AndroidPlayer.connect(this)
        // Opened from a YouTube Music link (or one shared to Wave).
        if (savedInstanceState == null) receiveLink(intent)
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
        // Android 13+: the playing notification needs permission.
        if (android.os.Build.VERSION.SDK_INT >= 33 && savedInstanceState == null) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        val preview = BuildConfig.DEBUG && intent.getBooleanExtra("wave_preview", false)
        setContent {
            // Googlebook / ChromeOS (or, in debug builds, `--ez wave_desktop true` to try it anywhere).
            val view = LocalView.current
            WaveApp(
                computer = desktopMode.value,
                preview = preview,
                // Keep the screen awake while music plays and Wave is open (lyrics, the player).
                onPlayingChanged = { view.keepScreenOn = it },
            )
        }
    }
}
