package app.wave.music.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wave.music.data.WaveSettings
import app.wave.music.playback.BeatBus
import app.wave.music.playback.NowPlaying
import app.wave.music.playback.PlayerConnection
import app.wave.music.playback.PlayerUiState
import app.wave.music.ui.components.ArtSize
import app.wave.music.ui.components.BlurredImage
import app.wave.music.ui.components.PlayPauseIcon
import app.wave.music.ui.components.WaveMotion
import app.wave.music.ui.components.atSize
import app.wave.music.ui.components.maskedBy
import app.wave.music.ui.components.pressable
import app.wave.music.ui.components.rememberArtColorScheme
import app.wave.music.ui.theme.darkBaseScheme
import coil3.compose.AsyncImage

/** The mini player's sizes, in dp: a pill, the everyday card, and one with big art. */
enum class PopOutSize(val widthDp: Int, val heightDp: Int) {
    Tiny(300, 68),
    Compact(420, 156),
    Bigger(320, 480),
}

/**
 * The mini player that stays on top of other windows (computers): the cover's face fading into
 * the song's colors, the song, progress and controls. Its window buttons (bigger/smaller, back to
 * Wave, close) show on hover in a spot of their own, never over the controls; right-click for
 * every size. The window around it (moving, snapping, staying on top) is the platform's.
 */
@Composable
fun PopOutPlayer(
    size: PopOutSize,
    onSize: (PopOutSize) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by PlayerConnection.state.collectAsState()
    val song = state.current
    val scheme = rememberArtColorScheme(song?.artworkUrl, darkBaseScheme())
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    var menu by remember { mutableStateOf(false) }
    val radius = when (size) {
        PopOutSize.Tiny -> 34.dp
        PopOutSize.Compact -> 26.dp
        PopOutSize.Bigger -> 30.dp
    }

    MaterialTheme(colorScheme = scheme) {
        Box(
            modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(radius))
                .background(Color.Black)
                .hoverable(hover)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) menu = true
                        }
                    }
                },
        ) {
            // The song's colors: the art, blurred, under a dark veil.
            Crossfade(song?.artworkUrl?.atSize(ArtSize.BLUR), Modifier.matchParentSize(), animationSpec = tween(320), label = "popArt") { art ->
                BlurredImage(art, Modifier.fillMaxSize(), radius = 36.dp)
            }
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.45f)))

            if (song != null) {
                val chrome by animateFloatAsState(if (hovered) 1f else 0f, tween(180), label = "popChrome")
                when (size) {
                    PopOutSize.Compact -> Compact(song, state, chrome, onSize, onBack, onClose)
                    PopOutSize.Bigger -> Bigger(song, state, chrome, onSize, onBack, onClose)
                    PopOutSize.Tiny -> Tiny(song, state, onSize)
                }
            }

            DropdownMenu(menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(18.dp)) {
                PopOutSize.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.name, fontWeight = if (s == size) FontWeight.Bold else FontWeight.Normal) },
                        onClick = { menu = false; onSize(s) },
                    )
                }
                DropdownMenuItem(text = { Text("Back to Wave") }, onClick = { menu = false; onBack() })
                DropdownMenuItem(text = { Text("Close") }, onClick = { menu = false; onClose() })
            }
        }
    }
}

/** 420 × 156: the cover's face on the left, fading into the card. */
@Composable
private fun BoxScope.Compact(
    song: NowPlaying,
    state: PlayerUiState,
    chrome: Float,
    onSize: (PopOutSize) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    Face(song, Modifier.width(210.dp).fillMaxHeight(), horizontal = true)
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 150.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // The song, and the window buttons beside it: nothing sits on anything.
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SongText(song, Modifier.weight(1f), titleSize = 16)
            WindowButtons(chrome, Icons.Rounded.OpenInFull, "Bigger mini player", { onSize(PopOutSize.Bigger) }, onBack, onClose)
        }
        Progress(state, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            LikeButton(state.liked)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                RoundIcon(Icons.Rounded.SkipPrevious, "Previous song") { PlayerConnection.previous() }
                PlayButton(state.isPlaying, Modifier.width(60.dp).height(46.dp), playingCorner = 14.dp, pausedCorner = 23.dp, iconSize = 22.dp)
                RoundIcon(Icons.Rounded.SkipNext, "Next song") { PlayerConnection.next() }
            }
        }
    }
}

/** 320 × 480: the cover fills the top and fades down into the song's colors. */
@Composable
private fun BoxScope.Bigger(
    song: NowPlaying,
    state: PlayerUiState,
    chrome: Float,
    onSize: (PopOutSize) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    Face(song, Modifier.fillMaxWidth().height(340.dp), horizontal = false)
    Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) {
        WindowButtons(chrome, Icons.Rounded.CloseFullscreen, "Smaller mini player", { onSize(PopOutSize.Compact) }, onBack, onClose)
    }
    Column(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SongText(song, Modifier.weight(1f), titleSize = 22)
            LikeButton(state.liked)
        }
        Progress(state, Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            GroupButton(Icons.Rounded.SkipPrevious, "Previous song", RoundedCornerShape(28.dp, 12.dp, 12.dp, 28.dp), MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), Color.White, 72) { PlayerConnection.previous() }
            PlayButton(state.isPlaying, Modifier.weight(1f).fillMaxHeight(), playingCorner = 16.dp, pausedCorner = 28.dp, iconSize = 26.dp)
            GroupButton(Icons.Rounded.SkipNext, "Next song", RoundedCornerShape(12.dp, 28.dp, 28.dp, 12.dp), MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), Color.White, 72) { PlayerConnection.next() }
        }
    }
}

/** 300 × 68: a pill with the song and play/pause. Click the song for the everyday size. */
@Composable
private fun BoxScope.Tiny(song: NowPlaying, state: PlayerUiState, onSize: (PopOutSize) -> Unit) {
    Face(song, Modifier.width(130.dp).fillMaxHeight(), horizontal = true)
    Row(
        Modifier.fillMaxSize().padding(start = 92.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SongText(
            song,
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onSize(PopOutSize.Compact) },
            titleSize = 14,
        )
        PlayButton(state.isPlaying, Modifier.size(48.dp), playingCorner = 14.dp, pausedCorner = 24.dp, iconSize = 20.dp)
    }
}

/** The cover itself, fading out toward the right ([horizontal]) or the bottom. */
@Composable
private fun Face(song: NowPlaying, modifier: Modifier, horizontal: Boolean) {
    val fade = if (horizontal) {
        Brush.horizontalGradient(0.4f to Color.Black, 1f to Color.Transparent)
    } else {
        Brush.verticalGradient(0.55f to Color.Black, 1f to Color.Transparent)
    }
    Crossfade(song.artworkUrl?.atSize(ArtSize.CARD), modifier.maskedBy(fade), animationSpec = tween(320), label = "popFace") { art ->
        AsyncImage(art, song.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun SongText(song: NowPlaying, modifier: Modifier, titleSize: Int) {
    Column(modifier) {
        Text(song.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = titleSize.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.artist, color = Color.White.copy(alpha = 0.78f), fontSize = (if (titleSize > 16) 14 else 13).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Progress(state: PlayerUiState, modifier: Modifier) {
    val song = state.current ?: return
    val position = rememberPosition(song.videoId, state.isPlaying, everyMs = 250)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Time(position.value)
        WavyProgress(
            progress = { if (state.durationMs > 0) position.value.toFloat() / state.durationMs else 0f },
            playing = state.isPlaying,
            energy = { if (WaveSettings.state.value.moveWithMusic) BeatBus.level.value else -1f },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
            stroke = 3.dp,
            amplitude = 2.dp,
            wavelength = 14.dp,
            onSeek = { PlayerConnection.seekTo((it * state.durationMs).toLong()) },
            modifier = Modifier.weight(1f).height(14.dp),
        )
        Time(state.durationMs)
    }
}

@Composable
private fun Time(ms: Long) {
    val total = ms.coerceAtLeast(0) / 1000
    Text("%d:%02d".format(total / 60, total % 60), color = Color.White.copy(alpha = 0.78f), fontSize = 11.sp)
}

/** Play/pause: a rounded square while playing that springs into a pill when paused. */
@Composable
private fun PlayButton(playing: Boolean, modifier: Modifier, playingCorner: Dp, pausedCorner: Dp, iconSize: Dp) {
    val corner by animateDpAsState(if (playing) playingCorner else pausedCorner, WaveMotion.bouncy(), label = "popPlay")
    Box(
        modifier
            .pressable(RoundedCornerShape(corner), MaterialTheme.colorScheme.primary, pressedScale = 0.9f) { PlayerConnection.togglePlay() }
            .semantics { contentDescription = if (playing) "Pause" else "Play" },
        contentAlignment = Alignment.Center,
    ) { PlayPauseIcon(playing, MaterialTheme.colorScheme.onPrimary, size = iconSize) }
}

@Composable
private fun LikeButton(liked: Boolean) {
    RoundIcon(
        if (liked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
        if (liked) "Liked" else "Like",
        tint = if (liked) MaterialTheme.colorScheme.primary else Color.White,
        iconSize = 20.dp,
    ) { PlayerConnection.toggleLike() }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, tint: Color = Color.White, iconSize: Dp = 22.dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .pressable(CircleShape, pressedScale = 0.86f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = tint, modifier = Modifier.size(iconSize)) }
}

/** Resize, back to Wave and close: small, faded in on hover. */
@Composable
private fun WindowButtons(
    alpha: Float,
    resizeIcon: ImageVector,
    resizeLabel: String,
    onResize: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    Row(Modifier.alpha(alpha), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        val enabled = alpha > 0.5f
        SmallButton(resizeIcon, resizeLabel, enabled, onResize)
        SmallButton(Icons.Rounded.OpenInNew, "Back to Wave", enabled, onBack)
        SmallButton(Icons.Rounded.Close, "Close mini player", enabled, onClose)
    }
}

@Composable
private fun SmallButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .pressable(CircleShape, Color.Black.copy(alpha = 0.4f), enabled = enabled, pressedScale = 0.86f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(16.dp)) }
}
