package app.wave.music.ui.video

import kotlinx.coroutines.flow.drop
import androidx.compose.foundation.border
import app.wave.music.ui.components.glass
import dev.chrisbanes.haze.hazeSource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wave.music.data.VideoRepository
import app.wave.music.data.VideoShelf
import app.wave.music.data.toVideoTarget
import app.wave.music.platform.PrismaLook
import app.wave.music.platform.WavePlatform
import app.wave.music.platform.currentWindowDp
import app.wave.music.platform.prismaDisplayFont
import app.wave.music.platform.prismaTextFont
import app.wave.music.platform.topBarPadding
import app.wave.music.playback.PlayRequest
import app.wave.music.playback.PlayerConnection
import app.wave.music.playback.Video
import app.wave.music.playback.VideoEngine
import app.wave.music.playback.VideoState
import app.wave.music.playback.VideoTarget
import app.wave.music.ui.components.LocalMiniPlayerSpace
import app.wave.music.ui.components.PlayPauseIcon
import app.wave.music.ui.components.WaveMotion
import app.wave.music.ui.components.blocksTouchesBelow
import app.wave.music.ui.components.pressable
import app.wave.music.ui.components.rememberArtColorScheme
import app.wave.music.ui.detail.PageActions
import app.wave.music.ui.player.WavyProgress
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private fun display(size: TextUnit, weight: FontWeight = FontWeight.Bold) = TextStyle(fontFamily = prismaDisplayFont, fontSize = size, fontWeight = weight)
private fun body(size: TextUnit, weight: FontWeight = FontWeight.Normal) = TextStyle(fontFamily = prismaTextFont, fontSize = size, fontWeight = weight)

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** Watches [video], with what comes after it in [list] (then what came before) up next. */
fun watchFrom(video: SongItem, list: List<SongItem>) {
    val i = list.indexOfFirst { it.id == video.id }
    val next = if (i < 0) list else list.drop(i + 1) + list.take(i)
    Video.watch(video.toVideoTarget(), next.map { it.toVideoTarget() })
}

/**
 * Watches a song's music video: the same song picked up at the same spot when the video is the
 * song itself, or its official video from the start.
 */
fun watchSongVideo(songId: String, videoId: String, title: String, artist: String, positionMs: Long = 0, songArt: String? = null) {
    Video.watch(VideoTarget(videoId, title, artist, Video.thumbnail(videoId), songId = songId, songArt = songArt), startMs = if (videoId == songId) positionMs else 0L)
}

/**
 * Back to listening: the song the video came from, with its album cover. When that song is the one
 * in the player, the player just carries on (your queue, next and previous all still there);
 * otherwise the song starts with more like it after.
 */
private fun listenInstead(target: VideoTarget, atMs: Long) {
    val songId = target.songId ?: target.videoId
    val sameTiming = songId == target.videoId
    close()
    val now = PlayerConnection.state.value.current
    if (now?.videoId == songId) {
        if (sameTiming) PlayerConnection.seekTo(atMs)
        if (!PlayerConnection.state.value.isPlaying) PlayerConnection.togglePlay()
    } else {
        Video.playSong(
            SongItem(
                id = songId,
                title = target.title.replace(Regex("\\s*[(\\[](official|music|lyric|video|audio)[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE), "").trim(),
                artists = listOf(com.metrolist.innertube.models.Artist(target.artist, null)),
                thumbnail = target.songArt ?: target.thumbnail.orEmpty(),
            ),
        )
    }
}

// ---------------------------------------------------------------- Cards and the Videos page

/** A music video: its picture (16:9), how long it is, its name and artist. */
@Composable
fun VideoCard(video: SongItem, width: Dp, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(PrismaLook.corner(20.dp))
    Column(Modifier.width(width).pressable(shape, pressedScale = 0.95f, clipAndRipple = false, onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shape).background(c.surfaceContainerHigh)) {
            AsyncImage(Video.thumbnail(video.id), video.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            video.duration?.let { d ->
                Text(
                    formatTime(d * 1000L),
                    Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 2.dp),
                    color = Color.White, style = body(12.sp, FontWeight.SemiBold),
                )
            }
        }
        Text(video.title, Modifier.padding(top = 8.dp), color = c.onBackground, style = body(15.sp, FontWeight.SemiBold).copy(lineHeight = 19.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(video.artists.joinToString { it.name }, color = c.onBackground.copy(alpha = 0.6f), style = body(13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The last card in the videos row: the whole Videos page. */
@Composable
fun VideoMoreCard(width: Dp, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(PrismaLook.corner(20.dp))
    Column(Modifier.width(width * 0.56f).pressable(shape, pressedScale = 0.95f, clipAndRipple = false, onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f * 16 / 9 / 0.56f).clip(shape).background(c.onBackground.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.SmartDisplay, null, tint = c.primary, modifier = Modifier.size(34.dp))
        }
        Text("All videos", Modifier.padding(top = 8.dp), color = c.onBackground, style = body(15.sp, FontWeight.SemiBold))
    }
}

/** Music videos picked for you: from the artists you play, what's trending and what's new. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideosScreen(actions: PageActions) {
    val c = MaterialTheme.colorScheme
    var shelves by remember { mutableStateOf<List<VideoShelf>?>(null) }
    LaunchedEffect(Unit) { shelves = runCatching { VideoRepository.shelves() }.getOrDefault(emptyList()) }
    val wide = currentWindowDp().screenWidthDp >= 700
    val cardWidth = if (wide) 300.dp else 264.dp
    Box(Modifier.fillMaxSize().blocksTouchesBelow().background(c.background)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp + LocalMiniPlayerSpace.current)) {
            item {
                Column(Modifier.topBarPadding().padding(horizontal = 16.dp)) {
                    CircleButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", c.onBackground.copy(alpha = 0.08f), c.onBackground, 48.dp, actions.back)
                    Text("Videos", Modifier.padding(top = 14.dp, start = 4.dp), color = c.onBackground, style = display(34.sp).copy(letterSpacing = (-0.6).sp))
                    Text("Music videos from the artists you play.", Modifier.padding(start = 4.dp, top = 2.dp, bottom = 14.dp), color = c.onBackground.copy(alpha = 0.65f), style = body(15.sp))
                }
            }
            val list = shelves
            when {
                list == null -> item { Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(72.dp)) } }
                list.isEmpty() -> item { Text("Couldn't find videos right now. Check your connection and try again.", Modifier.padding(24.dp), color = c.onBackground.copy(alpha = 0.65f), style = body(15.sp)) }
                else -> {
                    val hero = list.first().videos.first()
                    item(key = "hero") { HeroVideo(hero) { watchFrom(hero, list.first().videos) } }
                    list.forEachIndexed { i, shelf ->
                        val videos = if (i == 0) shelf.videos.drop(1) else shelf.videos
                        if (videos.isEmpty()) return@forEachIndexed
                        item(key = "h-$i") {
                            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp)) {
                                shelf.label?.let { Text(it, color = c.onBackground.copy(alpha = 0.6f), style = body(13.sp, FontWeight.Medium)) }
                                Text(shelf.title, color = c.onBackground, style = display(22.sp).copy(letterSpacing = (-0.3).sp))
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

@Composable
private fun HeroVideo(video: SongItem, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = 900.dp)
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .pressable(RoundedCornerShape(PrismaLook.corner(28.dp)), c.surfaceContainerHigh, pressedScale = 0.97f, onClick = onClick),
    ) {
        AsyncImage(Video.thumbnail(video.id), video.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))))
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f)) {
                Text(video.title, color = Color.White, style = display(22.sp).copy(lineHeight = 26.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(video.artists.joinToString { it.name }, color = Color.White.copy(alpha = 0.8f), style = body(14.sp), maxLines = 1)
            }
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(PrismaLook.corner(18.dp))).background(c.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, "Watch", tint = c.onPrimary, modifier = Modifier.size(30.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- Watching

/** Where the dock's song row wants the video's picture (the dock is the video's mini player). */
object VideoDock {
    val spot = mutableStateOf<Rect?>(null)
}

/** Stops the video and closes its player. */
fun closeVideo() = close()

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

/** Where the picture should be: reported by the full player or the mini player, in window pixels. */
private fun Modifier.reportSpot(spot: MutableState<Rect?>, enabled: Boolean): Modifier =
    if (!enabled) this else onGloballyPositioned { spot.value = it.boundsInRoot() }

/**
 * The video player, over everything, while a video is open: full (sideways on phones; videos are
 * horizontal only) or a mini player at the bottom while you use the rest of Wave.
 *
 * There's one picture, and it glides between the two: each layout leaves a hole where the video
 * goes and says where it is, and the picture moves there. (Handing the video from one view to
 * another left the mini player blank on some phones.)
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideoPlayerHost(miniBottom: Dp = 12.dp, inDock: Boolean = false) {
    val target = Video.open
    var shown by remember { mutableStateOf(target) }
    if (target != null) shown = target
    val current = shown ?: return
    val engine = Video.engine
    MaterialTheme(colorScheme = rememberArtColorScheme(current.thumbnail, base = app.wave.music.ui.theme.darkBaseScheme())) {
        val c = MaterialTheme.colorScheme
        if (engine == null) {
            if (target != null) Box(Modifier.fillMaxSize().blocksTouchesBelow().background(c.background).clickable { close() }, contentAlignment = Alignment.Center) {
                Text("Videos play in the Android app.", color = c.onBackground, style = body(16.sp))
            }
            return@MaterialTheme
        }
        val state by engine.state.collectAsState()
        val history = remember { mutableListOf<VideoTarget>() }
        LaunchedEffect(target?.videoId) {
            val t = target ?: return@LaunchedEffect
            if (PlayerConnection.state.value.isPlaying) PlayerConnection.togglePlay()
            engine.load(t.videoId, Video.startAt)
            Video.startAt = 0
            if (Video.upNext.size < 3) Video.upNext = Video.upNext + VideoRepository.upNextFor(t).filter { n -> Video.upNext.none { it.videoId == n.videoId } }
        }
        // A song started while a video is open (you picked one): the video steps aside for it.
        LaunchedEffect(target?.videoId) {
            if (target == null) return@LaunchedEffect
            androidx.compose.runtime.snapshotFlow { PlayerConnection.state.value.isPlaying to PlayerConnection.state.value.current?.videoId }
                .drop(1)
                .collect { (playing, _) -> if (playing && Video.open != null) close() }
        }
        LaunchedEffect(state.ended) { if (state.ended && Video.upNext.isNotEmpty() && target != null) { history += target; playNext(target) } }

        val window = currentWindowDp()
        val big = WavePlatform.current.isComputer || minOf(window.screenWidthDp, window.screenHeightDp) >= 600
        val wideRoom = big && window.screenWidthDp >= 840 && window.screenWidthDp > window.screenHeightDp
        var fullscreenAsked by remember { mutableStateOf(false) }
        val full = target != null && !Video.minimized
        val mini = target != null && Video.minimized
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
            onMinimize = { fullscreenAsked = false; Video.minimized = true },
            onFullscreen = if (wideRoom) ({ fullscreenAsked = !fullscreenAsked }) else null,
            onNext = { history += current; playNext(current) },
            onPrevious = { playPrevious(current, history) },
            hasPrevious = history.isNotEmpty(),
            hasNext = Video.upNext.isNotEmpty(),
            onListen = { listenInstead(current, engine.positionMs) },
            onShare = { WavePlatform.current.share(current.title, "https://music.youtube.com/watch?v=${current.videoId}") },
        )

        // The controls are frosted glass over the picture, like Wave's dock.
        val glassOver = dev.chrisbanes.haze.rememberHazeState()
        val fullSpot = remember { mutableStateOf<Rect?>(null) }
        val ownSpot = remember { mutableStateOf<Rect?>(null) }
        // In the dock, the dock's song row is the mini player; otherwise Wave draws its own.
        val miniSpot = if (inDock) VideoDock.spot else ownSpot
        var origin by remember { mutableStateOf(Offset.Zero) }
        val fullAlpha by animateFloatAsState(if (full) 1f else 0f, tween(if (full) 220 else 180), label = "videoFull")
        val miniAlpha by animateFloatAsState(if (mini) 1f else 0f, tween(if (mini) 220 else 140, delayMillis = if (mini) 120 else 0), label = "videoMini")
        if (fullAlpha == 0f && miniAlpha == 0f && target == null) return@MaterialTheme

        Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
            // 1. Backgrounds, each with a hole where the picture goes.
            if (fullAlpha > 0f) Box(Modifier.fillMaxSize().graphicsLayer { alpha = fullAlpha }) {
                FullLayout(engine, state, current, actions, history, edgeToEdge, front = false, spot = fullSpot)
            }
            if (miniAlpha > 0f && !inDock) Box(Modifier.fillMaxSize().graphicsLayer { alpha = miniAlpha }) {
                MiniLayout(engine, state, current, miniBottom, front = false, spot = miniSpot)
            }
            // 2. The one picture, gliding to wherever it's wanted.
            val goal = (if (full || miniSpot.value == null) fullSpot.value else miniSpot.value)?.translate(-origin)
            if (goal != null) {
                val rect = remember { Animatable(goal, Rect.VectorConverter) }
                LaunchedEffect(goal) { rect.animateTo(goal, spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)) }
                val r = rect.value
                val density = LocalDensity.current
                val corner by animateDpAsState(if (full) (if (edgeToEdge) 0.dp else 24.dp) else 18.dp, tween(260), label = "videoCorner")
                Box(
                    Modifier
                        .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                        .size(with(density) { r.width.toDp() }, with(density) { r.height.toDp() })
                        .graphicsLayer { alpha = maxOf(fullAlpha, miniAlpha) }
                        .clip(RoundedCornerShape(corner))
                        .background(Color.Black)
                        .then(if (full) Modifier.hazeSource(glassOver) else Modifier),
                ) {
                    engine.Surface(Modifier.fillMaxSize())
                    LoadingCover(state, current, small = !full)
                }
            }
            // 3. Controls and words, over the picture.
            if (fullAlpha > 0f) Box(Modifier.fillMaxSize().graphicsLayer { alpha = fullAlpha }) {
                if (full) FullLayout(engine, state, current, actions, history, edgeToEdge, front = true, spot = fullSpot, glassOver = glassOver)
            }
            if (miniAlpha > 0f && !inDock) Box(Modifier.fillMaxSize().graphicsLayer { alpha = miniAlpha }) {
                if (mini) MiniLayout(engine, state, current, miniBottom, front = true, spot = miniSpot)
            }
        }
    }
}

/**
 * Until the first picture shows: the video's thumbnail, dimmed, with a spinner and "Loading video"
 * (so you know it's coming). When it stalls later, just the spinner. When it fails, why.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingCover(state: VideoState, target: VideoTarget, small: Boolean) {
    val starting = !state.firstFrame && state.error == null
    val stalled = state.firstFrame && state.buffering && state.error == null
    AnimatedVisibility(starting || state.error != null, Modifier.fillMaxSize(), enter = fadeIn(tween(120)), exit = fadeOut(tween(260))) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(target.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.error == null) {
                    LoadingIndicator(Modifier.size(if (small) 34.dp else 72.dp), color = Color.White)
                    if (!small) Text("Loading video", color = Color.White, style = body(15.sp, FontWeight.SemiBold))
                } else if (!small) {
                    Text(state.error, Modifier.padding(horizontal = 24.dp), color = Color.White, style = body(15.sp, FontWeight.SemiBold))
                }
            }
        }
    }
    if (stalled) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(if (small) 30.dp else 64.dp), color = Color.White) }
}

/** Full: edge to edge (phones, sideways) or beside Up next (tablets and Googlebook). */
@Composable
private fun FullLayout(engine: VideoEngine, state: VideoState, target: VideoTarget, actions: VideoActions, history: MutableList<VideoTarget>, edgeToEdge: Boolean, front: Boolean, spot: MutableState<Rect?>, glassOver: dev.chrisbanes.haze.HazeState? = null) {
    val c = MaterialTheme.colorScheme
    val back = !front
    if (edgeToEdge) {
        Box(Modifier.fillMaxSize().then(if (back) Modifier.background(Color.Black).blocksTouchesBelow() else Modifier)) {
            // The picture keeps its shape, with black bars where the screen's differs.
            Box(Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(state.width.toFloat() / state.height.coerceAtLeast(1), matchHeightConstraintsFirst = true).reportSpot(spot, back))
            if (front) Controls(engine, state, target, actions, full = true, glassOver = glassOver, modifier = Modifier.fillMaxSize())
        }
    } else {
        Row(
            Modifier.fillMaxSize().then(if (back) Modifier.background(c.background).blocksTouchesBelow() else Modifier).topBarPadding().navigationBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(Modifier.weight(1.75f).fillMaxHeight()) {
                Row(Modifier.padding(bottom = 10.dp).graphicsLayer { alpha = if (front) 1f else 0f }, verticalAlignment = Alignment.CenterVertically) {
                    CircleButton(Icons.Rounded.KeyboardArrowDown, "Shrink to the mini player", c.onBackground.copy(alpha = 0.08f), c.onBackground, 44.dp, actions.onMinimize)
                    Text("Now watching", Modifier.padding(start = 12.dp), color = c.onBackground.copy(alpha = 0.7f), style = body(15.sp, FontWeight.SemiBold))
                }
                Box(Modifier.fillMaxWidth().weight(1f, fill = false).aspectRatio(16f / 9f).reportSpot(spot, back)) {
                    if (front) Controls(engine, state, target, actions, full = false, glassOver = glassOver, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)))
                }
                if (front) Info(target, actions)
            }
            Box(Modifier.weight(1f).fillMaxHeight()) { if (front) UpNext(Modifier.fillMaxSize(), target, history) }
        }
    }
}

/** The name, the artist, and what you can do with it (big screens). */
@Composable
private fun Info(target: VideoTarget, actions: VideoActions) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 16.dp, start = 4.dp)) {
        Text(target.title, color = c.onBackground, style = display(24.sp).copy(lineHeight = 28.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(target.artist, Modifier.padding(top = 2.dp), color = c.onBackground.copy(alpha = 0.65f), style = body(15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            actions.onFullscreen?.let { Pill(Icons.Rounded.Fullscreen, "Full screen", c.primary, c.onPrimary, it) }
            Pill(Icons.Rounded.Headphones, "Listen instead", c.onBackground.copy(alpha = 0.1f), c.onBackground, actions.onListen)
            CircleButton(Icons.Rounded.Share, "Share", c.onBackground.copy(alpha = 0.1f), c.onBackground, 44.dp, actions.onShare)
        }
    }
}

@Composable
private fun UpNext(modifier: Modifier, target: VideoTarget, history: MutableList<VideoTarget>) {
    val c = MaterialTheme.colorScheme
    LazyColumn(modifier, contentPadding = PaddingValues(top = 54.dp, bottom = 24.dp)) {
        if (Video.upNext.isNotEmpty()) item { Text("Up next", Modifier.padding(horizontal = 6.dp, vertical = 6.dp), color = c.onBackground, style = display(20.sp)) }
        items(Video.upNext, key = { "n" + it.videoId }) { v ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(PrismaLook.corner(18.dp)))
                    .clickable { history += target; Video.watch(v, Video.upNext.filter { it.videoId != v.videoId } + target, keepSize = true) }
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AsyncImage(v.thumbnail, null, Modifier.width(136.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(PrismaLook.corner(14.dp))).background(c.surfaceContainerHigh), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f)) {
                    Text(v.title, color = c.onBackground, style = body(15.sp, FontWeight.SemiBold).copy(lineHeight = 19.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(v.artist, color = c.onBackground.copy(alpha = 0.6f), style = body(13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/**
 * Wave 2.0's controls over the picture: frosted glass, like the dock. A glass bar along the bottom
 * holds the name, a wave you drag through, and play (a big pill between jumps of 10 seconds); glass
 * buttons in the top corners shrink it and pick the quality. The middle stays clear for the video.
 * Tap to show or hide them (they tuck away while it plays); double-tap a side to jump 10 seconds.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Controls(engine: VideoEngine, state: VideoState, target: VideoTarget, actions: VideoActions, full: Boolean, glassOver: dev.chrisbanes.haze.HazeState?, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    var shown by remember { mutableStateOf(true) }
    var touched by remember { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(shown, state.playing, touched, menuOpen) { if (shown && state.playing && !menuOpen) { delay(3500); shown = false } }
    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(target.videoId) { while (true) { position = engine.positionMs; delay(200) } }
    var jump by remember { mutableIntStateOf(0) }
    LaunchedEffect(jump) { if (jump != 0) { delay(650); jump = 0 } }
    val visible = shown || !state.playing || menuOpen || state.error != null
    val panel = RoundedCornerShape(PrismaLook.corner(if (full) 30.dp else 24.dp))
    val glass = Modifier
        .glass(glassOver, 28.dp, Color.Black, listOf(dev.chrisbanes.haze.HazeTint(Color.Black.copy(alpha = 0.35f))), Color.Black.copy(alpha = 0.55f))
        .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
    fun Modifier.glassPanel() = clip(panel)
        .glass(glassOver, 28.dp, Color.Black, listOf(dev.chrisbanes.haze.HazeTint(Color.Black.copy(alpha = 0.35f))), Color.Black.copy(alpha = 0.55f))
        .border(1.dp, Color.White.copy(alpha = 0.12f), panel)

    var chipSpot by remember { mutableStateOf<Rect?>(null) }
    var controlsOrigin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(modifier.onGloballyPositioned { controlsOrigin = it.positionInRoot() }) {
        val w = constraints.maxWidth
        Box(
            Modifier.fillMaxSize().pointerInput(w) {
                detectTapGestures(
                    onTap = { shown = !shown; touched++ },
                    onDoubleTap = { at ->
                        val back = at.x < w / 2f
                        engine.seekTo((engine.positionMs + if (back) -10_000 else 10_000).coerceIn(0, state.durationMs.coerceAtLeast(0)))
                        jump = if (back) -1 else 1
                        touched++
                    },
                )
            },
        )
        // Jumped: a glass bubble on that side.
        AnimatedVisibility(jump != 0, Modifier.align(if (jump < 0) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 56.dp), enter = fadeIn(tween(90)) + androidx.compose.animation.scaleIn(initialScale = 0.8f), exit = fadeOut(tween(200))) {
            Row(
                Modifier.clip(CircleShape).then(glass).padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(if (jump < 0) Icons.Rounded.Replay10 else Icons.Rounded.Forward10, null, tint = Color.White)
                Text(if (jump < 0) "10 seconds back" else "10 seconds on", color = Color.White, style = body(14.sp, FontWeight.SemiBold))
            }
        }
        // Paused: a quiet play mark in the middle, so it's clear at a glance.
        AnimatedVisibility(!state.playing && state.firstFrame && !state.ended && state.error == null && !visible, Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.size(72.dp).clip(CircleShape).then(glass), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(36.dp)) }
        }

        AnimatedVisibility(visible, Modifier.fillMaxSize(), enter = fadeIn(tween(160)), exit = fadeOut(tween(260))) {
            Box(Modifier.fillMaxSize()) {
                // Top corners: shrink on the left; quality and listening on the right.
                Row(
                    Modifier.align(Alignment.TopStart).fillMaxWidth().then(if (full) Modifier.topBarPadding().padding(horizontal = 20.dp, vertical = 14.dp) else Modifier.padding(12.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(44.dp).clip(CircleShape).then(glass).clickable(onClick = actions.onMinimize), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.KeyboardArrowDown, "Shrink to the mini player", tint = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    QualityChip(state, glass, onSpot = { chipSpot = it }) { menuOpen = true }
                    if (full) Row(
                        Modifier.height(44.dp).clip(CircleShape).then(glass).clickable(onClick = actions.onListen).padding(start = 14.dp, end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Rounded.Headphones, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Text("Listen", color = Color.White, style = body(14.sp, FontWeight.SemiBold))
                    }
                }

                // The glass bar along the bottom.
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .then(if (full) Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp) else Modifier.padding(10.dp))
                        .widthIn(max = 820.dp)
                        .fillMaxWidth()
                        .glassPanel()
                        .padding(start = 18.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(formatTime(position), color = Color.White, style = body(12.sp, FontWeight.SemiBold))
                        WavyProgress(
                            progress = { if (state.durationMs > 0) (engine.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f },
                            playing = state.playing && !PrismaLook.calm,
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.25f),
                            stroke = 4.dp,
                            amplitude = 3.dp,
                            wavelength = 24.dp,
                            showThumb = true,
                            onSeek = { f -> engine.seekTo((f * state.durationMs).toLong()); touched++ },
                            modifier = Modifier.weight(1f).height(30.dp),
                        )
                        Text(formatTime(state.durationMs), color = Color.White.copy(alpha = 0.7f), style = body(12.sp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (full) Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(target.title, color = Color.White, style = display(17.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(target.artist, color = Color.White.copy(alpha = 0.7f), style = body(13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else Spacer(Modifier.weight(1f))
                        if (full) SoftButton(Icons.Rounded.SkipPrevious, "Previous video", 42.dp, actions.hasPrevious) { actions.onPrevious() }
                        SoftButton(Icons.Rounded.Replay10, "Back 10 seconds", 46.dp) { engine.seekTo((engine.positionMs - 10_000).coerceAtLeast(0)); touched++ }
                        // Play: Wave's pill, firm while playing and rounder when paused.
                        val corner by animateDpAsState(if (state.playing) 16.dp else 26.dp, WaveMotion.bouncy(), label = "videoPlayShape")
                        Box(
                            Modifier.size(width = if (full) 76.dp else 64.dp, height = 52.dp).pressable(RoundedCornerShape(PrismaLook.corner(corner)), c.primary, pressedScale = 0.92f, enabled = state.firstFrame || state.error != null) {
                                if (state.ended) engine.seekTo(0)
                                engine.togglePlay(); touched++
                            },
                            contentAlignment = Alignment.Center,
                        ) {
                            Crossfade(when { state.error != null -> 2; state.ended -> 3; !state.firstFrame -> 1; else -> 0 }, label = "videoPlay") { s ->
                                when (s) {
                                    1 -> LoadingIndicator(Modifier.size(30.dp), color = c.onPrimary)
                                    2 -> Icon(Icons.Rounded.Refresh, "Try again", tint = c.onPrimary, modifier = Modifier.size(26.dp))
                                    3 -> Icon(Icons.Rounded.Replay, "Watch again", tint = c.onPrimary, modifier = Modifier.size(26.dp))
                                    else -> PlayPauseIcon(state.playing, c.onPrimary, size = 26.dp)
                                }
                            }
                        }
                        SoftButton(Icons.Rounded.Forward10, "Forward 10 seconds", 46.dp) { engine.seekTo((engine.positionMs + 10_000).coerceAtMost(state.durationMs.coerceAtLeast(0))); touched++ }
                        if (full) SoftButton(Icons.Rounded.SkipNext, "Next video", 42.dp, actions.hasNext) { actions.onNext() }
                        // Big screens: the whole window or back beside Up next (phones are always full).
                        actions.onFullscreen?.let { toggle ->
                            Box(Modifier.padding(horizontal = 4.dp).size(width = 1.dp, height = 26.dp).background(Color.White.copy(alpha = 0.18f)))
                            SoftButton(if (full) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, if (full) "Leave full screen" else "Full screen", 42.dp, onClick = toggle)
                        }
                    }
                }
            }
        }
        QualityPanel(engine, state, glassOver, menuOpen, chipSpot, controlsOrigin) { menuOpen = false }
    }
}

/** A round button inside the glass bar: no fill of its own, a soft one when pressed. */
@Composable
private fun SoftButton(icon: ImageVector, label: String, size: Dp, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.size(size).pressable(CircleShape, Color.White.copy(alpha = 0.08f), pressedScale = 0.86f, enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Color.White.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(size * 0.54f))
    }
}

/** The quality button: Auto (with the size showing) or the size you picked. */
@Composable
private fun QualityChip(state: VideoState, glass: Modifier, onSpot: (Rect) -> Unit, onClick: () -> Unit) {
    if (state.heights.none { it >= 144 }) return
    Row(
        Modifier.height(44.dp).onGloballyPositioned { onSpot(it.boundsInRoot()) }.clip(CircleShape).then(glass).clickable(onClick = onClick).padding(start = 14.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Rounded.Tune, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(state.cap?.let { "${it}p" } ?: ("Auto" + (state.showing?.let { " · ${it}p" } ?: "")), color = Color.White, style = body(14.sp, FontWeight.SemiBold))
    }
}

/**
 * The quality dropdown: frosted glass over the video like every menu in Wave 2.0, growing out of the
 * quality button. Auto, or a size of your own.
 */
@Composable
private fun QualityPanel(engine: VideoEngine, state: VideoState, glassOver: dev.chrisbanes.haze.HazeState?, open: Boolean, anchor: Rect?, origin: Offset, onClose: () -> Unit) {
    val sizes = state.heights.filter { it >= 144 }.distinct().sortedDescending()
    fun name(h: Int) = when { h >= 2160 -> "4K"; h >= 1440 -> "1440p"; h >= 1080 -> "1080p HD"; h >= 720 -> "720p HD"; else -> "${h}p" }
    val progress by animateFloatAsState(if (open) 1f else 0f, if (open) spring(dampingRatio = 0.8f, stiffness = 520f) else tween(140), label = "quality")
    if (progress == 0f || anchor == null) return
    val c = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val shape = RoundedCornerShape(PrismaLook.corner(24.dp))
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onClose() } }) {
        val a = anchor.translate(-origin)
        Column(
            Modifier
                .offset { IntOffset((a.right - 236.dp.toPx()).roundToInt().coerceAtLeast(8.dp.roundToPx()), (a.bottom + 8.dp.toPx()).roundToInt()) }
                .width(236.dp)
                .graphicsLayer {
                    alpha = progress.coerceIn(0f, 1f)
                    scaleX = 0.85f + 0.15f * progress
                    scaleY = 0.7f + 0.3f * progress
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)
                    shadowElevation = 16.dp.toPx() * progress
                    this.shape = shape
                    clip = true
                }
                .glass(glassOver, 28.dp, c.background, listOf(dev.chrisbanes.haze.HazeTint(c.surfaceContainerHigh.copy(alpha = 0.62f))), c.surfaceContainerHigh)
                .background(Color.White.copy(alpha = 0.04f))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(vertical = 6.dp),
        ) {
            Text("Quality", Modifier.padding(start = 18.dp, top = 8.dp, bottom = 4.dp), color = c.onSurface.copy(alpha = 0.6f), style = body(12.sp, FontWeight.Bold))
            (listOf<Int?>(null) + sizes).forEach { h ->
                val on = h == state.cap
                Row(
                    Modifier.fillMaxWidth().clickable { engine.setQuality(h); onClose() }.padding(horizontal = 18.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(if (h == null) "Auto" else name(h), color = c.onSurface, style = body(15.sp, if (on) FontWeight.Bold else FontWeight.Medium))
                        if (h == null) Text("Best for your connection", color = c.onSurface.copy(alpha = 0.6f), style = body(12.sp))
                    }
                    if (on) Icon(Icons.Rounded.Check, null, tint = c.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/**
 * The mini player: like the music one, but the video keeps playing in it, with a VIDEO tag and an
 * X to stop. Tap it to watch full again.
 */
@Composable
private fun MiniLayout(engine: VideoEngine, state: VideoState, target: VideoTarget, bottom: Dp, front: Boolean, spot: MutableState<Rect?>) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(PrismaLook.corner(28.dp))
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = 10.dp, end = 10.dp, bottom = bottom)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .then(if (front) Modifier.clip(shape).clickable { Video.minimized = false } else Modifier.shadow(16.dp, shape).clip(shape).background(c.surfaceContainerHigh)),
        ) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(112.dp).aspectRatio(16f / 9f).reportSpot(spot, !front))
                Column(Modifier.weight(1f).graphicsLayer { alpha = if (front) 1f else 0f }) {
                    Text(target.title, color = c.onSurface, style = body(15.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(target.artist, color = c.onSurface.copy(alpha = 0.65f), style = body(13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.graphicsLayer { alpha = if (front) 1f else 0f }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val corner by animateDpAsState(if (state.playing) 14.dp else 23.dp, WaveMotion.bouncy(), label = "miniVideoPlay")
                        Box(
                            Modifier.size(46.dp).pressable(RoundedCornerShape(PrismaLook.corner(corner)), c.primary, pressedScale = 0.88f, enabled = front) { engine.togglePlay() },
                            contentAlignment = Alignment.Center,
                        ) { PlayPauseIcon(state.playing, c.onPrimary, size = 24.dp) }
                        Box(
                            Modifier.size(40.dp).pressable(CircleShape, c.onSurface.copy(alpha = 0.08f), pressedScale = 0.86f, enabled = front, onClick = ::close),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Close, "Stop the video", tint = c.onSurface, modifier = Modifier.size(20.dp)) }
                    }
                }
            }
            var position by remember { mutableLongStateOf(0L) }
            LaunchedEffect(target.videoId, front) { while (front) { position = engine.positionMs; delay(500) } }
            Box(Modifier.fillMaxWidth().height(3.dp).graphicsLayer { alpha = if (front) 1f else 0f }.background(c.onSurface.copy(alpha = 0.1f))) {
                Box(Modifier.fillMaxWidth(if (state.durationMs > 0) (position.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f).height(3.dp).background(c.primary))
            }
        }
    }
}

@Composable
private fun CircleButton(icon: ImageVector, label: String, fill: Color, tint: Color, size: Dp, onClick: () -> Unit) {
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
        Text(label, color = ink, style = body(14.sp, FontWeight.SemiBold))
    }
}
