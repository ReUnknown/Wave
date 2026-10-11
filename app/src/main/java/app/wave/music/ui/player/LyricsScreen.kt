package app.wave.music.ui.player

import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberUpdatedState
import app.wave.music.ui.components.ArtSize
import app.wave.music.ui.components.atSize
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wave.music.data.LyricLine
import app.wave.music.data.LyricWord
import app.wave.music.data.Lyrics
import app.wave.music.data.LyricsRepository
import app.wave.music.playback.BeatBus
import app.wave.music.playback.PlayerConnection
import app.wave.music.data.WaveSettings
import app.wave.music.playback.PlayerUiState
import app.wave.music.ui.components.BlurredImage
import app.wave.music.ui.components.backGesture
import app.wave.music.ui.components.PlayPauseIcon
import app.wave.music.ui.components.WaveMotion
import app.wave.music.ui.components.pressable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.wave.music.ui.components.predictiveBack
import app.wave.music.ui.components.blocksTouchesBelow
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private sealed interface LyricsUi {
    data object Loading : LyricsUi
    data object Missing : LyricsUi
    data class Ready(val lyrics: Lyrics) : LyricsUi
}

private val LineStyle = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
private val BackingStyle = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)

/** Scrolls so item [index] sits in the exact vertical center of the list. */
private suspend fun LazyListState.centerOn(index: Int, animate: Boolean) {
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) scrollToItem(index)
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val center = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
    val delta = (item.offset + item.size / 2 - center).toFloat()
    if (animate) animateScrollBy(delta, spring(dampingRatio = 0.9f, stiffness = 110f)) else scrollBy(delta)
}

/**
 * Lyrics in the style of Apple Music and Cider: big bold lines over the blurred art, the current
 * line centered, each word lighting up as it's sung, and long held words glowing and lifting.
 * Tap any line to jump the song there.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricsScreen(state: PlayerUiState, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val back = predictiveBack(onBack = onClose)
    val song = state.current ?: return
    val scope = rememberCoroutineScope()
    var ui by remember(song.videoId) { mutableStateOf<LyricsUi>(LyricsUi.Loading) }
    var position by remember { mutableLongStateOf(0L) }
    var userScrolledAt by remember { mutableLongStateOf(0L) }
    val listState = rememberLazyListState()
    val drag = remember { MutableInteractionSource() }

    LaunchedEffect(song.videoId, state.durationMs > 0) {
        if (state.durationMs <= 0) return@LaunchedEffect
        ui = LyricsUi.Loading
        ui = LyricsRepository.load(song.videoId, song.title, song.artist, state.durationMs, song.album)
            ?.let { LyricsUi.Ready(it) } ?: LyricsUi.Missing
    }
    // Smooth, per-frame position for the word fill.
    // Every frame while playing; paused, just often enough to catch a seek.
    val playingNow by rememberUpdatedState(state.isPlaying)
    LaunchedEffect(song.videoId) {
        while (true) {
            if (playingNow) {
                withFrameMillis { position = PlayerConnection.position }
            } else {
                position = PlayerConnection.position
                delay(300)
            }
        }
    }
    // Only real finger drags pause auto-follow; taps don't.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) userScrolledAt = System.currentTimeMillis() }
    }

    val lyrics = (ui as? LyricsUi.Ready)?.lyrics
    // Only recomposes when the line changes; the per-frame word fill happens at draw time.
    val active by remember(lyrics) {
        derivedStateOf { if (lyrics?.synced == true) lyrics.lines.indexOfLast { it.startMs <= position + 80 } else -1 }
    }
    val positionNow: () -> Long = { position }

    // Move with the music: the sung line swells slightly on each beat and glows with loudness.
    val settings by WaveSettings.state.collectAsState()
    val beat by BeatBus.beat.collectAsState()
    val level by BeatBus.level.collectAsState()
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(beat) {
        if (settings.moveWithMusic && beat != 0L) {
            pulse.snapTo(1f)
            pulse.animateTo(0f, tween(320))
        }
    }
    val levelNow: () -> Float = { if (settings.moveWithMusic) level else 0f }
    val pulseNow: () -> Float = { if (settings.moveWithMusic) pulse.value else 0f }

    LaunchedEffect(active, ui) {
        if (active >= 0 && System.currentTimeMillis() - userScrolledAt > 3_500) listState.centerOn(active, animate = true)
    }

    BoxWithConstraints(
        modifier
            .backGesture(back)
            .fillMaxSize()
            .blocksTouchesBelow()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Backdrop: the art, blurred hard and darkened, so the page glows in the song's colors.
        BlurredImage(
            model = song.artworkUrl?.atSize(ArtSize.BLUR),
            radius = 90.dp,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = 1.4f; scaleY = 1.4f },
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)))

        when (val current = ui) {
            LyricsUi.Loading -> LoadingIndicator(Modifier.size(72.dp).align(Alignment.Center))
            LyricsUi.Missing -> Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No lyrics for this one", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Instrumental, or nobody has added them yet.", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
            }
            is LyricsUi.Ready -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = maxHeight / 2, bottom = maxHeight / 2),
            ) {
                itemsIndexed(current.lyrics.lines) { index, line ->
                    LyricLineItem(
                        line = line,
                        synced = current.lyrics.synced,
                        distance = if (current.lyrics.synced && active >= 0) index - active else 0,
                        position = positionNow,
                        level = levelNow,
                        pulse = pulseNow,
                        onTap = {
                            if (current.lyrics.synced) {
                                PlayerConnection.seekTo(line.startMs)
                                PlayerConnection.play()
                                userScrolledAt = 0L
                                scope.launch { listState.centerOn(index, animate = true) }
                            }
                        },
                    )
                }
                item {
                    Text(
                        (if (current.lyrics.synced) "" else "These lyrics aren't time-synced · ") + "Lyrics from ${current.lyrics.source}",
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                    )
                }
            }
        }

        // Header fade + controls
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent))),
        )
        Row(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .pressable(CircleShape, Color.Black.copy(alpha = 0.3f), pressedScale = 0.86f, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.KeyboardArrowDown, "Close lyrics", tint = Color.White) }
            AsyncImage(song.artworkUrl?.atSize(ArtSize.THUMB), null, Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f)) {
                Text(song.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val playCorner by animateDpAsState(if (state.isPlaying) 20.dp else 28.dp, WaveMotion.bouncy(), label = "playShape")
                Box(
                    Modifier
                        .size(56.dp)
                        .pressable(RoundedCornerShape(playCorner), MaterialTheme.colorScheme.primary, pressedScale = 0.9f) { PlayerConnection.togglePlay() }
                        .semantics { contentDescription = if (state.isPlaying) "Pause" else "Play" },
                    contentAlignment = Alignment.Center,
                ) {
                    PlayPauseIcon(state.isPlaying, MaterialTheme.colorScheme.onPrimary)
                }
                WavyProgress(
                    progress = { if (state.durationMs > 0) position.toFloat() / state.durationMs else 0f },
                    playing = state.isPlaying,
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                    stroke = 4.dp,
                    showThumb = true,
                    onSeek = { PlayerConnection.seekTo((it * state.durationMs).toLong()) },
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricLineItem(
    line: LyricLine,
    synced: Boolean,
    distance: Int,
    position: () -> Long,
    level: () -> Float,
    pulse: () -> Float,
    onTap: () -> Unit,
) {
    val isActive = synced && distance == 0
    val alpha by animateFloatAsState(
        when {
            !synced || isActive -> 1f
            distance < 0 -> 0.32f
            else -> (0.48f - distance * 0.05f).coerceAtLeast(0.2f)
        },
        tween(500),
        label = "alpha",
    )
    val scale by animateFloatAsState(if (isActive || !synced) 1f else 0.93f, spring(dampingRatio = 0.75f, stiffness = 180f), label = "scale")
    val blur by animateFloatAsState(if (!synced || abs(distance) <= 1) 0f else (abs(distance) * 1.2f).coerceAtMost(5f), tween(500), label = "blur")
    val isBreak = synced && line.text.isBlank()

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 28.dp, vertical = 12.dp)
            .graphicsLayer {
                val beatBump = if (isActive) 1f + pulse() * 0.025f else 1f
                scaleX = scale * beatBump
                scaleY = scale * beatBump
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .alpha(alpha)
            .blur(blur.dp),
    ) {
        if (isBreak) {
            if (isActive) BreakDots()
            return@Column
        }
        FlowRow {
            val wordPosition: () -> Long = when {
                isActive -> position
                distance < 0 -> { -> Long.MAX_VALUE }
                else -> { -> Long.MIN_VALUE }
            }
            line.words.forEach { word -> LyricWordText(word, wordPosition, if (isActive) level else { -> 0f }, LineStyle, synced) }
        }
        if (line.background.isNotEmpty()) {
            FlowRow(Modifier.padding(top = 4.dp)) {
                line.background.forEach { word -> LyricWordText(word, if (isActive) position else { -> Long.MIN_VALUE }, { 0f }, BackingStyle, synced) }
            }
        }
    }
}

/**
 * One word. Unsung it's dim; as it's sung a soft-edged wipe fills it bright left to right.
 * Long held words also glow and lift, like Apple Music's emphasis; the glow breathes with the
 * music's loudness. All of this reads the playback position at draw time, not in composition,
 * so it stays smooth without recomposing the lyrics every frame.
 */
@Composable
private fun LyricWordText(word: LyricWord, position: () -> Long, level: () -> Float, style: TextStyle, synced: Boolean) {
    val text = word.text + if (word.spaceAfter) " " else ""
    if (!synced) {
        Text(text, style = style, color = Color.White)
        return
    }
    val duration = (word.endMs - word.startMs).coerceAtLeast(1)
    val emphasized = duration >= 900 && word.text.length <= 14
    fun fillAt(p: Long) = ((p - word.startMs).toFloat() / duration).coerceIn(0f, 1f)
    fun liftAt(p: Long) = if (emphasized && p in word.startMs..word.endMs) sin(PI * fillAt(p)).toFloat() else 0f

    Box(
        Modifier.graphicsLayer {
            val lift = liftAt(position())
            translationY = -lift * 4.dp.toPx()
            val s = 1f + lift * 0.05f
            scaleX = s
            scaleY = s
            transformOrigin = TransformOrigin(0f, 0.6f)
        },
    ) {
        Text(text, style = style, color = Color.White.copy(alpha = 0.35f))
        if (emphasized) {
            // Glow layer, visible only while the word is held.
            Text(
                text,
                style = style.copy(shadow = Shadow(Color.White, Offset.Zero, 26f)),
                color = Color.White,
                modifier = Modifier.graphicsLayer {
                    val p = position()
                    alpha = if (p in word.startMs..word.endMs) (0.45f + level() * 0.55f) * liftAt(p).coerceAtLeast(0.3f) else 0f
                },
            )
        }
        Text(
            text,
            style = style,
            color = Color.White,
            modifier = Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    val fill = fillAt(position())
                    if (fill <= 0f) return@drawWithContent
                    drawContent()
                    if (fill < 1f) {
                        val edge = size.width * fill
                        val feather = 14.dp.toPx()
                        drawRect(
                            Brush.horizontalGradient(
                                0f to Color.Black,
                                (edge / size.width).coerceIn(0f, 1f) to Color.Black,
                                ((edge + feather) / size.width).coerceIn(0f, 1f) to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                },
        )
    }
}

/** Animated "• • •" shown during instrumental breaks. */
@Composable
private fun BreakDots() {
    val t by rememberInfiniteTransition(label = "dots").animateFloat(0f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Restart), label = "t")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        repeat(3) { i ->
            val phase = ((t * 3f) - i).let { if (it < 0) it + 3f else it }
            val lit = phase in 0f..1f
            Box(
                Modifier
                    .size(if (lit) 14.dp else 11.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (lit) 1f else 0.45f)),
            )
        }
    }
}
