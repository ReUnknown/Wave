package app.wave.music.ui.player

import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.Crossfade
import androidx.compose.ui.platform.LocalConfiguration
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
import app.wave.music.ui.components.ArtSize
import app.wave.music.ui.components.atSize
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
import app.wave.music.ui.components.rememberBackdrop
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
    /** Docked in the right half of a big computer window, beside the rest of the app. */
    docked: Boolean = false,
    /** Which side it's docked on: -1 left, 1 right. */
    dockSide: Int = 1,
    /** Big screens: switch between docked and the full window (or back to the player bar). */
    onToggleSize: (() -> Unit)? = null,
    /** Open straight to "lyrics" or "queue" (from the player bar's buttons). */
    openTo: String? = null,
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
    val peeking = app.wave.music.ui.components.LocalPeeking.current
    // Only the progress line and the times read this, so only they update as the song plays.
    val position = rememberPosition(song.videoId, state.isPlaying, everyMs = 200)
    // Roomy wide layouts show lyrics / up next in their side panel; elsewhere they open over the player.
    val conf = LocalConfiguration.current
    val roomy = !docked && conf.screenWidthDp >= 840 && conf.screenHeightDp >= 560 && conf.screenWidthDp > conf.screenHeightDp
    var showQueue by remember { mutableStateOf(openTo == "queue" && !roomy) }
    var showLyrics by remember { mutableStateOf(openTo == "lyrics" && !roomy) }

    MaterialTheme(colorScheme = rememberArtColorScheme(song.artworkUrl)) {
        val c = MaterialTheme.colorScheme
        Box(
            Modifier
                .offset { IntOffset(0, (drag + back() * 220.dp.toPx()).roundToInt()) }
                .then(modifier)
                .fillMaxSize()
                .graphicsLayer {
                    val pulled = ((drag / dismissAt) + back()).coerceIn(0f, 1f)
                    // Docked: a panel with rounded inner corners against the app beside it.
                    shape = when {
                        docked && dockSide < 0 -> RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
                        docked -> RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
                        else -> RoundedCornerShape(36.dp.toPx() * pulled)
                    }
                    clip = docked || pulled > 0f
                }
                .blocksTouchesBelow()
                .background(c.background),
        ) {
        // Wide spaces (landscape phones, tablets, Fold, Googlebook) get a side-by-side player. It goes
        // by the room the player actually has, so docked in half a window it's the classic layout.
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp && maxWidth > maxHeight
        if (wide) {
            WideNowPlaying(
                state, song, position,
                roomy = maxWidth >= 840.dp && maxHeight >= 560.dp,
                onToggleSize = onToggleSize,
                docked = docked,
                openTo = openTo,
                showQueue = showQueue,
                showLyrics = showLyrics,
                onClose = onClose,
                onQueue = { showQueue = true },
                onLyrics = { showLyrics = true },
            )
        } else
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .draggable(
                    dragState,
                    Orientation.Vertical,
                    // Computers: no swipe-to-close; the collapse button and Esc do it.
                    enabled = !app.wave.music.ui.components.LocalDesktop.current,
                    // Dragging down uncovers home, so home must be drawn while you do.
                    onDragStarted = { peeking?.value = true },
                    onDragStopped = { velocity ->
                        if (drag > dismissAt || velocity > 2_000f) {
                            haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                            peeking?.value = false
                            onClose()
                        } else {
                            dragScope.launch {
                                animate(drag, 0f, animationSpec = WaveMotion.bouncy()) { v, _ -> drag = v }
                                peeking?.value = false
                            }
                        }
                    },
                ),
        ) {
            val artHeight = maxHeight * 0.62f
            val artPx = with(LocalDensity.current) { artHeight.toPx() }
            // Art fills the top and blurs progressively; a mirrored, fully blurred copy continues it
            // under the controls, so there's no edge where the picture ends.
            val topBlur = rememberBackdrop()
            val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            // A new song's art fades in over the old one instead of popping.
            AnimatedContent(
                targetState = song.artworkUrl,
                transitionSpec = { fadeIn(tween(320, easing = WaveMotion.Emphasized)) togetherWith fadeOut(tween(1, delayMillis = 320)) },
                modifier = Modifier
                    .fillMaxSize()
                    .backdropSource(topBlur),
                label = "art",
            ) { art ->
            Box(Modifier.fillMaxSize()) {
                // Fully blurred, so a small copy of the art is plenty.
                BlurredImage(
                    art?.atSize(ArtSize.BLUR),
                    Modifier
                        // Tucked a few dp under the art above, so the two never show a seam.
                        .offset(y = artHeight - 6.dp)
                        .fillMaxWidth()
                        .height(artHeight + 6.dp)
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

            Header(state, song, onClose, Modifier.statusBarsPadding(), onToggleSize, docked)

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            ) {
                Controls(state, song, position, Modifier.padding(horizontal = 20.dp))
                Tray(state, showQueue, showLyrics, onQueue = { showQueue = true }, onLyrics = { showLyrics = true })
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

@Composable
private fun Header(
    state: PlayerUiState,
    song: NowPlaying,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleSize: (() -> Unit)? = null,
    docked: Boolean = false,
) {
    Row(
        modifier
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
        // Computers: expand the docked player to the full window (lyrics and up next beside it), or dock it again.
        onToggleSize?.let { toggle ->
            GlassCircle(
                if (docked) Icons.Rounded.OpenInFull else Icons.Rounded.CloseFullscreen,
                if (docked) "Expand player" else "Shrink player",
                toggle,
            )
            Spacer(Modifier.width(8.dp))
        }
        GlassCircle(Icons.Rounded.MoreVert, "More options") {
            menu.open(MenuTarget.Song(song.videoId, song.title, song.artist, song.artworkUrl))
        }
    }
}

/**
 * The player on wide screens. The song's art, blurred, fills the window behind everything.
 * - Roomy (tablets, unfolded Fold, Googlebook): the cover above the controls on the left, and
 *   Lyrics / Up next always open in a panel on the right.
 * - Short (landscape phones, small windows): the cover on the left, the controls on the right,
 *   with Up next and Lyrics opening over the player like on a phone.
 */
@Composable
private fun WideNowPlaying(
    state: PlayerUiState,
    song: NowPlaying,
    position: androidx.compose.runtime.State<Long>,
    roomy: Boolean,
    showQueue: Boolean,
    showLyrics: Boolean,
    onClose: () -> Unit,
    onQueue: () -> Unit,
    onLyrics: () -> Unit,
    onToggleSize: (() -> Unit)? = null,
    docked: Boolean = false,
    openTo: String? = null,
) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        Crossfade(song.artworkUrl?.atSize(ArtSize.BLUR), Modifier.fillMaxSize(), animationSpec = tween(320), label = "wideBackdrop") { art ->
            BlurredImage(art, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.2f; scaleY = 1.2f }, radius = 96.dp)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to c.background.copy(alpha = 0.45f), 1f to c.background.copy(alpha = 0.82f))))
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Header(state, song, onClose, onToggleSize = onToggleSize, docked = docked)
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
            ) {
                if (roomy) {
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Cover(song, minOf(maxWidth, maxHeight))
                        }
                        Column(Modifier.widthIn(max = 560.dp).padding(top = 20.dp)) {
                            Controls(state, song, position)
                            ExtrasDrawer(state, Modifier.padding(top = 6.dp))
                        }
                    }
                    SidePanel(state, Modifier.weight(1f).fillMaxHeight(), startOnQueue = openTo == "queue")
                } else {
                    BoxWithConstraints(Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true), contentAlignment = Alignment.Center) {
                        Cover(song, minOf(maxWidth, maxHeight))
                    }
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                        Controls(state, song, position, dense = true)
                        Row(Modifier.padding(top = 10.dp).fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PanelButton(Icons.AutoMirrored.Rounded.QueueMusic, "Up next", "queue", showQueue, Modifier.weight(1f), onQueue)
                            PanelButton(Icons.Rounded.Lyrics, "Lyrics", "lyrics", showLyrics, Modifier.weight(1f), onLyrics)
                        }
                        ExtrasDrawer(state, Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}

/** The cover as a big rounded card; a new song's cover fades in over the old one. */
@Composable
private fun Cover(song: NowPlaying, side: androidx.compose.ui.unit.Dp) {
    AnimatedContent(
        targetState = song.artworkUrl,
        transitionSpec = { fadeIn(tween(320, easing = WaveMotion.Emphasized)) togetherWith fadeOut(tween(1, delayMillis = 320)) },
        label = "cover",
    ) { art ->
        AsyncImage(
            model = art,
            contentDescription = song.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(side)
                .shadow(28.dp, RoundedCornerShape(40.dp))
                .clip(RoundedCornerShape(40.dp)),
        )
    }
}

/** Wide layouts: a handle under the controls that pulls out volume and speed. */
@Composable
private fun ExtrasDrawer(state: PlayerUiState, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val reveal = rememberReveal()
    Column(modifier.fillMaxWidth().revealDrag(reveal)) {
        RevealHandle(reveal, c.onBackground.copy(alpha = 0.35f))
        RevealBox(reveal) {
            PlaybackExtras(state.speed, c.onBackground, c.primary, c.onPrimary, Modifier.padding(top = 4.dp, bottom = 4.dp))
        }
    }
}

/** Up next / Lyrics as buttons that grow into their pages (short wide layout). */
@Composable
private fun PanelButton(icon: ImageVector, label: String, key: String, open: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(modifier.fillMaxHeight()) {
        MorphVisibility(!open) {
            Row(
                Modifier
                    .sharedContainer(key, this, RoundedCornerShape(22.dp))
                    .fillMaxSize()
                    .pressable(RoundedCornerShape(22.dp), c.primaryContainer.copy(alpha = 0.92f), pressedScale = 0.95f, onClick = onClick),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = c.onPrimaryContainer)
                Text("  $label", color = c.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

/** Roomy wide layout: Lyrics and Up next live side by side with the player, one tap apart. */
@Composable
private fun SidePanel(state: PlayerUiState, modifier: Modifier, startOnQueue: Boolean = false) {
    val c = MaterialTheme.colorScheme
    var tab by rememberSaveable { mutableStateOf(if (startOnQueue) 1 else 0) }
    Column(
        modifier
            .clip(RoundedCornerShape(32.dp))
            .background(Color.Black.copy(alpha = 0.2f)),
    ) {
        Row(Modifier.padding(12.dp).fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf("Lyrics", "Up next").forEachIndexed { i, label ->
                val on = tab == i
                val fill by animateColorAsState(if (on) c.primary else c.surfaceContainerHighest.copy(alpha = 0.6f), tween(160), label = "panelTab")
                val ink by animateColorAsState(if (on) c.onPrimary else c.onSurface, tween(160), label = "panelTabInk")
                val shape = if (i == 0) RoundedCornerShape(22.dp, 8.dp, 8.dp, 22.dp) else RoundedCornerShape(8.dp, 22.dp, 22.dp, 8.dp)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pressable(shape, fill, pressedScale = 0.95f) { tab = i },
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = ink, fontWeight = FontWeight.SemiBold) }
            }
        }
        Crossfade(tab, Modifier.weight(1f).fillMaxWidth(), animationSpec = tween(200), label = "panel") { shown ->
            if (shown == 0) {
                LyricsScreen(state, onClose = {}, embedded = true)
            } else {
                QueueScreen(state, onClose = {}, embedded = true)
            }
        }
    }
}

/** Title, like, progress, the play controls and the play modes. [dense] for short landscape screens. */
@Composable
private fun Controls(
    state: PlayerUiState,
    song: NowPlaying,
    position: androidx.compose.runtime.State<Long>,
    modifier: Modifier = Modifier,
    dense: Boolean = false,
) {
    val c = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // The title slides the way you're going: next comes in from the right, back from the left.
            AnimatedContent(
                targetState = song,
                contentKey = { it.videoId },
                transitionSpec = {
                    fun spot(s: NowPlaying) = state.playOrder.indexOf(state.queue.indexOfFirst { it.videoId == s.videoId })
                    val dir = if (spot(targetState) >= spot(initialState)) 1 else -1
                    (slideInHorizontally(tween(280, easing = WaveMotion.EmphasizedDecelerate)) { dir * it / 3 } + fadeIn(tween(180, delayMillis = 40))) togetherWith
                        (slideOutHorizontally(tween(180, easing = WaveMotion.EmphasizedAccelerate)) { -dir * it / 3 } + fadeOut(tween(110))) using
                        SizeTransform(clip = false)
                },
                modifier = Modifier.weight(1f),
                label = "title",
            ) { shown ->
                Column {
                    Text(shown.title, color = c.onBackground, fontSize = if (dense) 24.sp else 32.sp, lineHeight = if (dense) 28.sp else 38.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(shown.artist, color = c.onSurfaceVariant, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            LikeButton(state.liked) {
                haptics.performHapticFeedback(if (state.liked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                PlayerConnection.toggleLike()
            }
        }

        Spacer(Modifier.height(if (dense) 8.dp else 16.dp))
        WavyProgress(
            progress = { if (state.durationMs > 0) position.value.toFloat() / state.durationMs else 0f },
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
        Times(position, state.durationMs, c.onSurfaceVariant)

        // Connected group: previous · play/pause · next
        Row(
            Modifier
                .padding(top = if (dense) 8.dp else 14.dp)
                .fillMaxWidth()
                .height(if (dense) 68.dp else 92.dp),
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
                PlayPauseIcon(state.isPlaying, c.onPrimary, size = if (dense) 36.dp else 46.dp)
            }
            GroupButton(Icons.Rounded.SkipNext, "Next song", RoundedCornerShape(14.dp, 46.dp, 46.dp, 14.dp), c.secondaryContainer, c.onSecondaryContainer, 84) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                PlayerConnection.next()
            }
        }

        // Labeled play modes
        Row(
            Modifier
                .padding(top = if (dense) 6.dp else 10.dp)
                .fillMaxWidth()
                .height(if (dense) 40.dp else 48.dp),
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
}

/**
 * Song-colored tray behind the gesture bar: what's next, and lyrics. Each grows into its page.
 * Swipe the tray up (or tap its handle) and it rises to show Wave's volume and playback speed.
 */
@Composable
private fun Tray(state: PlayerUiState, showQueue: Boolean, showLyrics: Boolean, onQueue: () -> Unit, onLyrics: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val reveal = rememberReveal()
    Column(
        modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(c.primaryContainer.copy(alpha = 0.92f))
            .revealDrag(reveal)
            .navigationBarsPadding(),
    ) {
    RevealHandle(reveal, c.onPrimaryContainer.copy(alpha = 0.35f), Modifier.padding(top = 4.dp))
    Row(
        Modifier
            .height(60.dp)
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
                .pressable(RoundedCornerShape(20.dp), pressedScale = 0.96f, onClick = onQueue)
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
                .pressable(RoundedCornerShape(20.dp), pressedScale = 0.94f, onClick = onLyrics)
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
    RevealBox(reveal) {
        PlaybackExtras(state.speed, c.onPrimaryContainer, c.primary, c.onPrimary, Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp))
    }
    }
}

/** Elapsed and remaining time; its own scope, so ticking seconds don't redraw the player. */
@Composable
private fun Times(position: androidx.compose.runtime.State<Long>, durationMs: Long, color: Color) {
    // Only changes once a second, however often the position ticks.
    val seconds by remember(position) { androidx.compose.runtime.derivedStateOf { position.value / 1000 } }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatTime(seconds * 1000), color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text("−" + formatTime(durationMs - seconds * 1000), color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
            pop.animateTo(1.3f, tween(90, easing = WaveMotion.EmphasizedDecelerate))
            pop.animateTo(1f, WaveMotion.bouncy())
        }
    }
    val container by animateColorAsState(if (liked) c.primary else c.primaryContainer, tween(200), label = "likeBg")
    val content by animateColorAsState(if (liked) c.onPrimary else c.onPrimaryContainer, tween(200), label = "likeFg")
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
    val fill by animateColorAsState(if (on) c.primary else Color.Transparent, tween(170), label = "modeFill")
    val ink by animateColorAsState(if (on) c.onPrimary else c.onBackground, tween(170), label = "modeInk")
    val outline by animateColorAsState(if (on) Color.Transparent else c.onBackground.copy(alpha = 0.2f), tween(170), label = "modeLine")
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
        AnimatedContent(label, transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(90)) using SizeTransform(clip = false) }, label = "modeLabel") {
            Text("  $it", color = ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}
