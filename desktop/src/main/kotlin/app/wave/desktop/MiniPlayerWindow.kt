package app.wave.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.awt.MouseInfo
import java.awt.Point

/**
 * The mini player that stays on top of everything (Windows and Linux): opened from the player
 * bar, it takes Wave's place and sits in a corner of the screen, on every virtual desktop. Drag
 * it anywhere and it springs to the nearest corner; fling it against the left or right edge of
 * the screen and it tucks away as a round Wave button (click it or pull it out to get it back).
 * It remembers its corner and size.
 */
object MiniPlayer : Floating {
    val open = MutableStateFlow(false)
    val size = MutableStateFlow(PopOutSize.Compact)
    /** Bigger's volume and speed tray. */
    val tray = MutableStateFlow(false)
    override val tucked = MutableStateFlow(false)
    @Volatile override var corner = 3
    @Volatile override var tuckRight = true
    @Volatile override var tuckAt: Point? = null

    private val store get() = Stores.prefs("mini_player")
    private val sizeKey = stringPreferencesKey("size")
    private val cornerKey = intPreferencesKey("corner")

    suspend fun load() {
        val prefs = store.data.first()
        prefs[sizeKey]?.let { name -> PopOutSize.entries.firstOrNull { it.name == name } }?.let { size.value = it }
        prefs[cornerKey]?.let { corner = it.coerceIn(0, 3) }
    }

    override suspend fun save() {
        store.edit {
            it[sizeKey] = size.value.name
            it[cornerKey] = corner
        }
    }
}

/** The card's size for the mini player's state, in window units (dp). */
private fun cardSize(size: PopOutSize, tray: Boolean, tucked: Boolean): Pair<Int, Int> = when {
    tucked -> BUBBLE to BUBBLE
    size == PopOutSize.Bigger && tray -> size.widthDp to size.heightDp + POP_OUT_TRAY_DP
    else -> size.widthDp to size.heightDp
}

/** Rounded, not bubbly; the tucked button is a circle. */
private fun cardRadius(size: PopOutSize, tucked: Boolean): Float = when {
    tucked -> BUBBLE / 2f
    size == PopOutSize.Tiny -> 34f
    else -> 18f
}

@Composable
fun MiniPlayerWindow(onBack: () -> Unit, onClose: () -> Unit) {
    val size by MiniPlayer.size.collectAsState()
    val tray by MiniPlayer.tray.collectAsState()
    val tucked by MiniPlayer.tucked.collectAsState()
    val start = remember {
        val (w, h) = cardSize(MiniPlayer.size.value, MiniPlayer.tray.value, false)
        cornerBounds(MouseInfo.getPointerInfo()?.location ?: Point(), MiniPlayer.corner, w, h)
    }
    val state = rememberWindowState(size = DpSize(start.width.dp, start.height.dp), position = WindowPosition(start.x.dp, start.y.dp))
    // Nothing left to play: back to Wave.
    val playing by PlayerConnection.state.collectAsState()
    LaunchedEffect(playing.current == null) { if (playing.current == null) onBack() }

    Window(
        onCloseRequest = onClose,
        state = state,
        title = "Wave mini player",
        icon = WaveIcon.painter(),
        undecorated = true,
        transparent = seeThrough,
        resizable = false,
        alwaysOnTop = true,
        onPreviewKeyEvent = { event -> event.type == KeyEventType.KeyDown && shortcut(event) },
    ) {
        val scope = rememberCoroutineScope()
        val frame = remember { FloatingFrame(window, start.width, start.height, cardRadius(MiniPlayer.size.value, false)) }
        LaunchedEffect(Unit) {
            // On every virtual desktop: it comes along when you switch.
            Desktops.everywhere(window)
            AppFocus.onStarted(true)
            AppFocus.onFocus(true)
            BeatBus.visible = true
            shape(window, cardRadius(size, tucked).toInt(), tucked)
        }
        DisposableEffect(window) { onDispose { Desktops.stop(window) } }
        // A new size (or the tray, or tucking) morphs the card, keeping its corner.
        LaunchedEffect(size, tray, tucked) {
            val (w, h) = cardSize(size, tray, tucked)
            val target = if (tucked) tuckedBounds(MiniPlayer.tuckAt ?: frame.center, MiniPlayer.tuckRight)
            else cornerBounds(frame.center, MiniPlayer.corner, w, h)
            frame.go(target, cardRadius(size, tucked))
            MiniPlayer.save()
        }
        FloatingCard(frame) {
            PopOutPlayer(
                size = size,
                onSize = { MiniPlayer.size.value = it; if (it != PopOutSize.Bigger) MiniPlayer.tray.value = false },
                onBack = onBack,
                onClose = onClose,
                modifier = Modifier.movesWindow(window, frame, scope, MiniPlayer) { frame.go(it, cardRadius(MiniPlayer.size.value, MiniPlayer.tucked.value)) },
                tray = tray,
                onTray = { MiniPlayer.tray.value = it },
                tucked = tucked,
                rounded = false,
            )
        }
    }
}
