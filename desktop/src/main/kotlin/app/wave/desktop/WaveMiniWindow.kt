package app.wave.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowRight
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import app.wave.music.data.Stores
import app.wave.music.data.WaveSettings
import app.wave.music.platform.LocalTitleBarInset
import app.wave.music.playback.BeatBus
import app.wave.music.ui.WaveApp
import app.wave.music.ui.components.AppFocus
import app.wave.music.ui.components.pressable
import app.wave.music.ui.player.PopOutPlayer
import app.wave.music.ui.player.PopOutSize
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.awt.MouseInfo
import java.awt.Point
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

/**
 * WaveMini: the mini player as a whole Wave the size of a phone, on top of everything and on
 * every virtual desktop. It's Wave's phone layout (home, search, pages, the player, lyrics, up
 * next), with computer niceties (menus where you click, shortcuts, the mouse wheel). Drag it by
 * its top edge; it springs to the nearest corner, and flung against the side of the screen it
 * tucks away as a round button, just as you left it inside.
 */
object WaveMini : Floating {
    override val tucked = MutableStateFlow(false)
    @Volatile override var corner = 3
    @Volatile override var tuckRight = true
    @Volatile override var tuckAt: Point? = null

    private val store get() = Stores.prefs("mini_player")
    private val cornerKey = intPreferencesKey("wavemini_corner")

    suspend fun load() {
        store.data.first()[cornerKey]?.let { corner = it.coerceIn(0, 3) }
    }

    override suspend fun save() {
        store.edit { it[cornerKey] = corner }
    }
}

/** The strip along WaveMini's top that drags it (window units). */
private const val GRIP = 30
private const val RADIUS = 28

@Composable
fun WaveMiniWindow(onBack: () -> Unit, onClose: () -> Unit) {
    val tucked by WaveMini.tucked.collectAsState()
    // Phone-sized: 390 wide and as tall as fits (up to 820).
    val full = remember {
        val area = usableArea(MouseInfo.getPointerInfo()?.location ?: Point())
        390 to minOf(820, (area.height * 0.9).toInt())
    }
    val start = remember {
        val b = cornerBounds(MouseInfo.getPointerInfo()?.location ?: Point(), WaveMini.corner, full.first, full.second)
        WindowPosition(b.x.dp, b.y.dp)
    }
    val state = rememberWindowState(size = DpSize(full.first.dp, full.second.dp), position = start)

    Window(
        onCloseRequest = onClose,
        state = state,
        title = "WaveMini",
        icon = WaveIcon.painter(),
        undecorated = true,
        transparent = DesktopPlatform.isMac,
        resizable = false,
        alwaysOnTop = true,
        onPreviewKeyEvent = { event -> event.type == KeyEventType.KeyDown && shortcut(event) },
    ) {
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) {
            Desktops.everywhere(window)
            AppFocus.onStarted(true)
            AppFocus.onFocus(true)
            BeatBus.visible = true
        }
        DisposableEffect(window) {
            val listener = object : ComponentAdapter() {
                override fun componentResized(e: ComponentEvent?) = shape(window, RADIUS, WaveMini.tucked.value)
            }
            window.addComponentListener(listener)
            shape(window, RADIUS, WaveMini.tucked.value)
            onDispose {
                window.removeComponentListener(listener)
                Desktops.stop(window)
            }
        }
        // Tucking and coming back reshape the window smoothly.
        LaunchedEffect(tucked) {
            val target = if (tucked) {
                tuckedBounds(WaveMini.tuckAt ?: window.center(), WaveMini.tuckRight)
            } else {
                cornerBounds(window.center(), WaveMini.corner, full.first, full.second)
            }
            glide(window, target)
            shape(window, RADIUS, tucked)
            WaveMini.save()
        }

        val zoom = WaveSettings.state.collectAsState().value.uiScale
        val base = LocalDensity.current
        val fullPx = with(base) { full.first.dp.roundToPx() to full.second.dp.roundToPx() }
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(if (tucked) 50 else RADIUS)).background(Color(0xFF111418))) {
            // The app stays alive while tucked (so it's just as you left it) but isn't drawn.
            KeepAlive(visible = !tucked, width = fullPx.first, height = fullPx.second) {
                CompositionLocalProvider(
                    LocalDensity provides Density(base.density * zoom, base.fontScale),
                    LocalTitleBarInset provides (GRIP / zoom).dp,
                ) {
                    WaveApp(computer = true)
                }
            }
            if (tucked) {
                PopOutPlayer(
                    size = PopOutSize.Compact,
                    onSize = {},
                    onBack = onBack,
                    onClose = onClose,
                    modifier = Modifier.movesWindow(window, scope, WaveMini),
                    tucked = true,
                )
            } else {
                Grip(
                    modifier = Modifier.movesWindow(window, scope, WaveMini),
                    onTuck = {
                        val area = usableArea(window.center())
                        WaveMini.tuckRight = window.center().x > area.x + area.width / 2
                        WaveMini.tuckAt = window.center()
                        WaveMini.tucked.value = true
                    },
                    onBack = onBack,
                    onClose = onClose,
                )
            }
        }
    }
}

/** The top edge: drag it to move WaveMini; tuck it away, back to the full Wave, or close. */
@Composable
private fun Grip(modifier: Modifier, onTuck: () -> Unit, onBack: () -> Unit, onClose: () -> Unit) {
    Box(modifier.fillMaxWidth().height(GRIP.dp)) {
        Box(
            Modifier
                .align(Alignment.Center)
                .size(width = 40.dp, height = 4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.4f)),
        )
        Row(
            Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            GripButton(Icons.Rounded.KeyboardDoubleArrowRight, "Tuck away", onTuck)
            GripButton(Icons.Rounded.OpenInNew, "Back to the full Wave", onBack)
            GripButton(Icons.Rounded.Close, "Close WaveMini", onClose)
        }
    }
}

@Composable
private fun GripButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(22.dp)
            .pressable(CircleShape, Color.Black.copy(alpha = 0.35f), pressedScale = 0.86f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(14.dp)) }
}

/** Lays [content] out at a fixed size; when not [visible] it stays composed but isn't placed (or drawn). */
@Composable
private fun KeepAlive(visible: Boolean, width: Int, height: Int, content: @Composable () -> Unit) {
    Layout(content) { measurables, _ ->
        val placeables = measurables.map { it.measure(Constraints.fixed(width, height)) }
        layout(if (visible) width else 0, if (visible) height else 0) {
            if (visible) placeables.forEach { it.place(0, 0) }
        }
    }
}
