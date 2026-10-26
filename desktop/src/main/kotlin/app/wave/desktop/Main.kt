package app.wave.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.wave.music.platform.LocalTitleBarInset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.Alignment
import app.wave.music.data.WaveSettings
import app.wave.music.ui.components.BlurBudget
import kotlinx.coroutines.launch
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.wave.music.data.Links
import app.wave.music.platform.WavePlatform
import app.wave.music.playback.BeatBus
import app.wave.music.playback.PlayerConnection
import app.wave.music.ui.WaveApp
import app.wave.music.ui.components.AppFocus
import app.wave.music.ui.components.Shortcuts
import app.wave.music.ui.login.DesktopLoginHost
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.request.crossfade
import okio.Path.Companion.toPath
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File

fun main(args: Array<String>) {
    WavePlatform.current = DesktopPlatform
    // Big windows: progressive blurs use fewer, wider layers.
    BlurBudget.light = true
    // Motion that runs on its own (the progress wave, lyrics, turning shapes) at 30 fps, not the
    // monitor's 60-144: it looks the same and costs a fraction.
    app.wave.music.ui.components.FrameBudget.ambientFps = System.getProperty("wave.ambientFps")?.toIntOrNull() ?: 30
    // Settings first, so the window opens at your interface size.
    kotlinx.coroutines.runBlocking { WaveSettings.load() }
    kotlinx.coroutines.runBlocking { MiniPlayer.load() }
    PlayerConnection.backend = DesktopPlayer
    DesktopPlayer.restore()
    // Development: start the saved queue right away (for measuring playback without clicking).
    if (System.getProperty("wave.playOnStart") == "true") {
        WaveSettings.preview { it.copy(appVolume = 0f) }
        PlayerConnection.play()
    }
    // A YouTube Music link passed in (opened with Wave).
    args.firstOrNull { it.startsWith("http") }?.let(Links::offer)
    DesktopLoginHost.content = { onDone, onCancel -> DesktopLoginScreen(onDone, onCancel) }
    // Covers are saved for reuse, but never more than 200 MB of them on disk, and only the
    // covers on screen (plus a little) in memory.
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            .crossfade(200)
            .memoryCache { coil3.memory.MemoryCache.Builder().maxSizeBytes(40L * 1024 * 1024).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(AppDirs.cache, "images").absolutePath.toPath())
                    .maxSizeBytes(200L * 1024 * 1024)
                    .build()
            }
            .build()
    }
    val preview = DesktopPlatform.debug && args.contains("--preview")

    application {
        // A comfortable size on a laptop screen, centered.
        val window = rememberWindowState(size = DpSize(1120.dp, 740.dp))
        var mainWindow by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<java.awt.Window?>(null) }
        // Sized and centered once; reopened after the mini player, it comes back where it was.
        var placed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        // Room Wave leaves at the top for the system's window buttons (window units).
        var titleBarInset by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(if (DesktopPlatform.isMac) 28 else 0) }
        val miniOpen by MiniPlayer.open.collectAsState()
        // The mini player takes Wave's place. The main window isn't just minimized: it's closed,
        // and everything it drew is let go, so the mini player is all that's running. Back to
        // Wave (or closing the mini player) opens it again, where it was.
        if (miniOpen) {
            MiniPlayerWindow(
                onBack = { MiniPlayer.open.value = false },
                onClose = { MiniPlayer.open.value = false },
            )
            LaunchedEffect(Unit) { MemoryTrim.soon() }
            return@application
        }
        Window(
            // Closing Wave quits it completely: nothing keeps running in the background.
            onCloseRequest = ::quit,
            title = "Wave",
            state = window,
            icon = WaveIcon.painter(),
            onPreviewKeyEvent = { event -> event.type == KeyEventType.KeyDown && (zoomKey(event) || shortcut(event)) },
        ) {
            LaunchedEffect(Unit) {
                mainWindow = this@Window.window
                this@Window.window.minimumSize = java.awt.Dimension(420, 560)
                // Open at a comfortable size for the screen it's on (never bigger than 85% of it), centered.
                if (!placed) {
                    placed = true
                    val screen = this@Window.window.graphicsConfiguration.bounds
                    this@Window.window.setSize(minOf(1120, (screen.width * 0.85).toInt()), minOf(740, (screen.height * 0.85).toInt()))
                    this@Window.window.setLocationRelativeTo(null)
                }
                if (DesktopPlatform.isMac) {
                    // Wave's own colors run all the way to the top; the window buttons float over them.
                    this@Window.window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                    this@Window.window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                    this@Window.window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
                }
                if (DesktopPlatform.isWindows) {
                    if (WindowsTitleBar.extendIntoTitleBar(this@Window.window, dark = true)) titleBarInset = WindowsTitleBar.HEIGHT
                    else WindowsTitleBar.apply(this@Window.window, 0xFF111418.toInt())
                }
                // Decorative motion only while Wave is in front: on screen and focused.
                AppFocus.onStarted(true)
                BeatBus.visible = true
                // (While the mini player is open, it's the one on screen.)
                // Minimized (or in the background for a minute): memory goes back to the system.
                this@Window.window.addWindowListener(object : WindowAdapter() {
                    override fun windowIconified(e: WindowEvent?) {
                        AppFocus.onStarted(false)
                        BeatBus.visible = false
                        MemoryTrim.minimized()
                    }
                    override fun windowDeiconified(e: WindowEvent?) {
                        MemoryTrim.restored()
                        AppFocus.onStarted(true)
                        BeatBus.visible = true
                    }
                    override fun windowActivated(e: WindowEvent?) {
                        AppFocus.onFocus(true)
                        MemoryTrim.cancel()
                    }
                    override fun windowDeactivated(e: WindowEvent?) {
                        AppFocus.onFocus(false)
                        MemoryTrim.later()
                    }
                })
            }
            // Interface size: ⌘/Ctrl + and − zoom like a browser (remembered).
            val zoom = WaveSettings.state.collectAsState().value.uiScale
            val base = LocalDensity.current
            val dormant by MemoryTrim.dormant.collectAsState()
            if (dormant) {
                // Minimized for a while: nothing drawn, nothing held. Brought back, it's rebuilt.
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF111418)),
                )
                return@Window
            }
            CompositionLocalProvider(
                LocalDensity provides Density(base.density * zoom, base.fontScale),
                // In window units, so it stays the buttons' height at any interface size.
                LocalTitleBarInset provides (titleBarInset / zoom).dp,
            ) {
                WaveApp(
                    computer = true,
                    preview = preview,
                    onWindowColor = { argb, dark -> if (DesktopPlatform.isWindows) WindowsTitleBar.apply(this@Window.window, argb, dark) },
                )
            }
            if (DesktopPlatform.debug) FrameStats()
        }
    }
}

/** Saves your place, stops the music and ends the process (audio and network threads included). */
internal fun quit() {
    runCatching { DesktopPlayer.shutdown() }
    // A stuck shutdown never leaves Wave running: after 3 s it's ended outright.
    Thread { Thread.sleep(3000); Runtime.getRuntime().halt(0) }.apply { isDaemon = true }.start()
    kotlin.system.exitProcess(0)
}

/**
 * Space plays/pauses, Ctrl/⌘ + →/← skip, Ctrl/⌘ + L likes, Ctrl/⌘ + F or / search (Esc goes
 * back, handled by the screens). Ignored while typing.
 */
internal fun shortcut(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
    if (Shortcuts.typing) return false
    val command = event.isCtrlPressed || event.isMetaPressed
    when {
        event.key == Key.Spacebar && !command -> PlayerConnection.togglePlay()
        command && event.key == Key.DirectionRight -> PlayerConnection.next()
        command && event.key == Key.DirectionLeft -> PlayerConnection.previous()
        command && event.key == Key.L -> PlayerConnection.toggleLike()
        (command && event.key == Key.F) || event.key == Key.Slash -> Shortcuts.requestSearch()
        else -> return false
    }
    return true
}

/** ⌘/Ctrl + = bigger, − smaller, 0 back to the default: steps through the Interface size setting. */
private fun zoomKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
    if (!(event.isCtrlPressed || event.isMetaPressed)) return false
    val steps = listOf(0.8f, 0.9f, 1f, 1.1f, 1.25f, 1.5f)
    val now = WaveSettings.state.value.uiScale
    val at = steps.indexOfFirst { it >= now - 0.001f }.let { if (it < 0) steps.lastIndex else it }
    val next = when (event.key) {
        Key.Equals, Key.Plus, Key.NumPadAdd -> steps[(at + 1).coerceAtMost(steps.lastIndex)]
        Key.Minus, Key.NumPadSubtract -> steps[(at - 1).coerceAtLeast(0)]
        Key.Zero, Key.NumPad0 -> 0.9f
        else -> return false
    }
    scope.launch { WaveSettings.update { it.copy(uiScale = next) } }
    return true
}

private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
/** Development builds: logs frame times every two seconds while anything is animating. */
@androidx.compose.runtime.Composable
private fun FrameStats() {
    LaunchedEffect(Unit) {
        var last = 0L
        var count = 0
        var slow = 0
        var worst = 0L
        var windowStart = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last != 0L) {
                    val ms = (now - last) / 1_000_000
                    count++
                    if (ms > 20) slow++
                    worst = maxOf(worst, ms)
                }
                if (windowStart == 0L) windowStart = now
                if (now - windowStart > 2_000_000_000L) {
                    if (count > 0) println("frames: $count in 2s, slow(>20ms) $slow, worst ${worst}ms")
                    count = 0; slow = 0; worst = 0; windowStart = now
                }
                last = now
            }
        }
    }
}
