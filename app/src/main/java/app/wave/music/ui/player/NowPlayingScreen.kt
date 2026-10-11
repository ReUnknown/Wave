package app.wave.music.ui.player

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import app.wave.music.playback.NowPlaying
import app.wave.music.ui.components.PlayPauseIcon
import app.wave.music.ui.components.MorphVisibility
import app.wave.music.ui.components.WaveMotion
import app.wave.music.ui.components.predictiveBack
import app.wave.music.ui.components.pressable
import app.wave.music.ui.components.sharedContainer
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import app.wave.music.playback.PlayerConnection
import app.wave.music.playback.BeatBus
import app.wave.music.data.WaveSettings
import app.wave.music.playback.PlayerUiState
import app.wave.music.ui.components.BlurredImage
import app.wave.music.ui.components.blocksTouchesBelow
import app.wave.music.ui.menu.LocalItemMenu
import app.wave.music.ui.menu.MenuTarget
import app.wave.music.ui.components.ProgressiveBackdropBlur
import app.wave.music.ui.components.backdropSource
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import app.wave.music.ui.components.ProgressiveBlurImage
import app.wave.music.ui.components.rememberArtColorScheme
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

/** Full-art Now Playing (design board 10). */
@Composable
fun NowPlayingScreen(
    state: PlayerUiState,
    onClose: () -> Unit,
    /** The shared container it grows out of (the mini player). */
    modifier: Modifier = Modifier,
    /** For Up next and Lyrics to grow out of their buttons. */
    visibility: AnimatedVisibilityScope? = null,
) {
    val song = state.current ?: return
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    // Drag the player down (or swipe back) and it follows your finger, then tucks back into the mini player.
    var drag by remember { mutableFloatStateOf(0f) }
    val back = predictiveBack(onBack = onClose)
    val dismissAt = with(density) { 140.dp.toPx() }
    val dragState = rememberDraggableState { delta -> drag = (drag + delta).coerceAtLeast(0f) }
    val dragScope = rememberCoroutineScope()
    var position by remember { mutableLongStateOf(0L) }
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    LaunchedEffect(song.videoId) {
        while (true) {
            position = PlayerConnection.position
            delay(200)
        }
    }

    MaterialTheme(colorScheme = rememberArtColorScheme(song.artworkUrl)) {
        val c = MaterialTheme.colorScheme
        Box(
            Modifier
                .offset { IntOffset(0, (drag + back() * 220.dp.toPx()).roundToInt()) }
                .then(modifier)
                .fillMaxSize()
                .graphicsLayer {
                    val pulled = ((drag / dismissAt) + back()).coerceIn(0f, 1f)
                    shape = RoundedCornerShape(36.dp.toPx() * pulled)
                    clip = pulled > 0f
                }
                .blocksTouchesBelow()
                .background(c.background),
        ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .draggable(
                    dragState,
                    Orientation.Vertical,
                    onDragStopped = { velocity ->
                        if (drag > dismissAt || velocity > 2_000f) {
                            haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                            onClose()
                        } else {
                            dragScope.launch { animate(drag, 0f, animationSpec = WaveMotion.bouncy()) { v, _ -> drag = v } }
                        }
                    },
                ),
        ) {
            val artHeight = maxHeight * 0.62f
            val artPx = with(LocalDensity.current) { artHeight.toPx() }
            // Art fills the top and blurs progressively; a mirrored, fully blurred copy continues it
            // under the controls, so there's no edge where the picture ends.
            val topBlur = rememberGraphicsLayer()
            val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            // A new song's art fades in over the old one instead of popping.
            AnimatedContent(
                targetState = song.artworkUrl,
                transitionSpec = { fadeIn(tween(520, easing = WaveMotion.Emphasized)) togetherWith fadeOut(tween(1, delayMillis = 520)) },
                modifier = Modifier
                    .fillMaxSize()
                    .backdropSource(topBlur),
                label = "art",
            ) { art ->
            Box(Modifier.fillMaxSize()) {
                BlurredImage(
                    art,
                    Modifier
                        .offset(y = artHeight)
                        .fillMaxWidth()
                        .height(artHeight)
                        .graphicsLayer { scaleY = -1f },
                    radius = 72.dp,
                )
                ProgressiveBlurImage(
                    art,
                    Modifier
                        .fillMaxWidth()
                        .height(artHeight),
                    start = 0.58f,
                    end = 1f,
                    maxRadius = 72.dp,
                )
            }
            }
            // Frosted top so "Playing from" always reads, whatever the art.
            ProgressiveBackdropBlur(topBlur, Modifier.fillMaxWidth().height(statusTop + 110.dp))
            // Darken toward the controls so text stays readable, keeping the song's color.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.3f),
                            0.12f to Color.Transparent,
                            0.38f to Color.Transparent,
                            0.62f to c.background.copy(alpha = 0.55f),
                            1f to c.background.copy(alpha = 0.82f),
                        ),
                    ),
            )

            Row(
                Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                GlassCircle(Icons.Rounded.KeyboardArrowDown, "Collapse player", onClose)
                state.context?.let {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text("PLAYING FROM", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp)
                        Text(it.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                val menu = LocalItemMenu.current
                GlassCircle(Icons.Rounded.MoreVert, "More options") {
                    menu.open(MenuTarget.Song(song.videoId, song.title, song.artist, song.artworkUrl))
                }
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // The title slides the way you're going: next comes in from the right, back from the left.
                        AnimatedContent(
                            targetState = song,
                            contentKey = { it.videoId },
                            transitionSpec = {
                                fun spot(s: NowPlaying) = state.playOrder.indexOf(state.queue.indexOfFirst { it.videoId == s.videoId })
                                val dir = if (spot(targetState) >= spot(initialState)) 1 else -1
                                (slideInHorizontally(tween(420, easing = WaveMotion.EmphasizedDecelerate)) { dir * it / 3 } + fadeIn(tween(260, delayMillis = 60))) togetherWith
                                    (slideOutHorizontally(tween(260, easing = WaveMotion.EmphasizedAccelerate)) { -dir * it / 3 } + fadeOut(tween(160))) using
                                    SizeTransform(clip = false)
                            },
                            modifier = Modifier.weight(1f),
                            label = "title",
                        ) { shown ->
                            Column {
                                Text(shown.title, color = c.onBackground, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(shown.artist, color = c.onSurfaceVariant, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        LikeButton(state.liked) {
                            haptics.performHapticFeedback(if (state.liked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                            PlayerConnection.toggleLike()
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    WavyProgress(
                        progress = if (state.durationMs > 0) position.toFloat() / state.durationMs else 0f,
                        playing = state.isPlaying,
                        energy = { if (WaveSettings.state.value.moveWithMusic) BeatBus.level.value else -1f },
                        color = c.onBackground,
                        trackColor = c.onBackground.copy(alpha = 0.25f),
                        stroke = 5.dp,
                        amplitude = 4.dp,
                        wavelength = 24.dp,
                        showThumb = true,
                        onSeek = { PlayerConnection.seekTo((it * state.durationMs).toLong()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(position), color = c.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("−" + formatTime(state.durationMs - position), color = c.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    // Connected group: previous · play/pause · next
                    Row(
                        Modifier
                            .padding(top = 14.dp)
                            .fillMaxWidth()
                            .height(92.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        GroupButton(Icons.Rounded.SkipPrevious, "Previous song", RoundedCornerShape(46.dp, 14.dp, 14.dp, 46.dp), c.secondaryContainer, c.onSecondaryContainer, 84) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            PlayerConnection.previous()
                        }
                        // Playing: a firm squircle. Paused: it relaxes into a pill, and the icon morphs.
                        val playCorner by animateDpAsState(if (state.isPlaying) 30.dp else 46.dp, WaveMotion.bouncy(), label = "playShape")
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .pressable(RoundedCornerShape(playCorner), c.primary, pressedScale = 0.94f) {
                                    haptics.performHapticFeedback(if (state.isPlaying) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                                    PlayerConnection.togglePlay()
                                }
                                .semantics { contentDescription = if (state.isPlaying) "Pause" else "Play" },
                            contentAlignment = Alignment.Center,
                        ) {
                            PlayPauseIcon(state.isPlaying, c.onPrimary, size = 46.dp)
                        }
                        GroupButton(Icons.Rounded.SkipNext, "Next song", RoundedCornerShape(14.dp, 46.dp, 46.dp, 14.dp), c.secondaryContainer, c.onSecondaryContainer, 84) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            PlayerConnection.next()
                        }
                    }

                    // Labeled play modes
                    Row(
                        Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth()
                            .height(48.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ModeButton(
                            Icons.Rounded.Shuffle,
                            if (state.shuffle) "Shuffle on" else "Shuffle off",
                            on = state.shuffle,
                            modifier = Modifier.weight(1f),
                        ) { PlayerConnection.toggleShuffle() }
                        ModeButton(
                            if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            when (state.repeatMode) {
                                Player.REPEAT_MODE_ALL -> "Repeat all"
                                Player.REPEAT_MODE_ONE -> "Repeat one"
                                else -> "Repeat off"
                            },
                            on = state.repeatMode != Player.REPEAT_MODE_OFF,
                            modifier = Modifier.weight(1f),
                        ) { PlayerConnection.cycleRepeat() }
                    }
                }

                // Bottom tray: what's next + lyrics
                // Song-colored tray that runs down behind the gesture bar.
                Row(
                    Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(c.primaryContainer.copy(alpha = 0.92f))
                        .navigationBarsPadding()
                        .height(68.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // What actually plays next, shuffle included.
                    val next = state.upcoming.firstOrNull()?.let(state.queue::getOrNull)
                    // Up next and Lyrics grow out of these buttons, and shrink back into them.
                    Box(Modifier.weight(1f)) {
                    MorphVisibility(!showQueue) {
                    Row(
                        Modifier
                            .sharedContainer("queue", this, RoundedCornerShape(20.dp))
                            .fillMaxWidth()
                            .pressable(RoundedCornerShape(20.dp), pressedScale = 0.96f) { showQueue = true }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = c.onPrimaryContainer)
                        Column {
                            Text("Up next", color = c.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                next?.let { "${it.title} · ${it.artist}" } ?: "End of queue",
                                color = c.onPrimaryContainer.copy(alpha = 0.75f),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    }
                    }
                    Box(Modifier.width(1.dp).height(32.dp).background(c.onPrimaryContainer.copy(alpha = 0.2f)))
                    Box(Modifier.width(128.dp)) {
                    MorphVisibility(!showLyrics) {
                    Row(
                        Modifier
                            .sharedContainer("lyrics", this, RoundedCornerShape(20.dp))
                            .fillMaxWidth()
                            .pressable(RoundedCornerShape(20.dp), pressedScale = 0.94f) { showLyrics = true }
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Lyrics, null, tint = c.onPrimaryContainer)
                        Text("  Lyrics", color = c.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    }
                    }
                }
            }
        }
        AnimatedVisibility(visible = showQueue, enter = EnterTransition.None, exit = ExitTransition.None) {
            QueueScreen(state, onClose = { showQueue = false }, modifier = Modifier.sharedContainer("queue", this, RoundedCornerShape(28.dp), fromTop = true))
        }
        AnimatedVisibility(visible = showLyrics, enter = EnterTransition.None, exit = ExitTransition.None) {
            LyricsScreen(state, onClose = { showLyrics = false }, modifier = Modifier.sharedContainer("lyrics", this, RoundedCornerShape(28.dp), fromTop = true))
        }
        }
    }
}

/** Like, with a little pop when you like it. */
@Composable
private fun LikeButton(liked: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val pop = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(liked) {
        if (first) { first = false; return@LaunchedEffect }
        if (liked) {
            pop.animateTo(1.3f, tween(110, easing = WaveMotion.EmphasizedDecelerate))
            pop.animateTo(1f, WaveMotion.bouncy())
        }
    }
    val container by animateColorAsState(if (liked) c.primary else c.primaryContainer, tween(300), label = "likeBg")
    val content by animateColorAsState(if (liked) c.onPrimary else c.onPrimaryContainer, tween(300), label = "likeFg")
    val corner by animateDpAsState(if (liked) 26.dp else 18.dp, WaveMotion.bouncy(), label = "likeShape")
    Box(
        Modifier
            .size(52.dp)
            .pressable(RoundedCornerShape(corner), container, pressedScale = 0.88f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (liked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
            contentDescription = if (liked) "Liked" else "Like",
            tint = content,
            modifier = Modifier.graphicsLayer {
                scaleX = pop.value
                scaleY = pop.value
                rotationZ = (pop.value - 1f) * -40f
            },
        )
    }
}

@Composable
private fun GlassCircle(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .pressable(CircleShape, Color.Black.copy(alpha = 0.3f), pressedScale = 0.86f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.White) }
}

@Composable
private fun ModeButton(icon: ImageVector, label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    // Turning a mode on fills it in and firms up its corners; off, it relaxes back to an outline.
    val corner by animateDpAsState(if (on) 18.dp else 24.dp, WaveMotion.bouncy(), label = "modeShape")
    val fill by animateColorAsState(if (on) c.primary else Color.Transparent, tween(260), label = "modeFill")
    val ink by animateColorAsState(if (on) c.onPrimary else c.onBackground, tween(260), label = "modeInk")
    val outline by animateColorAsState(if (on) Color.Transparent else c.onBackground.copy(alpha = 0.2f), tween(260), label = "modeLine")
    val shape = RoundedCornerShape(corner)
    val haptics = LocalHapticFeedback.current
    Row(
        modifier
            .fillMaxSize()
            .pressable(shape, fill, pressedScale = 0.95f) {
                haptics.performHapticFeedback(if (on) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                onClick()
            }
            .border(1.dp, outline, shape),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = ink)
        AnimatedContent(label, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false) }, label = "modeLabel") {
            Text("  $it", color = ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}
