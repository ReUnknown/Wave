package app.wave.music.ui.video

import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.MoreVert
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

/** Watches a song's music video, from the start (the song and its video are separate). */
fun watchSongVideo(songId: String, videoId: String, title: String, artist: String) {
    Video.watch(VideoTarget(videoId, title, artist, Video.thumbnail(videoId), songId = songId))
}

/** From a video, the song itself: found by name and played as music, from the start. */
private suspend fun playTheSong(target: VideoTarget) {
    val name = target.title.replace(Regex("\\s*[(\\[](official|music|lyric|video|audio|visuali[sz]er|4k|hd)[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE), "").trim()
    val song = target.songId?.let { id -> SongItem(id = id, title = name, artists = listOf(com.metrolist.innertube.models.Artist(target.artist, null)), thumbnail = "") }
        ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.metrolist.innertube.YouTube.search("$name ${target.artist}", com.metrolist.innertube.YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items?.filterIsInstance<SongItem>()?.firstOrNull()
        }
    if (song == null) { app.wave.music.ui.components.Notices.show("Couldn't find the song for this video"); return }
    close()
    Video.playSong(song)
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
    Video.watch(next, Video.upNext.drop(1) + target)
}

private fun playPrevious(target: VideoTarget, history: MutableList<VideoTarget>) {
    val prev = history.removeLastOrNull() ?: return
    Video.watch(prev, listOf(target) + Video.upNext)
}

private class VideoActions(
    val onClose: () -> Unit,
    /** Sideways: back to the upright player (phones), or out of the whole window (big screens). */
    val onUpright: () -> Unit,
    /** Turn it sideways / the whole window. */
    val onFullscreen: () -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
    /** The ⋮: the song itself, and sharing. */
    val onMenu: (Rect) -> Unit,
    /** What's next, in a frosted panel. */
    val onUpNext: () -> Unit,
)

/** Where the picture should be, reported by the layout showing it (window pixels). */
private fun Modifier.reportSpot(spot: MutableState<Rect?>, enabled: Boolean): Modifier =
    if (!enabled) this else onGloballyPositioned { spot.value = it.boundsInRoot() }

/** A glass menu that's open: which one, and where it grows from. */
private class OpenMenu(val kind: String, val anchor: Rect)

/** Renders only: a menu to show open. */
object VideoRender {
    var menu: Pair<String, Rect>? = null
    var upNext = false
}

/**
 * The video player, over everything, while a video is open. Upright, the video sits at the top
 * (never cut off) with the controls under it the way the music player has them; turn the phone
 * sideways and the video fills the screen under frosted glass controls. Closing it stops it (there's
 * no mini player for videos).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VideoPlayerHost() {
    val target = Video.open
    var shown by remember { mutableStateOf(target) }
    if (target != null) shown = target
    val engine = Video.engine
    val window = currentWindowDp()
    val computer = WavePlatform.current.isComputer
    val landscapePhone = window.screenWidthDp > window.screenHeightDp && minOf(window.screenWidthDp, window.screenHeightDp) < 600 && !computer
    val wideRoom = !landscapePhone && window.screenWidthDp >= 840 && window.screenWidthDp > window.screenHeightDp
    var fullscreenAsked by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val edgeToEdge = target != null && (landscapePhone || fullscreenAsked)
    LaunchedEffect(target == null) { if (target == null) fullscreenAsked = false else engine?.holdOrientation(0) }
    LaunchedEffect(edgeToEdge) { engine?.setBarsHidden(edgeToEdge) }
    fun upright() {
        if (fullscreenAsked) fullscreenAsked = false
        if (landscapePhone || !computer) scope.launch {
            // Turn back upright, then follow the phone again (so turning it sideways goes full screen).
            engine?.holdOrientation(2)
            delay(2500)
            if (Video.open != null) engine?.holdOrientation(0)
        }
    }
    androidx.compose.ui.backhandler.BackHandler(enabled = target != null) { if (edgeToEdge) upright() else close() }

    androidx.compose.animation.AnimatedVisibility(
        visible = target != null,
        enter = androidx.compose.animation.slideInVertically(spring(dampingRatio = 0.86f, stiffness = 420f)) { it / 3 } + fadeIn(tween(200)) + androidx.compose.animation.scaleIn(spring(dampingRatio = 0.86f, stiffness = 420f), initialScale = 0.94f),
        exit = androidx.compose.animation.slideOutVertically(tween(260, easing = WaveMotion.EmphasizedAccelerate)) { it / 3 } + fadeOut(tween(200)),
    ) {
        val current = shown ?: return@AnimatedVisibility
        MaterialTheme(colorScheme = rememberArtColorScheme(current.thumbnail, base = app.wave.music.ui.theme.darkBaseScheme())) {
            val c = MaterialTheme.colorScheme
            if (engine == null) {
                Box(Modifier.fillMaxSize().blocksTouchesBelow().background(c.background).clickable { close() }, contentAlignment = Alignment.Center) {
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
                androidx.compose.runtime.snapshotFlow { PlayerConnection.state.value.isPlaying }.drop(1)
                    .collect { playing -> if (playing && Video.open != null) close() }
            }
            LaunchedEffect(state.ended) { if (state.ended && Video.upNext.isNotEmpty() && target != null) { history += target; playNext(target) } }

            var menu by remember { mutableStateOf(VideoRender.menu?.let { OpenMenu(it.first, it.second) }) }
            var upNextOpen by remember { mutableStateOf(VideoRender.upNext) }
            androidx.compose.ui.backhandler.BackHandler(enabled = upNextOpen) { upNextOpen = false }
            val actions = VideoActions(
                onClose = ::close,
                onUpright = ::upright,
                onFullscreen = { fullscreenAsked = true; if (!computer && window.screenWidthDp < window.screenHeightDp) engine.holdOrientation(1) },
                onNext = { history += current; playNext(current) },
                onPrevious = { playPrevious(current, history) },
                hasPrevious = history.isNotEmpty(),
                hasNext = Video.upNext.isNotEmpty(),
                onMenu = { menu = OpenMenu("more", it) },
                onUpNext = { upNextOpen = true },
            )
            val glassOver = dev.chrisbanes.haze.rememberHazeState()
            val spot = remember { mutableStateOf<Rect?>(null) }
            var origin by remember { mutableStateOf(Offset.Zero) }
            val layout = when { edgeToEdge -> "edge"; wideRoom -> "wide"; else -> "upright" }
            @Composable
            fun Layer(front: Boolean) {
                when (layout) {
                    "upright" -> UprightLayout(engine, state, current, actions, history, front, spot, onMenu = { kind, at -> menu = OpenMenu(kind, at) })
                    else -> FullLayout(engine, state, current, actions, history, edgeToEdge, front, spot, glassOver, onMenu = { kind, at -> menu = OpenMenu(kind, at) })
                }
            }
            // Menus blur everything under them: the player is their glass's source.
            val menuGlass = dev.chrisbanes.haze.rememberHazeState()
            Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
              Box(Modifier.fillMaxSize().hazeSource(menuGlass)) {
                // 1. The background, with a hole where the video goes.
                Layer(front = false)
                // 2. The video, in that hole.
                spot.value?.translate(-origin)?.let { r ->
                    val density = LocalDensity.current
                    Box(
                        Modifier
                            .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                            .size(with(density) { r.width.toDp() }, with(density) { r.height.toDp() })
                            .clip(RoundedCornerShape(if (layout == "edge") 0.dp else if (layout == "wide") 24.dp else 0.dp))
                            .background(Color.Black)
                            .hazeSource(glassOver),
                    ) {
                        engine.Surface(Modifier.fillMaxSize())
                        LoadingCover(state, current, small = false)
                    }
                }
                // 3. Controls and words.
                Layer(front = true)
              }
                // 4. What's next, sliding up (or in from the side when it's sideways).
                UpNextPanel(menuGlass, upNextOpen, sideways = layout != "upright", current = current, onClose = { upNextOpen = false }) { v ->
                    upNextOpen = false
                    history += current
                    Video.watch(v, Video.upNext.filter { it.videoId != v.videoId } + current)
                }
                // 5. A glass menu, when one's open.
                menu?.let { m ->
                    GlassMenu(menuGlass, m.anchor, origin, onClose = { menu = null }, title = when (m.kind) { "quality" -> "Quality"; "speed" -> "Speed"; else -> null }) {
                        when (m.kind) {
                            "quality" -> {
                                val sizes = state.heights.filter { it >= 144 }.distinct().sortedDescending()
                                fun name(h: Int) = when { h >= 2160 -> "4K"; h >= 1440 -> "1440p"; h >= 1080 -> "1080p HD"; h >= 720 -> "720p HD"; else -> "${h}p" }
                                MenuRow("Auto", "Best for your connection", state.cap == null) { engine.setQuality(null); menu = null }
                                sizes.forEach { h -> MenuRow(name(h), null, state.cap == h) { engine.setQuality(h); menu = null } }
                            }
                            "speed" -> listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { v ->
                                MenuRow(if (v == 1f) "Normal" else "${v.toString().removeSuffix(".0")}×", null, state.speed == v) { engine.setSpeed(v); menu = null }
                            }
                            else -> {
                                MenuRow("Play the song", "Just the music, from the start", false, icon = Icons.Rounded.Headphones) { menu = null; scope.launch { playTheSong(current) } }
                                MenuRow("Share", null, false, icon = Icons.Rounded.Share) { menu = null; WavePlatform.current.share(current.title, "https://music.youtube.com/watch?v=${current.videoId}") }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun speedName(v: Float) = if (v == 1f) "1×" else "${v.toString().removeSuffix(".0")}×"

/**
 * Upright: laid out exactly like the music player. The same round buttons at the top, the video in
 * the art's place (its whole picture, centered, over its own blurred colors), and at the bottom the
 * same controls with the same spacing: name (full screen where Like is), the wave and times,
 * previous · play · next, two labeled buttons (Quality and Speed where Shuffle and Repeat are),
 * and the frosted tray (Up next, and Full screen where Lyrics is).
 */
@Composable
private fun UprightLayout(engine: VideoEngine, state: VideoState, target: VideoTarget, actions: VideoActions, history: MutableList<VideoTarget>, front: Boolean, spot: MutableState<Rect?>, onMenu: (String, Rect) -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        if (!front) {
            // The video's picture, blurred, filling the screen like the music player's art.
            Box(Modifier.fillMaxSize().blocksTouchesBelow().background(c.background))
            app.wave.music.ui.components.BlurredImage(target.thumbnail, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.4f; scaleY = 1.4f }, radius = 72.dp)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.4f),
                        0.1f to Color.Transparent,
                        0.44f to Color.Transparent,
                        0.6f to c.background.copy(alpha = 0.32f),
                        0.74f to c.background.copy(alpha = 0.6f),
                        1f to c.background.copy(alpha = 0.85f),
                    ),
                ),
            )
        }
        Column(Modifier.fillMaxSize()) {
            // The header: the music player's, button for button.
            Row(Modifier.topBarPadding().padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().graphicsLayer { alpha = if (front) 1f else 0f }) {
                GlassButton(Icons.Rounded.KeyboardArrowDown, "Close the video", actions.onClose)
                Spacer(Modifier.weight(1f))
                var at by remember { mutableStateOf(Rect.Zero) }
                Box(Modifier.onGloballyPositioned { at = it.boundsInRoot() }) { GlassButton(Icons.Rounded.MoreVert, "More options") { actions.onMenu(at) } }
            }
            // Where the art is: the video, centered, its whole picture.
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val ratio = (state.width.toFloat() / state.height.coerceAtLeast(1)).coerceIn(0.5f, 2.4f)
                Box(Modifier.fillMaxWidth().aspectRatio(ratio).heightIn(max = maxHeight).reportSpot(spot, !front)) {
                    if (front) VideoTapLayer(engine, state, actions)
                }
            }
            if (front) {
                UprightControls(engine, state, target, actions, onMenu, Modifier.padding(horizontal = 20.dp))
                VideoTray(actions)
            } else {
                // Same room as the controls and tray, so the video sits in the same place.
                Box(Modifier.alpha0()) { UprightControls(engine, state, target, actions, onMenu, Modifier.padding(horizontal = 20.dp)); }
                Box(Modifier.alpha0()) { VideoTray(actions) }
            }
        }
    }
}

private fun Modifier.alpha0() = graphicsLayer { alpha = 0f }

/** The music player's round glass button (top corners). */
@Composable
private fun GlassButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).pressable(CircleShape, Color.Black.copy(alpha = 0.32f), pressedScale = 0.86f, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Color.White)
    }
}

/** The music player's controls, measure for measure. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UprightControls(engine: VideoEngine, state: VideoState, target: VideoTarget, actions: VideoActions, onMenu: (String, Rect) -> Unit, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(target.videoId) { while (true) { position = engine.positionMs; delay(250) } }
    val shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.35f), Offset(0f, 2f), 12f)
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(target.title, color = c.onBackground, style = display(28.sp).copy(lineHeight = 33.sp, letterSpacing = (-0.4).sp, shadow = shadow), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(target.artist, color = c.onBackground.copy(alpha = 0.7f), style = body(16.sp, FontWeight.Medium).copy(shadow = shadow), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // Where the music player has Like: full screen.
            Box(Modifier.size(48.dp).pressable(RoundedCornerShape(24.dp), c.onBackground.copy(alpha = 0.1f), pressedScale = 0.88f, onClick = actions.onFullscreen), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Fullscreen, "Full screen", tint = c.onBackground)
            }
        }
        Spacer(Modifier.height(16.dp))
        WavyProgress(
            progress = { if (state.durationMs > 0) (engine.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f },
            playing = state.playing && !PrismaLook.calm,
            color = c.onBackground,
            trackColor = c.onBackground.copy(alpha = 0.2f),
            stroke = 4.dp,
            amplitude = 3.5.dp,
            wavelength = 24.dp,
            showThumb = true,
            onSeek = { f -> engine.seekTo((f * state.durationMs).toLong()) },
            modifier = Modifier.fillMaxWidth().height(32.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position), color = c.onBackground.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("−" + formatTime((state.durationMs - position).coerceAtLeast(0)), color = c.onBackground.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        // Previous · play · next: glass circles around a big play pill.
        Row(Modifier.padding(top = 14.dp).fillMaxWidth().height(80.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RowCircle(Icons.Rounded.SkipPrevious, "Previous video", 80.dp, actions.hasPrevious, actions.onPrevious)
            val corner by animateDpAsState(if (state.playing) 26.dp else 40.dp, WaveMotion.bouncy(), label = "videoPlayShape")
            Box(
                Modifier.weight(1f).fillMaxSize().pressable(RoundedCornerShape(corner), c.primary, pressedScale = 0.94f, enabled = state.firstFrame || state.error != null) {
                    if (state.ended) engine.seekTo(0)
                    engine.togglePlay()
                },
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(when { state.error != null -> 2; state.ended -> 3; !state.firstFrame -> 1; else -> 0 }, label = "videoPlay") { s ->
                    when (s) {
                        1 -> LoadingIndicator(Modifier.size(44.dp), color = c.onPrimary)
                        2 -> Icon(Icons.Rounded.Refresh, "Try again", tint = c.onPrimary, modifier = Modifier.size(38.dp))
                        3 -> Icon(Icons.Rounded.Replay, "Watch again", tint = c.onPrimary, modifier = Modifier.size(38.dp))
                        else -> PlayPauseIcon(state.playing, c.onPrimary, size = 42.dp)
                    }
                }
            }
            RowCircle(Icons.Rounded.SkipNext, "Next video", 80.dp, actions.hasNext, actions.onNext)
        }
        // Where Shuffle and Repeat are: Quality and Speed.
        Row(Modifier.padding(top = 10.dp).fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val quality = state.cap?.let { "${it}p" } ?: ("Auto" + (state.showing?.let { " · ${it}p" } ?: ""))
            ModeChip(Icons.Rounded.Tune, quality, Modifier.weight(1f)) { onMenu("quality", it) }
            ModeChip(Icons.Rounded.Speed, if (state.speed == 1f) "Normal speed" else speedName(state.speed), Modifier.weight(1f), on = state.speed != 1f) { onMenu("speed", it) }
        }
    }
}

/** The music player's frosted tray: Up next (what's coming), and Full screen where Lyrics is. */
@Composable
private fun VideoTray(actions: VideoActions) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(androidx.compose.ui.graphics.lerp(Color.Black, c.primary, 0.12f).copy(alpha = 0.6f))
            .navigationBarsPadding(),
    ) {
        Box(Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(c.onBackground.copy(alpha = 0.3f)))
        Row(Modifier.heightIn(min = 68.dp).padding(start = 12.dp, end = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val next = Video.upNext.firstOrNull()
            Row(
                Modifier.weight(1f).pressable(RoundedCornerShape(20.dp), pressedScale = 0.96f, enabled = next != null, onClick = actions.onUpNext).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (next != null) AsyncImage(next.thumbnail, null, Modifier.size(width = 48.dp, height = 36.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                else Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = c.onBackground)
                Column {
                    Text("Up next", color = c.onBackground, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(next?.let { "${it.title} · ${it.artist}" } ?: "Finding more videos…", color = c.onBackground.copy(alpha = 0.65f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.width(1.dp).height(32.dp).background(c.onBackground.copy(alpha = 0.16f)))
            Row(
                Modifier.width(150.dp).pressable(RoundedCornerShape(20.dp), pressedScale = 0.94f, onClick = actions.onFullscreen).padding(10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Fullscreen, null, tint = c.onBackground)
                Text("  Full screen", color = c.onBackground, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun RowCircle(icon: ImageVector, label: String, size: Dp, enabled: Boolean = true, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.size(size).pressable(CircleShape, c.onBackground.copy(alpha = 0.1f), pressedScale = 0.86f, enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = c.onBackground.copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(size * 0.5f))
    }
}

/**
 * What's next, in a frosted panel: up from the bottom when upright, in from the right when sideways.
 * Tap one to watch it.
 */
@Composable
private fun UpNextPanel(glass: dev.chrisbanes.haze.HazeState?, open: Boolean, sideways: Boolean, current: VideoTarget, onClose: () -> Unit, onPick: (VideoTarget) -> Unit) {
    val c = MaterialTheme.colorScheme
    val p by animateFloatAsState(if (open) 1f else 0f, if (open) spring(dampingRatio = 0.86f, stiffness = 380f) else tween(220), label = "upNext")
    if (p == 0f) return
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = p }.background(Color.Black.copy(alpha = 0.35f)).pointerInput(Unit) { detectTapGestures { onClose() } })
        val shape = if (sideways) RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp) else RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        Column(
            Modifier
                .align(if (sideways) Alignment.CenterEnd else Alignment.BottomCenter)
                .then(if (sideways) Modifier.fillMaxHeight().width(380.dp) else Modifier.fillMaxWidth().fillMaxHeight(0.62f))
                .graphicsLayer {
                    if (sideways) translationX = (1f - p) * size.width else translationY = (1f - p) * size.height
                    this.shape = shape
                    clip = true
                }
                .glass(glass, 30.dp, c.background, listOf(dev.chrisbanes.haze.HazeTint(c.surfaceContainer.copy(alpha = 0.7f))), c.surfaceContainerHigh)
                .pointerInput(Unit) { detectTapGestures { } }
                .then(if (sideways) Modifier.padding(top = 8.dp) else Modifier.navigationBarsPadding()),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Up next", Modifier.weight(1f), color = c.onSurface, style = display(22.sp))
                CircleButton(if (sideways) Icons.Rounded.Close else Icons.Rounded.KeyboardArrowDown, "Close Up next", c.onSurface.copy(alpha = 0.08f), c.onSurface, 40.dp, onClose)
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 20.dp)) {
                items(Video.upNext, key = { "n" + it.videoId }) { v -> UpNextRow(v) { onPick(v) } }
            }
        }
    }
}

/** A labeled button like the music player's Shuffle and Repeat; reports where it is for its menu. */
@Composable
private fun ModeChip(icon: ImageVector, label: String, modifier: Modifier, on: Boolean = false, onClick: (Rect) -> Unit) {
    val c = MaterialTheme.colorScheme
    var at by remember { mutableStateOf(Rect.Zero) }
    val fill = if (on) c.primary else c.onBackground.copy(alpha = 0.08f)
    val ink = if (on) c.onPrimary else c.onBackground.copy(alpha = 0.85f)
    Row(
        modifier.fillMaxHeight().onGloballyPositioned { at = it.boundsInRoot() }.pressable(RoundedCornerShape(PrismaLook.corner(22.dp)), fill, pressedScale = 0.95f) { onClick(at) },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = ink, modifier = Modifier.size(20.dp))
        Text("  $label", color = ink, style = body(14.sp, FontWeight.SemiBold), maxLines = 1)
    }
}

/** On the video itself (upright): double-tap a side to jump 10 seconds, tap to play or pause. */
@Composable
private fun VideoTapLayer(engine: VideoEngine, state: VideoState, actions: VideoActions) {
    var jump by remember { mutableIntStateOf(0) }
    LaunchedEffect(jump) { if (jump != 0) { delay(650); jump = 0 } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth
        Box(Modifier.fillMaxSize().pointerInput(w) {
            detectTapGestures(
                onTap = { if (state.firstFrame) engine.togglePlay() },
                onDoubleTap = { at ->
                    val back = at.x < w / 2f
                    engine.seekTo((engine.positionMs + if (back) -10_000 else 10_000).coerceIn(0, state.durationMs.coerceAtLeast(0)))
                    jump = if (back) -1 else 1
                },
            )
        })
        AnimatedVisibility(jump != 0, Modifier.align(if (jump < 0) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = 28.dp), enter = fadeIn(tween(90)), exit = fadeOut(tween(200))) {
            Row(Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(if (jump < 0) Icons.Rounded.Replay10 else Icons.Rounded.Forward10, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Text(if (jump < 0) "10 s back" else "10 s on", color = Color.White, style = body(13.sp, FontWeight.SemiBold))
            }
        }
        AnimatedVisibility(!state.playing && state.firstFrame && !state.ended, Modifier.align(Alignment.Center), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
        }
    }
}

@Composable
private fun UpNextRow(v: VideoTarget, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(PrismaLook.corner(18.dp))).clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(v.thumbnail, null, Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(PrismaLook.corner(14.dp))).background(c.surfaceContainerHigh), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f)) {
            Text(v.title, color = c.onBackground, style = body(15.sp, FontWeight.SemiBold).copy(lineHeight = 19.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(v.artist, color = c.onBackground.copy(alpha = 0.6f), style = body(13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A frosted glass menu over the video, like every menu in Wave 2.0, growing out of its button. */
@Composable
private fun GlassMenu(glassOver: dev.chrisbanes.haze.HazeState?, anchor: Rect, origin: Offset, onClose: () -> Unit, title: String?, content: @Composable () -> Unit) {
    val c = MaterialTheme.colorScheme
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 520f)) }
    val shape = RoundedCornerShape(PrismaLook.corner(24.dp))
    androidx.compose.ui.layout.Layout(
        content = {
            Column(
                Modifier
                    .width(240.dp)
                    .graphicsLayer {
                        val p = progress.value
                        alpha = p.coerceIn(0f, 1f)
                        scaleX = 0.85f + 0.15f * p
                        scaleY = 0.7f + 0.3f * p
                        shadowElevation = 16.dp.toPx() * p
                        this.shape = shape
                        clip = true
                    }
                    .glass(glassOver, 28.dp, c.background, listOf(dev.chrisbanes.haze.HazeTint(c.surfaceContainerHigh.copy(alpha = 0.62f))), c.surfaceContainerHigh)
                    .background(Color.White.copy(alpha = 0.04f))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(vertical = 6.dp),
            ) {
                title?.let { Text(it, Modifier.padding(start = 18.dp, top = 8.dp, bottom = 4.dp), color = c.onSurface.copy(alpha = 0.6f), style = body(12.sp, FontWeight.Bold)) }
                content()
            }
        },
        modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onClose() } },
    ) { measurables, constraints ->
        val p = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val a = anchor.translate(-origin)
        val margin = 12.dp.roundToPx()
        val x = (if (a.center.x > constraints.maxWidth / 2f) a.right - p.width else a.left).roundToInt().coerceIn(margin, (constraints.maxWidth - p.width - margin).coerceAtLeast(margin))
        val below = a.bottom + 8.dp.toPx() + p.height < constraints.maxHeight - margin
        val y = (if (below) a.bottom + 8.dp.toPx() else a.top - 8.dp.toPx() - p.height).roundToInt().coerceAtLeast(margin)
        layout(constraints.maxWidth, constraints.maxHeight) { p.place(x, y) }
    }
}

@Composable
private fun MenuRow(label: String, sub: String?, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        icon?.let { Icon(it, null, tint = c.onSurface, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f)) {
            Text(label, color = c.onSurface, style = body(15.sp, if (selected) FontWeight.Bold else FontWeight.Medium))
            sub?.let { Text(it, color = c.onSurface.copy(alpha = 0.6f), style = body(12.sp)) }
        }
        if (selected) Icon(Icons.Rounded.Check, null, tint = c.primary, modifier = Modifier.size(20.dp))
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
private fun FullLayout(engine: VideoEngine, state: VideoState, target: VideoTarget, actions: VideoActions, history: MutableList<VideoTarget>, edgeToEdge: Boolean, front: Boolean, spot: MutableState<Rect?>, glassOver: dev.chrisbanes.haze.HazeState? = null, onMenu: (String, Rect) -> Unit) {
    val c = MaterialTheme.colorScheme
    val back = !front
    if (edgeToEdge) {
        Box(Modifier.fillMaxSize().then(if (back) Modifier.background(Color.Black).blocksTouchesBelow() else Modifier)) {
            // The picture keeps its shape, with black bars where the screen's differs.
            Box(Modifier.align(Alignment.Center).fillMaxWidth().aspectRatio(state.width.toFloat() / state.height.coerceAtLeast(1), matchHeightConstraintsFirst = true).reportSpot(spot, back))
            if (front) Controls(engine, state, target, actions, full = true, glassOver = glassOver, onMenu = onMenu, modifier = Modifier.fillMaxSize())
        }
    } else {
        Row(
            Modifier.fillMaxSize().then(if (back) Modifier.background(c.background).blocksTouchesBelow() else Modifier).topBarPadding().navigationBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(Modifier.weight(1.75f).fillMaxHeight()) {
                Row(Modifier.padding(bottom = 10.dp).graphicsLayer { alpha = if (front) 1f else 0f }, verticalAlignment = Alignment.CenterVertically) {
                    CircleButton(Icons.Rounded.KeyboardArrowDown, "Close the video", c.onBackground.copy(alpha = 0.08f), c.onBackground, 44.dp, actions.onClose)
                    Text("Now watching", Modifier.padding(start = 12.dp), color = c.onBackground.copy(alpha = 0.7f), style = body(15.sp, FontWeight.SemiBold))
                }
                Box(Modifier.fillMaxWidth().weight(1f, fill = false).aspectRatio(16f / 9f).reportSpot(spot, back)) {
                    if (front) Controls(engine, state, target, actions, full = false, glassOver = glassOver, onMenu = onMenu, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)))
                }
                if (front) Info(target, actions, onMenu)
            }
            Box(Modifier.weight(1f).fillMaxHeight()) { if (front) UpNext(Modifier.fillMaxSize(), target, history) }
        }
    }
}

/** The name, the artist, and what you can do with it (big screens). */
@Composable
private fun Info(target: VideoTarget, actions: VideoActions, onMenu: (String, Rect) -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 16.dp, start = 4.dp)) {
        Text(target.title, color = c.onBackground, style = display(24.sp).copy(lineHeight = 28.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(target.artist, Modifier.padding(top = 2.dp), color = c.onBackground.copy(alpha = 0.65f), style = body(15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill(Icons.Rounded.Fullscreen, "Full screen", c.primary, c.onPrimary, actions.onFullscreen)
            var at by remember { mutableStateOf(Rect.Zero) }
            Box(Modifier.onGloballyPositioned { at = it.boundsInRoot() }) { CircleButton(Icons.Rounded.MoreVert, "More options", c.onBackground.copy(alpha = 0.1f), c.onBackground, 44.dp) { actions.onMenu(at) } }
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
private fun Controls(engine: VideoEngine, state: VideoState, target: VideoTarget, actions: VideoActions, full: Boolean, glassOver: dev.chrisbanes.haze.HazeState?, onMenu: (String, Rect) -> Unit, modifier: Modifier) {
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
                    // Sideways: close the video, or back to the upright player (also at the bottom right).
                    if (full) {
                        Box(Modifier.size(44.dp).clip(CircleShape).then(glass).clickable(onClick = actions.onClose), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Close, "Close the video", tint = Color.White)
                        }
                        Box(Modifier.size(44.dp).clip(CircleShape).then(glass).clickable(onClick = actions.onUpright), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.FullscreenExit, "Leave full screen", tint = Color.White)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    QualityChip(state, glass, onSpot = { chipSpot = it }) { chipSpot?.let { onMenu("quality", it) } }
                    var speedAt by remember { mutableStateOf(Rect.Zero) }
                    Row(
                        Modifier.height(44.dp).onGloballyPositioned { speedAt = it.boundsInRoot() }.clip(CircleShape).then(glass).clickable { onMenu("speed", speedAt) }.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Rounded.Speed, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text(speedName(state.speed), color = Color.White, style = body(14.sp, FontWeight.SemiBold))
                    }
                    if (full) Row(
                        Modifier.height(44.dp).clip(CircleShape).then(glass).clickable(onClick = actions.onUpNext).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text("Up next", color = Color.White, style = body(14.sp, FontWeight.SemiBold))
                    }
                    var moreAt by remember { mutableStateOf(Rect.Zero) }
                    Box(Modifier.size(44.dp).onGloballyPositioned { moreAt = it.boundsInRoot() }.clip(CircleShape).then(glass).clickable { actions.onMenu(moreAt) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.MoreVert, "More options", tint = Color.White)
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
                        Box(Modifier.padding(horizontal = 4.dp).size(width = 1.dp, height = 26.dp).background(Color.White.copy(alpha = 0.18f)))
                        SoftButton(if (full) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, if (full) "Leave full screen" else "Full screen", 42.dp, onClick = if (full) actions.onUpright else actions.onFullscreen)
                    }
                }
            }
        }
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
