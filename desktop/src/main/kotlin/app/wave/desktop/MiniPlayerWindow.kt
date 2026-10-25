package app.wave.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
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
import app.wave.music.ui.player.PopOutPlayer
import app.wave.music.ui.player.PopOutSize
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
import java.awt.Window as AwtWindow

/**
 * The mini player that stays on top of everything (Windows): opened from the player bar, it
 * takes Wave's place (the main window minimizes) and sits in a corner of the screen. Drag it
 * anywhere and it springs to the nearest corner; it remembers its corner and size.
 */
object MiniPlayer {
    val open = MutableStateFlow(false)
    val size = MutableStateFlow(PopOutSize.Compact)
    /** 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right. */
    @Volatile var corner = 3

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

/** Where a window of [w] × [h] sits in [corner] of the screen [near] is on, clear of the taskbar. */
private fun cornerPoint(near: Point, corner: Int, w: Int, h: Int): Point {
    val area = usableArea(near)
    val x = if (corner % 2 == 0) area.x + MARGIN else area.x + area.width - w - MARGIN
    val y = if (corner < 2) area.y + MARGIN else area.y + area.height - h - MARGIN
    return Point(x, y)
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

private fun AwtWindow.center() = Point(x + width / 2, y + height / 2)

/** The corner of its screen nearest the window. */
private fun nearestCorner(window: AwtWindow): Int {
    val c = window.center()
    val area = usableArea(c)
    val right = c.x > area.x + area.width / 2
    val bottom = c.y > area.y + area.height / 2
    return (if (bottom) 2 else 0) + (if (right) 1 else 0)
}

/** Springs the window from where it is to [target]. */
private suspend fun glide(window: AwtWindow, target: Point) {
    val from = window.location
    val t = Animatable(0f)
    t.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)) {
        window.setLocation(from.x + ((target.x - from.x) * value).toInt(), from.y + ((target.y - from.y) * value).toInt())
    }
}

/** Drag anywhere on the mini player to move its window; let go and it springs to a corner. */
private fun Modifier.movesWindow(window: AwtWindow, scope: CoroutineScope): Modifier = pointerInput(window) {
    var grab = Point()
    var origin = Point()
    var settle: Job? = null
    detectDragGestures(
        onDragStart = {
            settle?.cancel()
            grab = MouseInfo.getPointerInfo()?.location ?: Point()
            origin = window.location
        },
        onDragEnd = {
            MiniPlayer.corner = nearestCorner(window)
            settle = scope.launch {
                glide(window, cornerPoint(window.center(), MiniPlayer.corner, window.width, window.height))
                MiniPlayer.save()
            }
        },
    ) { change, _ ->
        change.consume()
        val now = MouseInfo.getPointerInfo()?.location ?: return@detectDragGestures
        window.setLocation(origin.x + now.x - grab.x, origin.y + now.y - grab.y)
    }
}

@Composable
fun MiniPlayerWindow(onBack: () -> Unit, onClose: () -> Unit) {
    val size by MiniPlayer.size.collectAsState()
    val start = remember {
        val s = MiniPlayer.size.value
        val p = cornerPoint(MouseInfo.getPointerInfo()?.location ?: Point(), MiniPlayer.corner, s.widthDp, s.heightDp)
        WindowPosition(p.x.dp, p.y.dp)
    }
    val state = rememberWindowState(size = DpSize(size.widthDp.dp, size.heightDp.dp), position = start)
    // Nothing left to play: back to Wave.
    val playing by PlayerConnection.state.collectAsState()
    LaunchedEffect(playing.current == null) { if (playing.current == null) onBack() }

    Window(
        onCloseRequest = onClose,
        state = state,
        title = "Wave mini player",
        icon = WaveIcon.painter(),
        undecorated = true,
        transparent = true,
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
        // A new size keeps its corner.
        LaunchedEffect(size) {
            state.size = DpSize(size.widthDp.dp, size.heightDp.dp)
            window.setSize(size.widthDp, size.heightDp)
            window.location = cornerPoint(window.center(), MiniPlayer.corner, size.widthDp, size.heightDp)
            MiniPlayer.save()
        }
        PopOutPlayer(
            size = size,
            onSize = { MiniPlayer.size.value = it },
            onBack = onBack,
            onClose = onClose,
            modifier = Modifier.movesWindow(window, scope),
        )
    }
}
