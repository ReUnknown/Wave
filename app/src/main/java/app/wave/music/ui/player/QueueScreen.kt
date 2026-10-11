package app.wave.music.ui.player

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.Alignment
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
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
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
fun QueueScreen(state: PlayerUiState, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val current = state.current ?: return
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    val order = state.playOrder
    val currentPage = order.indexOf(state.index).coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = currentPage) { order.size }
    var userSwiping by remember { mutableStateOf(false) }

    // Follow the player when the song changes on its own.
    LaunchedEffect(currentPage) {
        if (pager.currentPage != currentPage && !pager.isScrollInProgress) pager.animateScrollToPage(currentPage)
    }
    // Swiping to a cover and letting it settle plays that song.
    LaunchedEffect(pager) {
        snapshotFlow { pager.isScrollInProgress }.collect { scrolling ->
            if (scrolling) {
                userSwiping = true
            } else if (userSwiping) {
                userSwiping = false
                val target = order.getOrNull(pager.settledPage)
                if (target != null && target != PlayerConnection.state.value.index) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    PlayerConnection.skipTo(target)
                    PlayerConnection.play()
                }
            }
        }
    }

    // Coming-up list: a local copy so drags are smooth; the player is only told when you let go.
    var dragging by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf(entriesFor(state.upcoming, state.queue)) }
    LaunchedEffect(state.upcoming, state.queue) {
        if (!dragging) entries = entriesFor(state.upcoming, state.queue)
    }
    val listState = rememberLazyListState()
    val headerItems = 1
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
        Modifier
            .fillMaxSize()
            .background(c.background),
    ) {
        BlurredImage(
            model = current.artworkUrl,
            radius = 80.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(600.dp)
                .graphicsLayer { scaleX = 1.3f; scaleY = 1.3f },
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to c.background.copy(alpha = 0.25f), 0.45f to c.background.copy(alpha = 0.7f), 0.75f to c.background)),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Column(Modifier.statusBarsPadding()) {
                    Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text("Up next", color = c.onBackground, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp)
                            Text(
                                listOfNotNull(state.context?.title?.let { "From $it" }, if (state.shuffle) "Shuffle on" else null).joinToString(" · "),
                                color = c.onSurfaceVariant,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.28f))
                                .clickable(onClick = onClose),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.KeyboardArrowDown, "Close queue", tint = Color.White) }
                    }

                    // Coverflow: played ← now playing → coming up
                    HorizontalPager(
                        state = pager,
                        contentPadding = PaddingValues(horizontal = 84.dp),
                        pageSpacing = (-40).dp,
                        beyondViewportPageCount = 2,
                        modifier = Modifier
                            .padding(top = 20.dp)
                            .fillMaxWidth()
                            .height(250.dp),
                    ) { page ->
                        val offset = pager.getOffsetDistanceInPages(page).coerceIn(-2f, 2f)
                        val song = state.queue.getOrNull(order[page])
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            AsyncImage(
                                model = song?.artworkUrl,
                                contentDescription = song?.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(226.dp)
                                    .graphicsLayer {
                                        rotationY = offset * -42f
                                        val s = 1f - abs(offset) * 0.2f
                                        scaleX = s
                                        scaleY = s
                                        alpha = 1f - abs(offset) * 0.28f
                                        cameraDistance = 14f * density
                                    }
                                    .shadow(22.dp, RoundedCornerShape(38.dp))
                                    .clip(RoundedCornerShape(38.dp))
                                    .clickable { scope.launch { pager.animateScrollToPage(page) } },
                            )
                        }
                    }

                    val shownPage = pager.currentPage
                    val shown = order.getOrNull(shownPage)?.let { state.queue.getOrNull(it) }
                    if (shown != null) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val rel = shownPage - currentPage
                            Text(
                                when {
                                    rel == 0 -> "NOW PLAYING"
                                    rel < 0 -> "PLAYED"
                                    rel == 1 -> "UP NEXT"
                                    else -> "$rel SONGS FROM NOW"
                                },
                                color = c.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                            )
                            Text(shown.title, color = c.onBackground, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(shown.artist, color = c.onSurfaceVariant, fontSize = 15.sp, maxLines = 1)
                        }
                    }
                    if (entries.isNotEmpty()) {
                        Text(
                            "Coming up",
                            Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp),
                            color = c.onBackground,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }

            items(entries, key = { it.key }) { entry ->
                ReorderableItem(reorder, key = entry.key) { isDragging ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .shadow(if (isDragging) 12.dp else 0.dp, RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isDragging) c.surfaceContainerHighest else Color.Transparent)
                            .clickable { PlayerConnection.skipTo(entry.index) }
                            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        AsyncImage(entry.song.artworkUrl, null, Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                        Column(Modifier.weight(1f)) {
                            Text(entry.song.title, color = c.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(entry.song.artist, color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { PlayerConnection.removeAt(entry.index) }) {
                            Icon(Icons.Rounded.Close, "Remove ${entry.song.title} from the queue", tint = c.onSurfaceVariant)
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
