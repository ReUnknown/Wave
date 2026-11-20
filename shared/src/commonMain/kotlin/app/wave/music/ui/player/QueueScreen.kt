package app.wave.music.ui.player

import app.wave.music.playback.RepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import app.wave.music.platform.topBarPadding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.layout.widthIn
import app.wave.music.ui.components.ArtSize
import app.wave.music.ui.components.atSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wave.music.playback.NowPlaying
import app.wave.music.playback.PlayerConnection
import app.wave.music.playback.PlayerUiState
import app.wave.music.ui.components.BlurredImage
import app.wave.music.ui.components.backGesture
import app.wave.music.ui.components.pressable
import app.wave.music.ui.components.WaveMotion
import androidx.compose.animation.core.animateFloatAsState
import app.wave.music.ui.components.predictiveBack
import app.wave.music.ui.components.blocksTouchesBelow
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.abs

/** A queue entry with a key that survives reordering (song id + which occurrence of it). */
private data class QueueEntry(val index: Int, val song: NowPlaying, val key: String)

private fun entriesFor(indices: List<Int>, queue: List<NowPlaying>): List<QueueEntry> {
    val seen = HashMap<String, Int>()
    // Occurrence numbers come from queue position so they don't change when the order does.
    val occurrence = queue.indices.associateWith { i -> queue[i].videoId.let { id -> seen.merge(id, 1, Int::plus)!! } }
    return indices.mapNotNull { i -> queue.getOrNull(i)?.let { QueueEntry(i, it, "${it.videoId}#${occurrence[i]}") } }
}

/**
 * Up next. The coverflow is the whole queue in play order: the playing song in the middle,
 * what already played to the left, what's coming to the right. Settling on a cover plays it.
 * Below, the songs coming up, drag to reorder.
 */
@Composable
fun QueueScreen(
    state: PlayerUiState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** Shown inside the player on wide screens: no backdrop or close button of its own. */
    embedded: Boolean = false,
) {
    val back = predictiveBack(enabled = !embedded, onBack = onClose)
    val current = state.current ?: return
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // Coming-up list: a local copy so drags are smooth; the player is only told when you let go.
    var dragging by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf(entriesFor(state.upcoming, state.queue)) }
    LaunchedEffect(state.upcoming, state.queue) {
        if (!dragging) entries = entriesFor(state.upcoming, state.queue)
    }
    val listState = rememberLazyListState()
    val headerItems = 2
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val a = from.index - headerItems
        val b = to.index - headerItems
        if (a in entries.indices && b in entries.indices) {
            entries = entries.toMutableList().apply { add(b, removeAt(a)) }
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }
    fun commitMove(key: String) {
        val entry = entries.firstOrNull { it.key == key } ?: return
        val newSlot = entries.indexOf(entry)
        // Unshuffled, songs after the current one are consecutive queue positions.
        val target = state.index + 1 + newSlot
        if (target != entry.index) PlayerConnection.move(entry.index, target)
    }

    Box(
        modifier
            .backGesture(back)
            .fillMaxSize()
            .then(if (embedded) Modifier else Modifier.blocksTouchesBelow().background(c.background)),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (!embedded) BlurredImage(
            model = current.artworkUrl?.atSize(ArtSize.BLUR),
            radius = 80.dp,
            fadeBottom = 0.4f,
            modifier = Modifier
                .fillMaxWidth()
                .height(600.dp)
                .graphicsLayer { scaleX = 1.3f; scaleY = 1.3f },
        )
        if (!embedded) Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to c.background.copy(alpha = 0.25f), 0.45f to c.background.copy(alpha = 0.7f), 0.75f to c.background)),
        )

        LazyColumn(
            state = listState,
            // A readable width on big screens.
            modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Column(if (embedded) Modifier else Modifier.topBarPadding()) {
                    Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Up next", color = c.onBackground, style = androidx.compose.ui.text.TextStyle(fontFamily = app.wave.music.platform.prismaDisplayFont, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp))
                            state.context?.title?.let {
                                Text(it, color = c.onBackground.copy(alpha = 0.6f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (!embedded) Box(
                            Modifier
                                .size(48.dp)
                                .pressable(CircleShape, Color.Black.copy(alpha = 0.32f), pressedScale = 0.86f, onClick = onClose),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.KeyboardArrowDown, "Close queue", tint = Color.White) }
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }

            // What's playing, first in the list: lit up, with the Wave lines moving.
            item(key = "now") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(app.wave.music.platform.PrismaLook.corner(20.dp)))
                        .background(Brush.horizontalGradient(listOf(c.primary.copy(alpha = 0.28f), c.primary.copy(alpha = 0.1f))))
                        .padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AsyncImage(current.artworkUrl?.atSize(ArtSize.THUMB), null, Modifier.size(48.dp).clip(RoundedCornerShape(app.wave.music.platform.PrismaLook.corner(12.dp))), contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f)) {
                        Text(current.title, color = c.onBackground, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(current.artist, color = c.onBackground.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    WaveLines(state.isPlaying, c.primary)
                }
            }

            items(entries, key = { it.key }) { entry ->
                ReorderableItem(reorder, key = entry.key) { isDragging ->
                    // Picked up: it lifts and grows a touch; let go and it settles back in.
                    val lift by animateFloatAsState(if (isDragging) 1f else 0f, WaveMotion.bouncy(), label = "lift")
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .graphicsLayer {
                                scaleX = 1f + 0.03f * lift
                                scaleY = 1f + 0.03f * lift
                            }
                            .shadow(12.dp * lift, RoundedCornerShape(app.wave.music.platform.PrismaLook.corner(20.dp)))
                            .clip(RoundedCornerShape(app.wave.music.platform.PrismaLook.corner(20.dp)))
                            .background(c.surfaceContainerHighest.copy(alpha = lift.coerceIn(0f, 1f)))
                            .clickable { PlayerConnection.skipTo(entry.index) }
                            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        AsyncImage(entry.song.artworkUrl?.atSize(ArtSize.THUMB), null, Modifier.size(48.dp).clip(RoundedCornerShape(app.wave.music.platform.PrismaLook.corner(12.dp))), contentScale = ContentScale.Crop)
                        Column(Modifier.weight(1f)) {
                            Text(entry.song.title, color = c.onBackground, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(entry.song.artist, color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { PlayerConnection.removeAt(entry.index) }) {
                            Icon(Icons.Rounded.Close, "Remove ${entry.song.title} from the queue", tint = c.onBackground.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                        }
                        if (!state.shuffle) {
                            IconButton(
                                onClick = {},
                                modifier = Modifier.draggableHandle(
                                    onDragStarted = {
                                        dragging = true
                                        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                    },
                                    onDragStopped = {
                                        commitMove(entry.key)
                                        dragging = false
                                        haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                    },
                                ),
                            ) {
                                Icon(Icons.Rounded.DragIndicator, "Drag to reorder ${entry.song.title}", tint = c.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item { Box(Modifier.navigationBarsPadding()) }
        }
    }
}


/** The Wave mark's three lines, rippling while the song plays and settling flat when paused. */
@Composable
private fun WaveLines(playing: Boolean, color: Color) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "wave")
    val phase by t.animateFloat(0f, (2 * Math.PI).toFloat(), androidx.compose.animation.core.infiniteRepeatable(tween(1100, easing = androidx.compose.animation.core.LinearEasing)), label = "phase")
    val amount by animateFloatAsState(if (playing) 1f else 0.25f, tween(400), label = "amount")
    androidx.compose.foundation.Canvas(Modifier.size(width = 28.dp, height = 22.dp)) {
        val stroke = 2.6.dp.toPx()
        val amp = 2.6.dp.toPx() * amount
        repeat(3) { row ->
            val y0 = size.height * (0.2f + row * 0.3f)
            val path = androidx.compose.ui.graphics.Path()
            val steps = 24
            for (i in 0..steps) {
                val x = size.width * i / steps
                val y = y0 + amp * kotlin.math.sin(phase + row * 0.9f + x / size.width * 2f * Math.PI.toFloat())
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
    }
}
