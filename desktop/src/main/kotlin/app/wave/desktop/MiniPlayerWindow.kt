package app.wave.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.wave.music.data.Stores
import app.wave.music.playback.BeatBus
import app.wave.music.playback.PlayerConnection
import app.wave.music.ui.components.AppFocus
import app.wave.music.ui.player.POP_OUT_TRAY_DP
import app.wave.music.ui.player.PopOutPlayer
import app.wave.music.ui.player.PopOutSize
import com.sun.jna.Native
import com.sun.jna.platform.win32.GDI32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.Window as AwtWindow

/**
 * The mini player that stays on top of everything (Windows): opened from the player bar, it
 * takes Wave's place (the main window minimizes) and sits in a corner of the screen. Drag it
 * anywhere and it springs to the nearest corner; fling it against the left or right edge of the
 * screen and it tucks away as a round Wave button (click it or pull it out to get it back).
 * It remembers its corner and size.
 */
object MiniPlayer {
    val open = MutableStateFlow(false)
    val size = MutableStateFlow(PopOutSize.Compact)
    /** Bigger's volume and speed tray. */
    val tray = MutableStateFlow(false)
    /** Tucked against the screen's edge as a round button. */
    val tucked = MutableStateFlow(false)
    /** 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right. */
    @Volatile var corner = 3
    /** Where it was tucked: which edge, and the point it was let go. */
    @Volatile var tuckRight = true
    @Volatile var tuckAt: Point? = null

    private val store get() = Stores.prefs("mini_player")
    private val sizeKey = stringPreferencesKey("size")
    private val cornerKey = intPreferencesKey("corner")

    suspend fun load() {
        val prefs = store.data.first()
        prefs[sizeKey]?.let { name -> PopOutSize.entries.firstOrNull { it.name == name } }?.let { size.value = it }
        prefs[cornerKey]?.let { corner = it.coerceIn(0, 3) }
    }

    suspend fun save() {
        store.edit {
            it[sizeKey] = size.value.name
            it[cornerKey] = corner
        }
    }
}

private const val MARGIN = 16
private const val BUBBLE = 56

/** The window's size for the mini player's current state, in window units (dp). */
private fun targetSize(size: PopOutSize, tray: Boolean, tucked: Boolean): Pair<Int, Int> = when {
    tucked -> BUBBLE to BUBBLE
    size == PopOutSize.Bigger && tray -> size.widthDp to size.heightDp + POP_OUT_TRAY_DP
    else -> size.widthDp to size.heightDp
}

/** The screen containing [point], minus its taskbar. */
private fun usableArea(point: Point): Rectangle {
    val env = GraphicsEnvironment.getLocalGraphicsEnvironment()
    val config = env.screenDevices.map { it.defaultConfiguration }.firstOrNull { it.bounds.contains(point) }
        ?: env.defaultScreenDevice.defaultConfiguration
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
    val b = config.bounds
    return Rectangle(b.x + insets.left, b.y + insets.top, b.width - insets.left - insets.right, b.height - insets.top - insets.bottom)
}

/** Where a window of [w] × [h] sits in [corner] of the screen [near] is on, clear of the taskbar. */
private fun cornerBounds(near: Point, corner: Int, w: Int, h: Int): Rectangle {
    val area = usableArea(near)
    val x = if (corner % 2 == 0) area.x + MARGIN else area.x + area.width - w - MARGIN
    val y = if (corner < 2) area.y + MARGIN else area.y + area.height - h - MARGIN
    return Rectangle(x, y, w, h)
}

/** The round button's spot against the left or right edge, at the height it was let go. */
private fun tuckedBounds(near: Point, right: Boolean): Rectangle {
    val area = usableArea(near)
    val x = if (right) area.x + area.width - BUBBLE - 6 else area.x + 6
    val y = (near.y - BUBBLE / 2).coerceIn(area.y + MARGIN, area.y + area.height - BUBBLE - MARGIN)
    return Rectangle(x, y, BUBBLE, BUBBLE)
}

private fun AwtWindow.center() = Point(x + width / 2, y + height / 2)

/** The corner of its screen nearest [point]. */
private fun nearestCorner(point: Point): Int {
    val area = usableArea(point)
    val right = point.x > area.x + area.width / 2
    val bottom = point.y > area.y + area.height / 2
    return (if (bottom) 2 else 0) + (if (right) 1 else 0)
}

/** Springs the window from its current bounds to [target]: moving and resizing together. */
private suspend fun glide(window: AwtWindow, target: Rectangle) {
    val from = window.bounds
    if (from == target) return
    val t = Animatable(0f)
    t.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) {
        fun lerp(a: Int, b: Int) = a + ((b - a) * value).toInt()
        window.setBounds(lerp(from.x, target.x), lerp(from.y, target.y), lerp(from.width, target.width), lerp(from.height, target.height))
    }
    window.bounds = target
}

/**
 * Windows: the window itself is cut to the mini player's rounded shape (or a circle when
 * tucked). Cheaper than a see-through window, which Windows has to blend on every frame.
 */
private fun shape(window: AwtWindow, radiusDp: Int, circle: Boolean) {
    if (!DesktopPlatform.isWindows) return
    runCatching {
        val scale = window.graphicsConfiguration.defaultTransform.scaleX
        val w = (window.width * scale).toInt()
        val h = (window.height * scale).toInt()
        // A circle is a rounded rectangle whose corners meet.
        val d = if (circle) maxOf(w, h) else (radiusDp * 2 * scale).toInt()
        val region = GDI32.INSTANCE.CreateRoundRectRgn(0, 0, w + 1, h + 1, d, d)
        // The window owns the region from here on (Windows frees it).
        User32.INSTANCE.SetWindowRgn(WinDef.HWND(Native.getComponentPointer(window)), region, true)
    }
}

private fun radiusOf(size: PopOutSize) = when (size) {
    PopOutSize.Tiny -> 34
    PopOutSize.Compact -> 26
    PopOutSize.Bigger -> 30
}

/**
 * Drag anywhere on the mini player to move its window (a drag the progress line or the tray
 * handle takes for itself stays theirs). Let go and it springs to the nearest corner, or, flung
 * at (or dropped against) the left or right edge, tucks away. A click on the tucked button, or
 * pulling it away from the edge, brings the player back.
 */
private fun Modifier.movesWindow(window: AwtWindow, scope: CoroutineScope): Modifier = pointerInput(window) {
    var settle: Job? = null
    val slop = viewConfiguration.touchSlop * 1.5f
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val grab = MouseInfo.getPointerInfo()?.location ?: return@awaitEachGesture
        val origin = window.location
        var dragging = false
        // Recent pointer positions (time, x) to tell a fling from a drop.
        val trail = ArrayDeque<Pair<Long, Int>>()
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            // Something inside (seeking, the tray handle) took this drag.
            if (!dragging && change.isConsumed) return@awaitEachGesture
            if (!dragging && (change.position - down.position).getDistance() > slop) {
                dragging = true
                settle?.cancel()
            }
            if (dragging) {
                // Ours now: the button underneath doesn't also get a click.
                change.consume()
                val now = MouseInfo.getPointerInfo()?.location ?: continue
                window.setLocation(origin.x + now.x - grab.x, origin.y + now.y - grab.y)
                trail.addLast(System.nanoTime() to now.x)
                while (trail.size > 6) trail.removeFirst()
            }
        }
        val tucked = MiniPlayer.tucked.value
        if (!dragging) {
            // A click on the tucked button brings the player back.
            if (tucked) MiniPlayer.tucked.value = false
            return@awaitEachGesture
        }
        val pointer = MouseInfo.getPointerInfo()?.location ?: window.center()
        val area = usableArea(pointer)
        val speed = if (trail.size >= 2) {
            val (t0, x0) = trail.first()
            val (t1, x1) = trail.last()
            (x1 - x0) / ((t1 - t0).coerceAtLeast(1) / 1e9)
        } else 0.0
        val atLeft = pointer.x <= area.x + 24 || window.x < area.x - window.width / 3
        val atRight = pointer.x >= area.x + area.width - 24 || window.x + window.width > area.x + area.width + window.width / 3
        val flungLeft = speed < -2200 && pointer.x < area.x + area.width / 3
        val flungRight = speed > 2200 && pointer.x > area.x + area.width * 2 / 3
        if (atLeft || flungLeft || atRight || flungRight) {
            // Against an edge: tuck away (or move along the edge, already tucked).
            MiniPlayer.tuckRight = atRight || flungRight
            MiniPlayer.tuckAt = pointer
            if (tucked) settle = scope.launch { glide(window, tuckedBounds(pointer, MiniPlayer.tuckRight)) }
            else MiniPlayer.tucked.value = true
        } else {
            MiniPlayer.corner = nearestCorner(pointer)
            if (tucked) {
                // Pulled away from the edge: the player comes back, in that corner.
                MiniPlayer.tucked.value = false
            } else {
                settle = scope.launch {
                    glide(window, cornerBounds(pointer, MiniPlayer.corner, window.width, window.height))
                    MiniPlayer.save()
                }
            }
        }
    }
}

@Composable
fun MiniPlayerWindow(onBack: () -> Unit, onClose: () -> Unit) {
    val size by MiniPlayer.size.collectAsState()
    val tray by MiniPlayer.tray.collectAsState()
    val tucked by MiniPlayer.tucked.collectAsState()
    val start = remember {
        val (w, h) = targetSize(MiniPlayer.size.value, MiniPlayer.tray.value, false)
        val b = cornerBounds(MouseInfo.getPointerInfo()?.location ?: Point(), MiniPlayer.corner, w, h)
        WindowPosition(b.x.dp, b.y.dp) to DpSize(w.dp, h.dp)
    }
    val state = rememberWindowState(size = start.second, position = start.first)
    // Nothing left to play: back to Wave.
    val playing by PlayerConnection.state.collectAsState()
    LaunchedEffect(playing.current == null) { if (playing.current == null) onBack() }

    Window(
        onCloseRequest = onClose,
        state = state,
        title = "Wave mini player",
        icon = WaveIcon.painter(),
        undecorated = true,
        // macOS composites see-through windows for free; on Windows the window is shaped instead.
        transparent = DesktopPlatform.isMac,
        resizable = false,
        alwaysOnTop = true,
        onPreviewKeyEvent = { event -> event.type == KeyEventType.KeyDown && shortcut(event) },
    ) {
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) {
            // Wave's main window is minimized, but the music is still on screen here.
            AppFocus.onStarted(true)
            AppFocus.onFocus(true)
            BeatBus.visible = true
        }
        // Keep the window's shape in step with its size, every frame of a resize.
        androidx.compose.runtime.DisposableEffect(window) {
            val listener = object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent?) = shape(window, radiusOf(MiniPlayer.size.value), MiniPlayer.tucked.value)
            }
            window.addComponentListener(listener)
            shape(window, radiusOf(MiniPlayer.size.value), MiniPlayer.tucked.value)
            onDispose { window.removeComponentListener(listener) }
        }
        // A new size (or the tray, or tucking) reshapes the window smoothly, keeping its corner.
        LaunchedEffect(size, tray, tucked) {
            val (w, h) = targetSize(size, tray, tucked)
            val target = if (tucked) {
                tuckedBounds(MiniPlayer.tuckAt ?: window.center(), MiniPlayer.tuckRight)
            } else {
                cornerBounds(window.center(), MiniPlayer.corner, w, h)
            }
            glide(window, target)
            shape(window, radiusOf(size), tucked)
            MiniPlayer.save()
        }
        PopOutPlayer(
            size = size,
            onSize = { MiniPlayer.size.value = it; if (it != PopOutSize.Bigger) MiniPlayer.tray.value = false },
            onBack = onBack,
            onClose = onClose,
            modifier = Modifier.movesWindow(window, scope),
            tray = tray,
            onTray = { MiniPlayer.tray.value = it },
            tucked = tucked,
        )
    }
}
