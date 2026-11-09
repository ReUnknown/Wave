package app.wave.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import app.wave.music.ui.components.ArtSize
import app.wave.music.ui.components.BlurredImage
import app.wave.music.ui.components.atSize
import app.wave.music.ui.components.rememberArtColorScheme
import app.wave.music.ui.player.MiniTab
import app.wave.music.ui.player.PopOutPlayer
import app.wave.music.ui.player.PopOutSize
import app.wave.music.ui.player.WaveMiniPanel
import app.wave.music.ui.player.WaveMiniTabs
import app.wave.music.ui.theme.darkBaseScheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import java.awt.MouseInfo
import java.awt.Point

/**
 * WaveMini: the mini player with what you reach for while listening built around it, so the big
 * window can stay closed. Up next, lyrics, search (artists, albums and playlists open right
 * inside it) and the settings you change often, in tabs under the player (Compact) or beside it
 * (Bigger). Click the open tab to fold the panel away and keep just the player. Floats on top,
 * on every virtual desktop; drag it to a corner, or fling it at the screen's edge to tuck it away.
 */
object WaveMini : Floating {
    /** Compact (the player on top) or Bigger (the player beside the panel). */
    val size = MutableStateFlow(PopOutSize.Compact)
    /** The open tab; null = just the player. */
    val tab = MutableStateFlow<MiniTab?>(MiniTab.UpNext)
    override val tucked = MutableStateFlow(false)
    @Volatile override var corner = 3
    @Volatile override var tuckRight = true
    @Volatile override var tuckAt: Point? = null

    private val store get() = Stores.prefs("mini_player")
    private val cornerKey = intPreferencesKey("wavemini_corner")
    private val sizeKey = stringPreferencesKey("wavemini_size")

    suspend fun load() {
        val prefs = store.data.first()
        prefs[cornerKey]?.let { corner = it.coerceIn(0, 3) }
        prefs[sizeKey]?.let { name -> PopOutSize.entries.firstOrNull { it.name == name } }?.let { size.value = it }
    }

    override suspend fun save() {
        store.edit {
            it[cornerKey] = corner
            it[sizeKey] = size.value.name
        }
    }
}

private const val PANEL = 340
private const val TABS = 56
private const val RADIUS = 14f

/** The card's size (window units). */
private fun cardSize(size: PopOutSize, tab: MiniTab?, tucked: Boolean): Pair<Int, Int> = when {
    tucked -> BUBBLE to BUBBLE
    size == PopOutSize.Bigger -> PopOutSize.Bigger.widthDp + 440 to PopOutSize.Bigger.heightDp
    else -> PopOutSize.Compact.widthDp to PopOutSize.Compact.heightDp + TABS + (if (tab != null) PANEL else 0)
}

@Composable
fun WaveMiniWindow(onBack: () -> Unit, onClose: () -> Unit) {
    val size by WaveMini.size.collectAsState()
    val tab by WaveMini.tab.collectAsState()
    val tucked by WaveMini.tucked.collectAsState()
    val start = remember {
        val (w, h) = cardSize(WaveMini.size.value, WaveMini.tab.value, false)
        cornerBounds(MouseInfo.getPointerInfo()?.location ?: Point(), WaveMini.corner, w, h)
    }
    val state = rememberWindowState(size = DpSize(start.width.dp, start.height.dp), position = WindowPosition(start.x.dp, start.y.dp))
    val playing by PlayerConnection.state.collectAsState()
    LaunchedEffect(playing.current == null) { if (playing.current == null) onBack() }

    Window(
        onCloseRequest = onClose,
        state = state,
        title = "WaveMini",
        icon = WaveIcon.painter(),
        undecorated = true,
        transparent = seeThrough,
        resizable = false,
        alwaysOnTop = true,
        onPreviewKeyEvent = { event -> event.type == KeyEventType.KeyDown && !app.wave.music.ui.components.Shortcuts.typing && shortcut(event) },
    ) {
        val scope = rememberCoroutineScope()
        val frame = remember { FloatingFrame(window, start.width, start.height, RADIUS) }
        LaunchedEffect(Unit) {
            Desktops.everywhere(window)
            AppFocus.onStarted(true)
            AppFocus.onFocus(true)
            BeatBus.visible = true
            shape(window, RADIUS.toInt(), false)
        }
        DisposableEffect(window) { onDispose { Desktops.stop(window) } }
        LaunchedEffect(size, tab, tucked) {
            val (w, h) = cardSize(size, tab, tucked)
            val target = if (tucked) tuckedBounds(WaveMini.tuckAt ?: frame.center, WaveMini.tuckRight)
            else cornerBounds(frame.center, WaveMini.corner, w, h)
            frame.go(target, if (tucked) BUBBLE / 2f else RADIUS)
            WaveMini.save()
        }
        val drag = Modifier.movesWindow(window, frame, scope, WaveMini) { frame.go(it, if (WaveMini.tucked.value) BUBBLE / 2f else RADIUS) }
        val onSize: (PopOutSize) -> Unit = { s ->
            when (s) {
                PopOutSize.Bigger -> { WaveMini.size.value = PopOutSize.Bigger; if (WaveMini.tab.value == null) WaveMini.tab.value = MiniTab.UpNext }
                PopOutSize.Compact -> WaveMini.size.value = PopOutSize.Compact
                PopOutSize.Tiny -> { WaveMini.size.value = PopOutSize.Compact; WaveMini.tab.value = null }
            }
        }

        FloatingCard(frame) {
            if (tucked) {
                PopOutPlayer(PopOutSize.Compact, onSize = {}, onBack = onBack, onClose = onClose, modifier = drag, tucked = true, rounded = false)
                return@FloatingCard
            }
            WaveMiniContent(size, tab, playing.current?.artworkUrl, drag, onSize, onBack, onClose)
        }
    }
}

/** WaveMini's card: the player with the tabs and panel under it (Compact) or beside it (Bigger). */
@Composable
internal fun WaveMiniContent(
    size: PopOutSize,
    tab: MiniTab?,
    art: String?,
    drag: Modifier,
    onSize: (PopOutSize) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    MaterialTheme(colorScheme = rememberArtColorScheme(art, darkBaseScheme())) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            // The song's colors behind the panel too.
            BlurredImage(art?.atSize(ArtSize.BLUR), Modifier.fillMaxSize(), radius = 40.dp)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
            if (size == PopOutSize.Bigger) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.width(PopOutSize.Bigger.widthDp.dp).fillMaxHeight()) {
                        PopOutPlayer(PopOutSize.Bigger, onSize, onBack, onClose, modifier = drag, rounded = false)
                    }
                    Column(Modifier.weight(1f).fillMaxHeight().padding(10.dp)) {
                        WaveMiniTabs(tab ?: MiniTab.UpNext, onTab = { WaveMini.tab.value = it ?: MiniTab.UpNext })
                        WaveMiniPanel(tab ?: MiniTab.UpNext, onOpenWave = onBack, modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp))
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(PopOutSize.Compact.heightDp.dp)) {
                        PopOutPlayer(PopOutSize.Compact, onSize, onBack, onClose, modifier = drag, rounded = false)
                    }
                    WaveMiniTabs(tab, onTab = { WaveMini.tab.value = it }, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                    tab?.let { open ->
                        WaveMiniPanel(open, onOpenWave = onBack, modifier = Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        }
    }
}
