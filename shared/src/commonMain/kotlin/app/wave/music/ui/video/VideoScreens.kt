package app.wave.music.ui.video

import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wave.music.data.VideoRepository
import app.wave.music.data.VideoShelf
import app.wave.music.data.toVideoTarget
import app.wave.music.playback.PlayRequest
import app.wave.music.playback.PlayerConnection
import app.wave.music.playback.Video
import app.wave.music.playback.VideoTarget
import app.wave.music.platform.WavePlatform
import app.wave.music.platform.currentWindowDp
import app.wave.music.platform.topBarPadding
import app.wave.music.ui.components.LocalMiniPlayerSpace
import app.wave.music.ui.components.PlayPauseIcon
import app.wave.music.ui.components.blocksTouchesBelow
import app.wave.music.ui.components.pressable
import app.wave.music.ui.components.rememberArtColorScheme
import app.wave.music.ui.detail.PageActions
import app.wave.music.ui.player.WavyProgress
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.delay

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

private fun watchFrom(video: SongItem, list: List<SongItem>) {
    val i = list.indexOfFirst { it.id == video.id }
    // What comes after it in the row, then what came before.
    val next = if (i < 0) list else list.drop(i + 1) + list.take(i)
    Video.watch(video.toVideoTarget(), next.map { it.toVideoTarget() })
}

// ---------------------------------------------------------------- Cards

/** A music video: its picture (16:9), how long it is, its name and artist. */
@Composable
fun VideoCard(video: SongItem, width: Dp, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.width(width).pressable(RoundedCornerShape(20.dp), pressedScale = 0.95f, clipAndRipple = false, onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerHigh)) {
            AsyncImage(Video.thumbnail(video.id), video.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            video.duration?.let { d ->
                Text(
                    formatTime(d * 1000L),
                    Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 7.dp, vertical = 2.dp),
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Text(video.title, Modifier.padding(top = 8.dp, start = 2.dp), color = c.onBackground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
        Text(video.artists.joinToString { it.name }, Modifier.padding(start = 2.dp), color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The last card in a row of videos: the whole Videos page. */
@Composable
fun VideoMoreCard(width: Dp, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.width(width * 0.6f).pressable(RoundedCornerShape(20.dp), pressedScale = 0.95f, clipAndRipple = false, onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 15f).clip(RoundedCornerShape(20.dp)).background(c.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.SmartDisplay, null, tint = c.onPrimaryContainer, modifier = Modifier.size(40.dp)) }
        Text("All videos", Modifier.padding(top = 8.dp, start = 2.dp), color = c.onBackground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------------------------------------------------------------- The Videos page

/** Music videos picked for you, from the artists you play, plus what's trending and new. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideosScreen(actions: PageActions) {
    val c = MaterialTheme.colorScheme
    var shelves by remember { mutableStateOf<List<VideoShelf>?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        shelves = null
        shelves = runCatching { VideoRepository.shelves(refresh = attempt > 0) }.getOrDefault(emptyList())
    }
    val wide = currentWindowDp().screenWidthDp >= 700
    val cardWidth = if (wide) 300.dp else 260.dp
    Box(Modifier.fillMaxSize().blocksTouchesBelow().background(c.background), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp + LocalMiniPlayerSpace.current)) {
            item {
                Column(Modifier.topBarPadding().padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(48.dp).pressable(CircleShape, c.onBackground.copy(alpha = 0.08f), pressedScale = 0.88f, onClick = actions.back),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = c.onBackground) }
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier.size(48.dp).pressable(CircleShape, c.onBackground.copy(alpha = 0.08f), pressedScale = 0.88f) { attempt++ },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Refresh, "Refresh", tint = c.onBackground) }
                    }
                    Text("Music videos", Modifier.padding(top = 14.dp, start = 4.dp), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, color = c.onBackground)
                    Text("Picked from the artists you play.", Modifier.padding(start = 4.dp, top = 2.dp, bottom = 12.dp), color = c.onSurfaceVariant, fontSize = 15.sp)
                }
            }
            val list = shelves
            when {
                list == null -> item { Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(72.dp)) } }
                list.isEmpty() -> item {
                    Text("Couldn't find videos right now. Check your connection and try again.", Modifier.padding(24.dp), color = c.onSurfaceVariant, fontSize = 15.sp)
                }
                else -> {
                    val hero = list.first().videos.first()
                    item(key = "hero") { HeroVideo(hero) { watchFrom(hero, list.first().videos) } }
                    list.forEachIndexed { i, shelf ->
                        val videos = if (i == 0) shelf.videos.drop(1) else shelf.videos
                        if (videos.isEmpty()) return@forEachIndexed
                        item(key = "h-$i") {
                            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp)) {
                                shelf.label?.let { Text(it.uppercase(), color = c.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp) }
                                Text(shelf.title, color = c.onBackground, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        item(key = "r-$i") {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(videos, key = { "v$i" + it.id }) { v -> VideoCard(v, cardWidth) { watchFrom(v, shelf.videos) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The first video, big, with its name over it. */
@Composable
private fun HeroVideo(video: SongItem, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = 900.dp)
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .pressable(RoundedCornerShape(28.dp), c.surfaceContainerHigh, pressedScale = 0.97f, onClick = onClick),
    ) {
        AsyncImage(Video.thumbnail(video.id), video.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.78f))))
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("WATCH NOW", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(video.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 26.sp)
                Text(video.artists.joinToString { it.name }, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, maxLines = 1)
            }
            Box(Modifier.size(56.dp).clip(CircleShape).background(c.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, "Watch", tint = c.onPrimary, modifier = Modifier.size(32.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- Watching

/**
 * The video player while a video is open: full (sideways on phones; videos are watched horizontal
 * only) or shrunk to a mini player at the bottom while you use the rest of Wave.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideoPlayerHost(shell: Boolean = false) {
    val target = Video.open
    var shown by remember { mutableStateOf(target) }
    if (target != null) shown = target
    val current = shown ?: return
    val engine = Video.engine
    val base = app.wave.music.ui.theme.darkBaseScheme()
    MaterialTheme(colorScheme = rememberArtColorScheme(current.thumbnail, base = base)) {
        val c = MaterialTheme.colorScheme
        if (engine == null) {
            if (target != null) Box(Modifier.fillMaxSize().blocksTouchesBelow().background(c.background).clickable { close() }, contentAlignment = Alignment.Center) {
                Text("Videos play in the Android app.", color = c.onBackground, fontSize = 17.sp)
            }
            return@MaterialTheme
        }
        val state by engine.state.collectAsState()
        val history = remember { mutableListOf<VideoTarget>() }
        // Load each video once (full or mini); the music pauses while you watch.
        LaunchedEffect(target?.videoId) {
            val t = target ?: return@LaunchedEffect
            if (PlayerConnection.state.value.isPlaying) PlayerConnection.togglePlay()
            engine.load(t.videoId, Video.startAt)
            Video.startAt = 0
            if (Video.upNext.size < 3) Video.upNext = Video.upNext + VideoRepository.upNextFor(t).filter { n -> Video.upNext.none { it.videoId == n.videoId } }
        }
        // At the end, the next one starts.
        LaunchedEffect(state.ended) { if (state.ended && Video.upNext.isNotEmpty() && target != null) { history += target; playNext(target) } }

        val window = currentWindowDp()
        val big = WavePlatform.current.isComputer || window.smallestScreenWidthDp >= 600
        val wideRoom = big && window.screenWidthDp >= 840 && window.screenWidthDp > window.screenHeightDp
        var fullscreenAsked by remember { mutableStateOf(false) }
        val full = target != null && !Video.minimized
        // Phones: sideways while it's full, upright again as a mini player.
        LaunchedEffect(full, target == null) {
            when {
                target == null -> Unit
                full -> engine.holdOrientation(1)
                else -> engine.holdOrientation(2)
            }
        }
        val edgeToEdge = full && (!wideRoom || fullscreenAsked)
        LaunchedEffect(edgeToEdge) { engine.setBarsHidden(edgeToEdge) }
        androidx.compose.ui.backhandler.BackHandler(enabled = full) { if (wideRoom && fullscreenAsked) fullscreenAsked = false else Video.minimized = true }

        val actions = VideoActions(
            onClose = ::close,
            onMinimize = { fullscreenAsked = false; Video.minimized = true },
            onFullscreen = if (wideRoom) ({ fullscreenAsked = !fullscreenAsked }) else null,
            onNext = { history += current; playNext(current) },
            onPrevious = { playPrevious(current, history) },
            hasPrevious = history.isNotEmpty(),
            hasNext = Video.upNext.isNotEmpty(),
            onListen = {
                // Same song as music, picked up where the video was.
                val at = engine.positionMs
                close()
                PlayerConnection.play(
                    PlayRequest(listOf(app.wave.music.playback.NowPlaying(current.videoId, current.title, current.artist, current.thumbnail)), 0, app.wave.music.playback.PlaybackContext("video-${current.videoId}", current.title, current.thumbnail, null, isCollection = false), false),
                    at,
                )
            },
            onShare = { WavePlatform.current.share(current.title, "https://music.youtube.com/watch?v=${current.videoId}") },
        )

        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = full,
                enter = fadeIn(tween(220)) + androidx.compose.animation.scaleIn(tween(320, easing = app.wave.music.ui.components.WaveMotion.EmphasizedDecelerate), initialScale = 0.9f),
                exit = fadeOut(tween(180)) + androidx.compose.animation.scaleOut(tween(240), targetScale = 0.9f),
            ) {
                Box(Modifier.fillMaxSize().blocksTouchesBelow().background(if (edgeToEdge) Color.Black else c.background)) {
                    if (edgeToEdge) VideoFrame(engine, state, current, actions, full = true, modifier = Modifier.fillMaxSize())
                    else Row(Modifier.fillMaxSize().topBarPadding().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        // Tablets and Googlebook: the video big on the left, what's next on the right.
                        Column(Modifier.weight(1.75f).fillMaxHeight()) {
                            Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                RoundButton(Icons.Rounded.KeyboardArrowDown, "Shrink to the mini player", c.onBackground.copy(alpha = 0.08f), c.onBackground, onClick = actions.onMinimize)
                                Text("Watching", Modifier.padding(start = 12.dp), color = c.onBackground.copy(alpha = 0.7f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            }
                            VideoFrame(engine, state, current, actions, full = false, modifier = Modifier.fillMaxWidth().weight(1f, fill = false).aspectRatio(16f / 9f).clip(RoundedCornerShape(24.dp)))
                            VideoInfo(current, actions)
                        }
                        UpNextList(Modifier.weight(1f).fillMaxHeight(), current, history)
                    }
                }
            }
            AnimatedVisibility(
                visible = target != null && Video.minimized,
                modifier = Modifier.align(if (shell) Alignment.BottomEnd else Alignment.BottomCenter),
                enter = fadeIn(tween(200, delayMillis = 120)) + androidx.compose.animation.slideInVertically(tween(320, delayMillis = 80, easing = app.wave.music.ui.components.WaveMotion.EmphasizedDecelerate)) { it },
                exit = fadeOut(tween(160)) + androidx.compose.animation.slideOutVertically(tween(220)) { it },
            ) {
                VideoMiniPlayer(engine, state, current, shell)
            }
        }
    }
}

/**
 * The video's mini player: like the music one, but with the video itself still playing in it, a
 * VIDEO tag, and an X to stop. Tap it to watch full again.
 */
@Composable
private fun VideoMiniPlayer(engine: app.wave.music.playback.VideoEngine, state: app.wave.music.playback.VideoState, target: VideoTarget, shell: Boolean) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = if (shell) 104.dp else 12.dp)
            .then(if (shell) Modifier.width(420.dp) else Modifier.widthIn(max = 640.dp).fillMaxWidth())
            .shadow(18.dp, RoundedCornerShape(30.dp))
            .clip(RoundedCornerShape(30.dp))
            .background(c.surfaceContainerHigh)
            .clickable { Video.minimized = false },
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // The video keeps playing, small.
            Box(Modifier.width(118.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(20.dp)).background(Color.Black)) {
                engine.Surface(Modifier.fillMaxSize())
                if (state.buffering && engine.positionMs < 500) AsyncImage(target.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("VIDEO", Modifier.clip(CircleShape).background(c.primary).padding(horizontal = 7.dp, vertical = 1.dp), color = c.onPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    Text(target.artist, color = c.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(target.title, Modifier.padding(top = 2.dp), color = c.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
            }
            val corner by androidx.compose.animation.core.animateDpAsState(if (state.playing) 16.dp else 24.dp, app.wave.music.ui.components.WaveMotion.bouncy(), label = "miniVideoPlay")
            Box(
                Modifier.size(48.dp).pressable(RoundedCornerShape(corner), c.primary, pressedScale = 0.88f) { engine.togglePlay() },
                contentAlignment = Alignment.Center,
            ) { PlayPauseIcon(state.playing, c.onPrimary, size = 24.dp) }
            Box(
                Modifier.size(40.dp).pressable(CircleShape, c.onSurface.copy(alpha = 0.08f), pressedScale = 0.86f, onClick = ::close),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, "Stop the video", tint = c.onSurface, modifier = Modifier.size(20.dp)) }
        }
        // How far along, as a thin line along the bottom.
        var position by remember { mutableLongStateOf(0L) }
        LaunchedEffect(target.videoId) { while (true) { position = engine.positionMs; delay(500) } }
        Box(Modifier.fillMaxWidth().height(3.dp).background(c.onSurface.copy(alpha = 0.1f))) {
            Box(Modifier.fillMaxWidth(if (state.durationMs > 0) (position.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f).height(3.dp).background(c.primary))
        }
    }
}

private fun close() {
    Video.engine?.setBarsHidden(false)
    Video.engine?.holdOrientation(0)
    Video.engine?.stop()
    Video.minimized = false
    Video.open = null
}

private fun playNext(target: VideoTarget) {
    val next = Video.upNext.firstOrNull() ?: return
    Video.watch(next, Video.upNext.drop(1) + target, keepSize = true)
}

private fun playPrevious(target: VideoTarget, history: MutableList<VideoTarget>) {
    val prev = history.removeLastOrNull() ?: return
    Video.watch(prev, listOf(target) + Video.upNext, keepSize = true)
}

private class VideoActions(
    val onClose: () -> Unit,
    /** Shrinks it to the mini player. */
    val onMinimize: () -> Unit,
    /** Big screens: the whole window, or back beside Up next. Phones are always full. */
    val onFullscreen: (() -> Unit)?,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
    val onListen: () -> Unit,
    val onShare: () -> Unit,
)

/** The name, the artist, and what you can do with it. */
@Composable
private fun VideoInfo(target: VideoTarget, actions: VideoActions) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 18.dp).padding(top = 14.dp)) {
        Text(target.title, color = c.onBackground, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 25.sp)
        Text(target.artist, Modifier.padding(top = 2.dp), color = c.onSurfaceVariant, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            actions.onFullscreen?.let { Pill(Icons.Rounded.Fullscreen, "Full screen", c.primary, c.onPrimary, it) }
            Pill(Icons.Rounded.Headphones, "Listen instead", c.onBackground.copy(alpha = 0.1f), c.onBackground, actions.onListen)
            RoundButton(Icons.Rounded.Share, "Share", c.onBackground.copy(alpha = 0.1f), c.onBackground, onClick = actions.onShare)
        }
    }
}

/** What plays next; tap one to watch it now. */
@Composable
private fun UpNextList(modifier: Modifier, target: VideoTarget, history: MutableList<VideoTarget>) {
    val c = MaterialTheme.colorScheme
    val next = Video.upNext
    LazyColumn(modifier, contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp)) {
        if (next.isNotEmpty()) item {
            Text("Up next", Modifier.padding(horizontal = 18.dp, vertical = 6.dp), color = c.onBackground, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        }
        items(next, key = { "n" + it.videoId }) { v ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { history += target; Video.watch(v, Video.upNext.filter { it.videoId != v.videoId } + target) }
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AsyncImage(v.thumbnail, null, Modifier.width(132.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(c.surfaceContainerHigh), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f)) {
                    Text(v.title, color = c.onBackground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
                    Text(v.artist, color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * The picture, with Wave's controls over it. Tap to show or hide them (they tuck away on their own
 * while it plays); double-tap the left or right side to jump 10 seconds.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VideoFrame(engine: app.wave.music.playback.VideoEngine, state: app.wave.music.playback.VideoState, target: VideoTarget, actions: VideoActions, full: Boolean, modifier: Modifier) {
    var controls by remember { mutableStateOf(true) }
    var touched by remember { mutableIntStateOf(0) }
    LaunchedEffect(controls, state.playing, touched) { if (controls && state.playing) { delay(3200); controls = false } }
    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(target.videoId) { while (true) { position = engine.positionMs; delay(200) } }
    var jump by remember { mutableIntStateOf(0) }
    LaunchedEffect(jump) { if (jump != 0) { delay(650); jump = 0 } }
    val shade by animateFloatAsState(if (controls || !state.playing) 1f else 0f, tween(220), label = "shade")

    BoxWithConstraints(modifier.background(Color.Black)) {
        val w = constraints.maxWidth
        // The picture keeps its shape (black bars where the screen's shape differs).
        Box(Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(state.width.toFloat() / state.height.coerceAtLeast(1), matchHeightConstraintsFirst = full)) {
            engine.Surface(Modifier.fillMaxSize())
        }
        // Before the first picture: the thumbnail.
        if (state.buffering && position < 500) AsyncImage(target.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)

        Box(
            Modifier.fillMaxSize().pointerInput(w) {
                detectTapGestures(
                    onTap = { controls = !controls; touched++ },
                    onDoubleTap = { at ->
                        val back = at.x < w / 2f
                        engine.seekTo((engine.positionMs + if (back) -10_000 else 10_000).coerceIn(0, state.durationMs.coerceAtLeast(0)))
                        jump = if (back) -1 else 1
                        touched++
                    },
                )
            },
        )
        // A soft shade at the top and bottom, so the controls read over any picture.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = shade }.background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.6f), 0.28f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f))))

        if (jump != 0) {
            Row(
                Modifier.align(if (jump < 0) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(if (jump < 0) Icons.Rounded.Replay10 else Icons.Rounded.Forward10, null, tint = Color.White)
                Text(if (jump < 0) "−10 s" else "+10 s", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        AnimatedVisibility(controls || !state.playing || state.error != null, Modifier.fillMaxSize(), enter = fadeIn(tween(160)), exit = fadeOut(tween(220))) {
            Box(Modifier.fillMaxSize()) {
                // Top: back out of full screen, what's playing, and the picture quality.
                if (full) Row(
                    Modifier.align(Alignment.TopStart).fillMaxWidth().topBarPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    RoundButton(Icons.Rounded.KeyboardArrowDown, "Shrink to the mini player", Color.White.copy(alpha = 0.16f), Color.White, onClick = actions.onMinimize)
                    Column(Modifier.weight(1f)) {
                        Text(target.title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(target.artist, color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    QualityChip(engine, state)
                    RoundButton(Icons.Rounded.Headphones, "Listen instead", Color.White.copy(alpha = 0.16f), Color.White, onClick = actions.onListen)
                } else Row(Modifier.align(Alignment.TopEnd).padding(10.dp)) { QualityChip(engine, state) }

                // Middle: previous, back 10, play/pause (big), forward 10, next.
                Row(
                    Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (full) 28.dp else 14.dp),
                ) {
                    if (full) RoundButton(Icons.Rounded.SkipPrevious, "Previous video", Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = if (actions.hasPrevious) 1f else 0.35f), size = 52.dp) { if (actions.hasPrevious) actions.onPrevious() }
                    RoundButton(Icons.Rounded.Replay10, "Back 10 seconds", Color.White.copy(alpha = 0.14f), Color.White, size = if (full) 56.dp else 46.dp) {
                        engine.seekTo((engine.positionMs - 10_000).coerceAtLeast(0)); touched++
                    }
                    val big = if (full) 84.dp else 64.dp
                    val corner by androidx.compose.animation.core.animateDpAsState(if (state.playing) big * 0.32f else big / 2, app.wave.music.ui.components.WaveMotion.bouncy(), label = "videoPlayShape")
                    Box(
                        Modifier.size(big).pressable(RoundedCornerShape(corner), MaterialTheme.colorScheme.primary, pressedScale = 0.9f) {
                            if (state.ended) engine.seekTo(0)
                            engine.togglePlay(); touched++
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Crossfade(when { state.error != null -> 2; state.buffering && !state.playing -> 1; state.ended -> 3; else -> 0 }, label = "videoPlay") { s ->
                            when (s) {
                                1 -> LoadingIndicator(Modifier.size(big * 0.6f), color = MaterialTheme.colorScheme.onPrimary)
                                2 -> Icon(Icons.Rounded.Refresh, "Try again", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(big * 0.45f))
                                3 -> Icon(Icons.Rounded.Replay, "Watch again", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(big * 0.45f))
                                else -> PlayPauseIcon(state.playing, MaterialTheme.colorScheme.onPrimary, size = big * 0.45f)
                            }
                        }
                    }
                    RoundButton(Icons.Rounded.Forward10, "Forward 10 seconds", Color.White.copy(alpha = 0.14f), Color.White, size = if (full) 56.dp else 46.dp) {
                        engine.seekTo((engine.positionMs + 10_000).coerceAtMost(state.durationMs.coerceAtLeast(0))); touched++
                    }
                    if (full) RoundButton(Icons.Rounded.SkipNext, "Next video", Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = if (actions.hasNext) 1f else 0.35f), size = 52.dp) { if (actions.hasNext) actions.onNext() }
                }
                state.error?.let {
                    Text(it, Modifier.align(Alignment.Center).padding(top = if (full) 150.dp else 110.dp), color = Color.White, fontSize = 14.sp)
                }

                // Bottom: the time, a wave you can drag through, and full screen.
                Row(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().then(if (full) Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 14.dp) else Modifier.padding(horizontal = 12.dp, vertical = 4.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(formatTime(position), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    WavyProgress(
                        progress = { if (state.durationMs > 0) (engine.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f },
                        playing = state.playing,
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.28f),
                        stroke = if (full) 4.dp else 3.dp,
                        amplitude = if (full) 3.dp else 2.5.dp,
                        wavelength = 22.dp,
                        showThumb = true,
                        onSeek = { f -> engine.seekTo((f * state.durationMs).toLong()); touched++ },
                        modifier = Modifier.weight(1f).height(32.dp),
                    )
                    Text(formatTime(state.durationMs), color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
                    val toggle = actions.onFullscreen
                    if (toggle != null) RoundButton(if (full) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, if (full) "Leave full screen" else "Full screen", Color.Transparent, Color.White, size = 40.dp, onClick = toggle)
                    else RoundButton(Icons.Rounded.PictureInPictureAlt, "Shrink to the mini player", Color.Transparent, Color.White, size = 40.dp, onClick = actions.onMinimize)
                }
            }
        }
    }
}

/** The picture quality: Auto (Wave picks for your connection), or a size you choose. Tap for the next. */
@Composable
private fun QualityChip(engine: app.wave.music.playback.VideoEngine, state: app.wave.music.playback.VideoState) {
    val sizes = state.heights.filter { it >= 144 }.distinct().sortedDescending()
    if (sizes.isEmpty()) return
    val options: List<Int?> = listOf<Int?>(null) + sizes
    val label = when (val cap = state.cap) {
        null -> "Auto" + (state.showing?.let { " · ${it}p" } ?: "")
        else -> if (cap >= 1080) "${cap}p HD" else "${cap}p"
    }
    Text(
        label,
        Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .clickable {
                val i = options.indexOf(state.cap)
                engine.setQuality(options.getOrElse(i + 1) { null })
            }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, fill: Color, tint: Color, size: Dp = 44.dp, onClick: () -> Unit) {
    Box(Modifier.size(size).pressable(CircleShape, fill, pressedScale = 0.86f, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(size * 0.52f))
    }
}

@Composable
private fun Pill(icon: ImageVector, label: String, fill: Color, ink: Color, onClick: () -> Unit) {
    Row(
        Modifier.height(44.dp).pressable(CircleShape, fill, pressedScale = 0.94f, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = ink, modifier = Modifier.size(20.dp))
        Text(label, color = ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
