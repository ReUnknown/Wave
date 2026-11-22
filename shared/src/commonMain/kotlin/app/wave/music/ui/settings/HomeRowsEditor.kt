package app.wave.music.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wave.music.data.HomeRepository
import app.wave.music.data.HomeRows
import app.wave.music.data.WaveSettings
import app.wave.music.platform.prismaDisplayFont
import app.wave.music.platform.prismaTextFont
import app.wave.music.platform.topInset
import app.wave.music.ui.components.LocalMiniPlayerSpace
import app.wave.music.ui.components.blocksTouchesBelow
import app.wave.music.ui.components.pressable
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private fun display(size: TextUnit, weight: FontWeight = FontWeight.Bold) = TextStyle(fontFamily = prismaDisplayFont, fontSize = size, fontWeight = weight)
private fun body(size: TextUnit, weight: FontWeight = FontWeight.Normal) = TextStyle(fontFamily = prismaTextFont, fontSize = size, fontWeight = weight)

private val about = mapOf(
    "picks" to "Big cards picked from what you play",
    "recent" to "What you played lately",
    "playlists" to "Playlists you made or saved",
    "feed" to "Mixes and rows YouTube Music makes for you",
    "radio" to "Live stations like the music you play",
)

/**
 * Settings › Customize › Home rows. A little picture of your home at the top that changes as you
 * do; under it, each row as a card with a sketch of what it looks like. Drag a card by its handle
 * to move it, flip its switch to hide it, and tap YouTube Music's own rows off and on.
 */
@Composable
internal fun HomeRowsEditor(onBack: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val settings by WaveSettings.state.collectAsState()
    // Edited here and saved as you go (a drag saves when you let go).
    var rows by remember { mutableStateOf(HomeRows.parse(settings.homeRows)) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(settings.homeRows) { if (!dragging) rows = HomeRows.parse(settings.homeRows) }
    val hidden = settings.hiddenSections.split("\n").filter { it.isNotBlank() }.toSet()
    val ytRows = remember { HomeRepository.lastSectionTitles }
    fun save(next: List<Pair<String, Boolean>>) {
        rows = next
        scope.launch { WaveSettings.update { it.copy(homeRows = HomeRows.write(next)) } }
    }
    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val a = rows.indexOfFirst { "row-" + it.first == from.key }
        val b = rows.indexOfFirst { "row-" + it.first == to.key }
        if (a >= 0 && b >= 0) {
            rows = rows.toMutableList().apply { add(b, removeAt(a)) }
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }
    val top = topInset()

    Box(Modifier.fillMaxSize().blocksTouchesBelow().background(c.background)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 760.dp
            Row(Modifier.fillMaxSize()) {
                // Wide screens: the preview stays put on the left while you edit on the right.
                if (wide) Box(Modifier.width(320.dp).fillMaxSize().padding(top = top + 72.dp, start = 24.dp), contentAlignment = Alignment.TopCenter) {
                    HomePreview(rows, hidden, ytRows)
                }
                LazyColumn(
                    Modifier.weight(1f).fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(top = top + 8.dp, bottom = 40.dp + LocalMiniPlayerSpace.current),
                ) {
                    item(key = "top") {
                        Column {
                            Box(
                                Modifier.padding(start = 12.dp).size(48.dp).pressable(CircleShape, c.onBackground.copy(alpha = 0.08f), pressedScale = 0.88f, onClick = onBack),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = c.onBackground) }
                            Text("Your home", Modifier.padding(start = 20.dp, top = 14.dp), color = c.onBackground, style = display(34.sp).copy(letterSpacing = (-0.6).sp))
                            Text(
                                "Drag rows to move them, switch them off to hide them. The preview shows how it'll look.",
                                Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
                                color = c.onBackground.copy(alpha = 0.65f), style = body(14.sp).copy(lineHeight = 19.sp),
                            )
                            if (!wide) Box(Modifier.fillMaxWidth().padding(bottom = 18.dp), contentAlignment = Alignment.Center) { HomePreview(rows, hidden, ytRows) }
                            Label("Rows")
                        }
                    }
                    items(rows, key = { "row-" + it.first }) { (key, on) ->
                        ReorderableItem(reorder, key = "row-$key", modifier = Modifier.animateItem()) { isDragging ->
                            val lift by animateFloatAsState(if (isDragging) 1f else 0f, app.wave.music.ui.components.WaveMotion.bouncy(), label = "lift")
                            val shape = RoundedCornerShape(app.wave.music.platform.PrismaLook.corner(24.dp))
                            Row(
                                Modifier
                                    .padding(horizontal = 16.dp, vertical = 5.dp)
                                    .fillMaxWidth()
                                    .graphicsLayer { scaleX = 1f + 0.03f * lift; scaleY = 1f + 0.03f * lift }
                                    .shadow(14.dp * lift, shape)
                                    .clip(shape)
                                    .background(if (on) c.surfaceContainerHigh else c.surfaceContainerLow)
                                    .clickable { save(rows.map { if (it.first == key) key to !on else it }) }
                                    .padding(start = 6.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    Modifier.size(width = 30.dp, height = 48.dp).draggableHandle(
                                        onDragStarted = { dragging = true; haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                                        onDragStopped = { dragging = false; save(rows); haptics.performHapticFeedback(HapticFeedbackType.GestureEnd) },
                                    ),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(Icons.Rounded.DragIndicator, "Drag to move ${HomeRows.names[key]}", tint = c.onSurfaceVariant) }
                                // A sketch of the row itself.
                                Box(
                                    Modifier
                                        .size(width = 92.dp, height = 60.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(c.background.copy(alpha = 0.6f))
                                        .graphicsLayer { alpha = if (on) 1f else 0.35f }
                                        .padding(7.dp),
                                ) { RowSketch(key, Modifier.fillMaxSize()) }
                                Column(Modifier.weight(1f).graphicsLayer { alpha = if (on) 1f else 0.55f }) {
                                    Text(HomeRows.names[key].orEmpty(), color = c.onSurface, style = display(16.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (on) about[key].orEmpty() else "Hidden from your home", color = c.onSurfaceVariant, style = body(12.5.sp).copy(lineHeight = 16.sp), maxLines = 2)
                                }
                                Switch(checked = on, onCheckedChange = { v -> save(rows.map { if (it.first == key) key to v else it }) })
                            }
                        }
                    }
                    if (ytRows.isNotEmpty() && rows.any { it.first == "feed" && it.second }) {
                        item(key = "yt") {
                            Column {
                                Label("From YouTube Music")
                                Text("Tap a row to hide it, tap again to bring it back.", Modifier.padding(horizontal = 24.dp).padding(bottom = 10.dp), color = c.onBackground.copy(alpha = 0.6f), style = body(13.sp))
                                YouTubeRowChips(ytRows, hidden) { title ->
                                    val next = if (title in hidden) hidden - title else hidden + title
                                    scope.launch { WaveSettings.update { it.copy(hiddenSections = next.joinToString("\n")) } }
                                }
                            }
                        }
                    }
                    item(key = "reset") {
                        Row(
                            Modifier.padding(start = 16.dp, top = 22.dp).height(46.dp).pressable(CircleShape, c.onBackground.copy(alpha = 0.08f), pressedScale = 0.95f) {
                                scope.launch { WaveSettings.update { it.copy(homeRows = HomeRows.DEFAULT, hiddenSections = "") } }
                            }.padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Rounded.Restore, null, tint = c.onBackground, modifier = Modifier.size(20.dp))
                            Text("Reset to how Wave starts", color = c.onBackground, style = body(14.sp, FontWeight.SemiBold))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(),
        Modifier.padding(start = 24.dp, end = 20.dp, top = 14.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        style = body(12.sp, FontWeight.Bold).copy(letterSpacing = 0.8.sp),
    )
}

/** YouTube Music's rows as chips: on ones are filled with a check, hidden ones are faded and struck. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YouTubeRowChips(titles: List<String>, hidden: Set<String>, onToggle: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        titles.forEach { title ->
            val on = title !in hidden
            Row(
                Modifier
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(if (on) c.secondaryContainer else Color.Transparent)
                    .border(1.dp, if (on) Color.Transparent else c.outlineVariant, CircleShape)
                    .clickable { onToggle(title) }
                    .padding(start = 12.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(if (on) Icons.Rounded.Check else Icons.Rounded.VisibilityOff, null, tint = if (on) c.onSecondaryContainer else c.onSurfaceVariant, modifier = Modifier.size(17.dp))
                Text(title, color = if (on) c.onSecondaryContainer else c.onSurfaceVariant, style = body(14.sp, FontWeight.Medium), maxLines = 1)
            }
        }
    }
}

/**
 * Your home in miniature: the top bar, then each row that's on, in order, drawn the way it looks.
 * It grows and shrinks as rows come and go.
 */
@Composable
private fun HomePreview(rows: List<Pair<String, Boolean>>, hidden: Set<String>, ytRows: List<String>) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(30.dp)
    Column(
        Modifier
            .width(210.dp)
            .shadow(20.dp, shape)
            .clip(shape)
            .background(c.surfaceContainerLowest)
            .border(3.dp, c.outlineVariant.copy(alpha = 0.6f), shape)
            .padding(horizontal = 12.dp, vertical = 14.dp)
            .animateContentSize(tween(260)),
    ) {
        // The top bar: Wave's mark and your picture.
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 22.dp, height = 8.dp).clip(CircleShape).background(c.onSurface.copy(alpha = 0.7f)))
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(14.dp).clip(CircleShape).background(c.primary.copy(alpha = 0.7f)))
        }
        val shownFeed = ytRows.count { it !in hidden }.coerceAtLeast(if (ytRows.isEmpty()) 3 else 0).coerceAtMost(3)
        val order = rows.map { it.first }
        val radioAfterFeed = order.indexOf("radio").let { it > 0 && order[it - 1] == "feed" }
        rows.forEach { (key, on) ->
            key(key) {
                AnimatedVisibility(on, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Column {
                        if (key == "feed") {
                            repeat(shownFeed) { i ->
                                PreviewRow("feed")
                                if (radioAfterFeed && i == 2 && rows.any { it.first == "radio" && it.second }) PreviewRow("radio")
                            }
                        } else if (!(key == "radio" && radioAfterFeed && shownFeed >= 3)) PreviewRow(key)
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewRow(key: String) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 8.dp)) {
        Box(Modifier.padding(bottom = 4.dp).size(width = if (key == "picks") 64.dp else 48.dp, height = 6.dp).clip(CircleShape).background(c.onSurface.copy(alpha = 0.55f)))
        RowSketch(key, Modifier.fillMaxWidth().height(if (key == "picks") 52.dp else if (key == "feed") 34.dp else 30.dp))
    }
}

/** What a row looks like, drawn simply: tall cards, squares, song lines or live stations. */
@Composable
private fun RowSketch(key: String, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    val primary = c.primary
    val tertiary = c.tertiary
    val soft = c.onSurface.copy(alpha = 0.16f)
    val line = c.onSurface.copy(alpha = 0.3f)
    val live = c.error
    Canvas(modifier) {
        val h = size.height
        val w = size.width
        val r = CornerRadius(h * 0.14f)
        fun DrawScope.card(x: Float, cw: Float, ch: Float, brush: Brush) = drawRoundRect(brush, Offset(x, 0f), Size(cw, ch), r)
        when (key) {
            "picks" -> {
                val cw = w * 0.56f
                card(0f, cw, h, Brush.verticalGradient(listOf(primary.copy(alpha = 0.9f), tertiary.copy(alpha = 0.7f))))
                card(cw + w * 0.05f, cw, h, Brush.verticalGradient(listOf(tertiary.copy(alpha = 0.6f), primary.copy(alpha = 0.4f))))
                drawRoundRect(Color.White.copy(alpha = 0.85f), Offset(w * 0.05f, h * 0.72f), Size(cw * 0.5f, h * 0.08f), CornerRadius(h))
            }
            "recent", "playlists" -> {
                val s = h * if (key == "playlists") 0.72f else 1f
                var x = 0f
                var i = 0
                while (x < w) {
                    val col = if (key == "recent") listOf(primary, tertiary, soft)[i % 3] else soft
                    drawRoundRect(col.copy(alpha = if (col == soft) col.alpha else 0.75f), Offset(x, 0f), Size(s, s), r)
                    if (key == "playlists") drawRoundRect(line, Offset(x, s + h * 0.1f), Size(s * 0.7f, h * 0.1f), CornerRadius(h))
                    x += s + w * 0.05f
                    i++
                }
            }
            "feed" -> {
                // Song rows: a little cover and two lines each, in two columns.
                val rowH = h / 3f
                for (col in 0..1) for (row in 0..2) {
                    val x = col * w * 0.56f
                    val y = row * rowH
                    drawRoundRect(if ((row + col) % 2 == 0) primary.copy(alpha = 0.6f) else soft, Offset(x, y + rowH * 0.1f), Size(rowH * 0.8f, rowH * 0.8f), CornerRadius(rowH * 0.2f))
                    drawRoundRect(line, Offset(x + rowH, y + rowH * 0.2f), Size(w * 0.3f, rowH * 0.22f), CornerRadius(h))
                    drawRoundRect(line.copy(alpha = 0.18f), Offset(x + rowH, y + rowH * 0.55f), Size(w * 0.2f, rowH * 0.18f), CornerRadius(h))
                }
            }
            "radio" -> {
                val s = h
                var x = 0f
                var i = 0
                while (x < w) {
                    drawRoundRect(listOf(tertiary, primary, soft)[i % 3].copy(alpha = 0.7f), Offset(x, 0f), Size(s, s), r)
                    // The little red LIVE tag in the corner.
                    drawRoundRect(live, Offset(x + s * 0.1f, s * 0.1f), Size(s * 0.42f, s * 0.18f), CornerRadius(s))
                    x += s + w * 0.05f
                    i++
                }
            }
        }
    }
}
