package app.wave.music.ui.video

import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Settings
import app.wave.music.data.WaveSettings
import app.wave.music.ui.components.backGesture
import androidx.compose.animation.core.animateFloat
import sh.calvin.reorderable.ReorderableItem
import androidx.compose.material.icons.rounded.DragIndicator
import app.wave.music.ui.player.revealDrag
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
import app.wave.music.ui.i18n.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import app.wave.music.ui.i18n.Text
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
                    item(key = "hero") {
                        LaunchedEffect(hero.id) { Video.engine?.prefetch(hero.id) }
                        HeroVideo(hero) { watchFrom(hero, list.first().videos) }
                    }
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
    Video.watch(next, Video.upNext.drop(1) + target, keepSize = true)
}

private fun playPrevious(target: VideoTarget, history: MutableList<VideoTarget>) {
    val prev = history.removeLastOrNull() ?: return
    Video.watch(prev, listOf(target) + Video.upNext, keepSize = true)
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
    var volume = false
}

/** Where the dock's song row wants the video's picture (the dock is the video's mini player). */
object VideoDock {
    val spot = mutableStateOf<Rect?>(null)
}

/**
 * The video player while a video is open. Full: upright, the video at the top with the controls
 * under it like the music player; turn the phone and it fills the screen under glass controls.
 * Shrunk (⌄, the X, or Back): it moves into the dock like a song, still playing, and the dock's row
 * opens it again (swipe it down to stop). One picture the whole time, gliding between the two.
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
    val full = target != null && !Video.minimized
    val edgeToEdge = full && (landscapePhone || fullscreenAsked)
    LaunchedEffect(target == null) { if (target == null) fullscreenAsked = false }
    LaunchedEffect(full) { if (full) engine?.holdOrientation(0) }
    LaunchedEffect(edgeToEdge) { engine?.setBarsHidden(edgeToEdge) }
    /** Back upright (and then follow the phone again, so turning it goes full screen). */
    fun turnUpright() {
        if (landscapePhone || fullscreenAsked) scope.launch {
            engine?.holdOrientation(2)
            delay(2500)
            engine?.holdOrientation(0)
        }
        fullscreenAsked = false
    }
    fun minimize() {
        turnUpright()
        Video.minimized = true
    }

    val current = shown ?: return
    if (engine == null) {
        if (target != null) MaterialTheme(colorScheme = app.wave.music.ui.theme.darkBaseScheme()) {
            Box(Modifier.fillMaxSize().blocksTouchesBelow().background(MaterialTheme.colorScheme.background).clickable { close() }, contentAlignment = Alignment.Center) {
                Text("Videos play in the Android app.", color = MaterialTheme.colorScheme.onBackground, style = body(16.sp))
            }
        }
        return
    }
    androidx.compose.runtime.SideEffect { Video.closer = ::close }
    val state by engine.state.collectAsState()
    val history = remember { mutableListOf<VideoTarget>() }
    LaunchedEffect(target?.videoId) {
        val t = target ?: return@LaunchedEffect
        // One player: a video takes over from the music (it doesn't wait paused underneath).
        if (PlayerConnection.state.value.current != null) PlayerConnection.reset()
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
    // Watched: into Recently played as a video of its own (tapping it there plays the video).
    LaunchedEffect(target?.videoId) {
        val t = target ?: return@LaunchedEffect
        app.wave.music.data.RecentPlays.addVideo(t.videoId, t.title, t.artist, 0)
    }
    // Once it's playing, look up what's next so it starts right away.
    LaunchedEffect(state.firstFrame, Video.upNext.firstOrNull()?.videoId) {
        if (state.firstFrame) Video.upNext.take(2).forEach { engine.prefetch(it.videoId) }
    }
    LaunchedEffect(state.ended) { if (state.ended && Video.upNext.isNotEmpty() && target != null) { history += target; playNext(target) } }

    // How far open the full player is (it fades and settles while the picture glides to the dock).
    val open by animateFloatAsState(if (full) 1f else 0f, if (full) spring(dampingRatio = 0.86f, stiffness = 420f) else tween(260, easing = WaveMotion.EmphasizedAccelerate), label = "videoOpen")
    if (target == null && open == 0f) return

    // Ambient mode (upright, Settings › Playback): the background softly takes on the colors on
    // screen as the video plays. Buttons keep the video's own colors, so nothing jumps around.
    val base = app.wave.music.ui.theme.darkBaseScheme()
    val artScheme = rememberArtColorScheme(current.thumbnail, base = base)
    val ambientOn = WaveSettings.state.collectAsState().value.videoAmbient && !edgeToEdge && full
    var glowTarget by remember(current.videoId) { mutableStateOf<Color?>(null) }
    LaunchedEffect(current.videoId, ambientOn) {
        if (!ambientOn) return@LaunchedEffect
        while (true) {
            delay(700)
            if (!engine.state.value.firstFrame) continue
            val pixels = engine.framePixels() ?: continue
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { app.wave.music.ui.components.schemeFromPixels(pixels, base) }?.let { glowTarget = it.primaryContainer }
        }
    }
    val glow by androidx.compose.animation.animateColorAsState(glowTarget ?: artScheme.primaryContainer, tween(1400), label = "ambient")

    CompositionLocalProviderGlow(if (ambientOn) glow else null) {
    MaterialTheme(colorScheme = artScheme) {
        var menu by remember { mutableStateOf(VideoRender.menu?.let { OpenMenu(it.first, it.second) }) }
        var upNextOpen by remember { mutableStateOf(VideoRender.upNext) }
        LaunchedEffect(full) { if (!full) { menu = null; upNextOpen = false } }
        val actions = VideoActions(
            onClose = ::minimize,
            onUpright = ::turnUpright,
            onFullscreen = { fullscreenAsked = true; if (!computer && window.screenWidthDp < window.screenHeightDp) engine.holdOrientation(1) },
            onNext = { history += current; playNext(current) },
            onPrevious = { playPrevious(current, history) },
            hasPrevious = history.isNotEmpty(),
            hasNext = Video.upNext.isNotEmpty(),
            onMenu = { menu = OpenMenu("more", it) },
            onUpNext = { upNextOpen = true },
        )
        val glassOver = dev.chrisbanes.haze.rememberHazeState()
        val menuGlass = dev.chrisbanes.haze.rememberHazeState()
        val fullSpot = remember { mutableStateOf<Rect?>(null) }
        var origin by remember { mutableStateOf(Offset.Zero) }
        val layout = when { edgeToEdge -> "edge"; wideRoom -> "wide"; else -> "upright" }
        // Back: menus and Up next first, then full screen goes upright, then the video shrinks into
        // the dock (following your swipe, like the music player).
        val swipe = app.wave.music.ui.components.predictiveBack(enabled = full && !edgeToEdge && menu == null && !upNextOpen, resetAfter = true, onBack = ::minimize)
        // The full player's motion, shared by its layers and the picture so they move as one.
        val motion = Modifier.graphicsLayer {
            alpha = open.coerceIn(0f, 1f)
            translationY = (1f - open) * size.height * 0.06f
        }.backGesture(swipe)
        @Composable
        fun Layer(front: Boolean) {
            when (layout) {
                "upright" -> UprightLayout(engine, state, current, actions, history, front, fullSpot, onMenu = { kind, at -> menu = OpenMenu(kind, at) })
                else -> FullLayout(engine, state, current, actions, history, edgeToEdge, front, fullSpot, glassOver, onMenu = { kind, at -> menu = OpenMenu(kind, at) })
            }
        }
        val menuRows: @Composable (OpenMenu, (() -> Unit) -> Unit) -> Unit = { m, dismiss ->
            fun qualityName(h: Int) = when { h >= 2160 -> "4K"; h >= 1440 -> "1440p"; h >= 1080 -> "1080p HD"; h >= 720 -> "720p HD"; else -> "${h}p" }
            val qualityNow = state.cap?.let(::qualityName) ?: ("Auto" + (state.showing?.let { " · ${it}p" } ?: ""))
            val speedNow = if (state.speed == 1f) "Normal" else speedName(state.speed)
            when (m.kind) {
                "quality" -> {
                    MenuRow("Auto", "Best for your connection", state.cap == null) { dismiss { engine.setQuality(null) } }
                    state.heights.filter { it >= 144 }.distinct().sortedDescending().forEach { h -> MenuRow(qualityName(h), null, state.cap == h) { dismiss { engine.setQuality(h) } } }
                }
                "speed" -> listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { v ->
                    MenuRow(if (v == 1f) "Normal" else speedName(v), null, state.speed == v) { dismiss { engine.setSpeed(v) } }
                }
                "settings" -> {
                    MenuRow("Quality", qualityNow, false, icon = Icons.Rounded.Tune, more = true) { menu = OpenMenu("quality", m.anchor) }
                    MenuRow("Speed", speedNow, false, icon = Icons.Rounded.Speed, more = true) { menu = OpenMenu("speed", m.anchor) }
                    MenuRow("Loop this video", null, state.loop, icon = Icons.Rounded.RepeatOne) { dismiss { engine.setLoop(!state.loop) } }
                }
                else -> {
                    MenuRow("Play the song", "Just the music, from the start", false, icon = Icons.Rounded.Headphones) { dismiss { scope.launch { playTheSong(current) } } }
                    MenuRow("Up next", if (Video.upNext.isEmpty()) "Finding more videos…" else "${Video.upNext.size} videos", false, icon = Icons.AutoMirrored.Rounded.QueueMusic) { dismiss { upNextOpen = true } }
                    MenuRow("Loop this video", null, state.loop, icon = Icons.Rounded.RepeatOne) { dismiss { engine.setLoop(!state.loop) } }
                    MenuRow("Share", null, false, icon = Icons.Rounded.Share) { dismiss { WavePlatform.current.share(current.title, "https://music.youtube.com/watch?v=${current.videoId}") } }
                    MenuRow("Open in YouTube", null, false, icon = Icons.AutoMirrored.Rounded.OpenInNew) { dismiss { WavePlatform.current.openUrl("https://www.youtube.com/watch?v=${current.videoId}") } }
                    MenuRow("Stop the video", null, false, icon = Icons.Rounded.Close) { dismiss { close() } }
                }
            }
        }
        Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
          // Everything in here is what a menu blurs: the player, the video itself and its controls.
          Box(Modifier.fillMaxSize().hazeSource(menuGlass)) {
            // 1. The full player's background, with a hole where the video goes.
            if (open > 0f) Box(Modifier.fillMaxSize().then(motion)) { Layer(front = false) }
            // 2. The one picture: in the full player's hole, or in the dock's song row when shrunk.
            val dock = VideoDock.spot.value
            val goal = (if (full || dock == null) fullSpot.value else dock)?.translate(-origin)
            if (goal != null) {
                val rect = remember { Animatable(goal, Rect.VectorConverter) }
                LaunchedEffect(goal) { rect.animateTo(goal, spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)) }
                val r = rect.value
                val density = LocalDensity.current
                val corner by animateDpAsState(if (full) (if (layout == "wide") 24.dp else 0.dp) else 12.dp, tween(260), label = "videoCorner")
                // Shrunk with nowhere to go (the dock is hidden, e.g. by the keyboard): out of sight.
                val hidden = !full && dock == null
                Box(Modifier.fillMaxSize().then(if (full) motion else Modifier)) {
                    Box(
                        Modifier
                            .offset { IntOffset(r.left.roundToInt(), r.top.roundToInt()) }
                            .size(with(density) { r.width.toDp() }, with(density) { r.height.toDp() })
                            .graphicsLayer { alpha = if (hidden) 0f else 1f }
                            .clip(RoundedCornerShape(corner))
                            .background(Color.Black)
                            .hazeSource(glassOver),
                    ) {
                        engine.Surface(Modifier.fillMaxSize())
                        LoadingCover(state, current, small = !full)
                    }
                }
            }
            // 3. The controls (full only).
            if (open > 0f && full) Box(Modifier.fillMaxSize().then(motion)) { Layer(front = true) }
          }
            // 4. Up next and menus, over (and blurring) everything above.
            if (open > 0f && full) Box(Modifier.fillMaxSize().then(motion)) {
                if (layout == "upright") VideoQueue(state, current, upNextOpen, onClose = { upNextOpen = false }) { v ->
                    upNextOpen = false
                    history += current
                    Video.watch(v, Video.upNext.filter { it.videoId != v.videoId } + current)
                } else UpNextPanel(menuGlass, upNextOpen, sideways = true, current = current, onClose = { upNextOpen = false }) { v ->
                    upNextOpen = false
                    history += current
                    Video.watch(v, Video.upNext.filter { it.videoId != v.videoId } + current)
                }
                androidx.compose.runtime.key(menu != null, upNextOpen, edgeToEdge) {
                    androidx.compose.ui.backhandler.BackHandler(enabled = menu != null || upNextOpen || edgeToEdge) {
                        when {
                            menu != null -> menu = null
                            upNextOpen -> upNextOpen = false
                            else -> turnUpright()
                        }
                    }
                }
                menu?.let { m ->
                    // Each menu (and each submenu) grows out of the button that opened it.
                    androidx.compose.runtime.key(m.kind) {
                        GlassMenu(menuGlass, m.anchor, origin, onClose = { if (menu === m) menu = null }, title = when (m.kind) { "quality" -> "Quality"; "speed" -> "Speed"; "settings" -> "Settings"; else -> null }) { dismiss ->
                            menuRows(m, dismiss)
                        }
                    }
                }
            }
        }
    }
    }
}

private val LocalGlow = androidx.compose.runtime.compositionLocalOf<Color?> { null }

@Composable
private fun CompositionLocalProviderGlow(glow: Color?, content: @Composable () -> Unit) =
    androidx.compose.runtime.CompositionLocalProvider(LocalGlow provides glow, content = content)

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
            Box(Modifier.fillMaxSize().blocksTouchesBelow().background(c.background))
            val glow = LocalGlow.current
            if (glow != null) {
                // Ambient mode: a soft glow in the colors on screen right now, drifting as it plays.
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to glow.copy(alpha = 0.75f), 0.4f to glow.copy(alpha = 0.4f), 0.75f to c.background.copy(alpha = 0.9f), 1f to c.background)))
            } else {
                // The video's picture, blurred, filling the screen like the music player's art.
                app.wave.music.ui.components.BlurredImage(target.thumbnail, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.4f; scaleY = 1.4f }, radius = 72.dp)
            }
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
                GlassButton(Icons.Rounded.KeyboardArrowDown, "Shrink to the mini player", actions.onClose)
                Spacer(Modifier.weight(1f))
                GlassButton(Icons.Rounded.Fullscreen, "Full screen", actions.onFullscreen)
                Spacer(Modifier.width(8.dp))
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

/**
 * The music player's frosted tray, all Up next: what's coming (tap for the whole list). Pull its
 * handle up (or tap it) for Wave's volume, like the music player.
 */
@Composable
private fun VideoTray(actions: VideoActions) {
    val c = MaterialTheme.colorScheme
    val reveal = app.wave.music.ui.player.rememberReveal()
    LaunchedEffect(Unit) { if (VideoRender.volume) reveal.toggle() }
    Column(
        Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(androidx.compose.ui.graphics.lerp(Color.Black, c.primary, 0.12f).copy(alpha = 0.6f))
            .revealDrag(reveal)
            .navigationBarsPadding(),
    ) {
        app.wave.music.ui.player.RevealHandle(reveal, c.onBackground.copy(alpha = 0.3f), Modifier.padding(top = 4.dp))
        val next = Video.upNext.firstOrNull()
        Row(
            Modifier
                .heightIn(min = 68.dp)
                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                .fillMaxWidth()
                .pressable(RoundedCornerShape(20.dp), pressedScale = 0.97f, enabled = next != null, onClick = actions.onUpNext)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (next != null) AsyncImage(next.thumbnail, null, Modifier.size(width = 64.dp, height = 36.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
            else Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = c.onBackground)
            Column(Modifier.weight(1f)) {
                Text("Up next", color = c.onBackground, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(next?.let { "${it.title} · ${it.artist}" } ?: "Finding more videos…", color = c.onBackground.copy(alpha = 0.65f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (Video.upNext.size > 1) Text("${Video.upNext.size} videos", color = c.onBackground.copy(alpha = 0.55f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Rounded.KeyboardArrowUp, null, tint = c.onBackground.copy(alpha = 0.6f))
        }
        app.wave.music.ui.player.RevealBox(reveal) {
            Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)) {
                app.wave.music.ui.player.WaveVolume(c.onBackground, c.primary, c.onPrimary)
            }
        }
    }
}

/**
 * Up next for videos, like the songs' Up next: a big title, the video playing now lit up with the
 * Wave lines moving, then what's coming. Tap one to watch it, X to drop it, drag the handle to move it.
 */
@Composable
private fun VideoQueue(state: VideoState, current: VideoTarget, open: Boolean, onClose: () -> Unit, onPick: (VideoTarget) -> Unit) {
    val c = MaterialTheme.colorScheme
    val p by animateFloatAsState(if (open) 1f else 0f, if (open) spring(dampingRatio = 0.86f, stiffness = 380f) else tween(240), label = "videoQueue")
    if (p == 0f) return
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var entries by remember { mutableStateOf(Video.upNext) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(Video.upNext) { if (!dragging) entries = Video.upNext }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val reorder = sh.calvin.reorderable.rememberReorderableLazyListState(listState) { from, to ->
        val a = entries.indexOfFirst { "q" + it.videoId == from.key }
        val b = entries.indexOfFirst { "q" + it.videoId == to.key }
        if (a >= 0 && b >= 0) {
            entries = entries.toMutableList().apply { add(b, removeAt(a)) }
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentFrequentTick)
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = p.coerceIn(0f, 1f); translationY = (1f - p) * size.height * 0.25f }
            .blocksTouchesBelow()
            .background(c.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        app.wave.music.ui.components.BlurredImage(current.thumbnail, Modifier.fillMaxWidth().height(600.dp).graphicsLayer { scaleX = 1.3f; scaleY = 1.3f }, radius = 80.dp, fadeBottom = 0.4f)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to c.background.copy(alpha = 0.25f), 0.45f to c.background.copy(alpha = 0.7f), 0.75f to c.background)))
        LazyColumn(state = listState, modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "head") {
                Column(Modifier.topBarPadding()) {
                    Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Up next", color = c.onBackground, style = display(34.sp).copy(letterSpacing = (-0.6).sp))
                            Text("Music videos", color = c.onBackground.copy(alpha = 0.6f), fontSize = 14.sp)
                        }
                        GlassButton(Icons.Rounded.KeyboardArrowDown, "Close Up next", onClose)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
            item(key = "now") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(PrismaLook.corner(20.dp)))
                        .background(Brush.horizontalGradient(listOf(c.primary.copy(alpha = 0.28f), c.primary.copy(alpha = 0.1f))))
                        .padding(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AsyncImage(current.thumbnail, null, Modifier.size(width = 80.dp, height = 45.dp).clip(RoundedCornerShape(PrismaLook.corner(12.dp))), contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f)) {
                        Text(current.title, color = c.onBackground, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(current.artist, color = c.onBackground.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    WatchingLines(state.playing, c.primary)
                }
            }
            if (entries.isNotEmpty()) item(key = "label") {
                Text("Coming up", Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp), color = c.onBackground.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            items(entries, key = { "q" + it.videoId }) { v ->
                ReorderableItem(reorder, key = "q" + v.videoId, modifier = Modifier.animateItem()) { isDragging ->
                    val lift by animateFloatAsState(if (isDragging) 1f else 0f, WaveMotion.bouncy(), label = "lift")
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp)
                            .graphicsLayer { scaleX = 1f + 0.03f * lift; scaleY = 1f + 0.03f * lift }
                            .shadow(12.dp * lift, RoundedCornerShape(PrismaLook.corner(20.dp)))
                            .clip(RoundedCornerShape(PrismaLook.corner(20.dp)))
                            .background(c.surfaceContainerHighest.copy(alpha = lift.coerceIn(0f, 1f)))
                            .clickable { onPick(v) }
                            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        AsyncImage(v.thumbnail, null, Modifier.size(width = 80.dp, height = 45.dp).clip(RoundedCornerShape(PrismaLook.corner(12.dp))), contentScale = ContentScale.Crop)
                        Column(Modifier.weight(1f)) {
                            Text(v.title, color = c.onBackground, fontWeight = FontWeight.Medium, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(v.artist, color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        androidx.compose.material3.IconButton(onClick = { Video.upNext = Video.upNext.filter { it.videoId != v.videoId } }) {
                            Icon(Icons.Rounded.Close, "Remove ${v.title} from Up next", tint = c.onBackground.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                        }
                        androidx.compose.material3.IconButton(
                            onClick = {},
                            modifier = Modifier.draggableHandle(
                                onDragStarted = { dragging = true },
                                onDragStopped = { dragging = false; Video.upNext = entries },
                            ),
                        ) { Icon(Icons.Rounded.DragIndicator, "Drag to move ${v.title}", tint = c.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

/** The Wave mark's three lines, rippling while the video plays. */
@Composable
private fun WatchingLines(playing: Boolean, color: Color) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "wave")
    val phase by t.animateFloat(0f, (2 * Math.PI).toFloat(), androidx.compose.animation.core.infiniteRepeatable(tween(1100, easing = androidx.compose.animation.core.LinearEasing)), label = "phase")
    val amount by animateFloatAsState(if (playing) 1f else 0.25f, tween(400), label = "amount")
    androidx.compose.foundation.Canvas(Modifier.size(width = 28.dp, height = 22.dp)) {
        val stroke = 2.6.dp.toPx()
        val amp = 2.6.dp.toPx() * amount
        repeat(3) { row ->
            val y0 = size.height * (0.2f + row * 0.3f)
            val path = androidx.compose.ui.graphics.Path()
            for (i in 0..24) {
                val x = size.width * i / 24
                val y = y0 + amp * kotlin.math.sin(phase + row * 0.9f + x / size.width * 2f * Math.PI.toFloat())
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
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

/** Where a menu sits, worked out as it's laid out (its shape reads it to start from the button). */
private class MenuSpot { var x = 0f; var y = 0f }

/**
 * A frosted glass menu, like every menu in Wave 2.0. It grows out of the button that opened it, the
 * way the song ⋮ does: it starts as the button's own shape and color and opens up into the menu,
 * then folds back into the button when it closes. [content] gets a dismiss that runs an action and
 * folds the menu away.
 */
@Composable
private fun GlassMenu(glass: dev.chrisbanes.haze.HazeState?, anchor: Rect, origin: Offset, onClose: () -> Unit, title: String?, from: Color = Color.White.copy(alpha = 0.16f), content: @Composable ((() -> Unit) -> Unit) -> Unit) {
    val c = MaterialTheme.colorScheme
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { progress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 420f)) }
    val dismiss: (() -> Unit) -> Unit = { action ->
        action()
        if (!closing) {
            closing = true
            scope.launch {
                progress.animateTo(0f, tween(200, easing = WaveMotion.EmphasizedAccelerate))
                onClose()
            }
        }
    }
    val spot = remember { MenuSpot() }
    val density = LocalDensity.current
    val radius = with(density) { PrismaLook.corner(24.dp).toPx() }
    val local = anchor.translate(-origin)
    val shape = remember(local) {
        object : androidx.compose.ui.graphics.Shape {
            override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
                val p = progress.value.coerceIn(0f, 1.04f)
                val start = local.translate(-spot.x, -spot.y)
                fun l(a: Float, b: Float) = a + (b - a) * p
                val r = Rect(
                    l(start.left, 0f).coerceAtLeast(0f), l(start.top, 0f).coerceAtLeast(0f),
                    l(start.right, size.width).coerceAtMost(size.width), l(start.bottom, size.height).coerceAtMost(size.height),
                )
                val corner = l(start.height / 2f, radius).coerceAtMost(r.minDimension / 2f)
                return androidx.compose.ui.graphics.Outline.Rounded(androidx.compose.ui.geometry.RoundRect(r, androidx.compose.ui.geometry.CornerRadius(corner)))
            }
        }
    }
    androidx.compose.ui.layout.Layout(
        content = {
            Box(
                Modifier
                    .width(250.dp)
                    .graphicsLayer {
                        this.shape = shape
                        clip = true
                        shadowElevation = 16.dp.toPx() * progress.value.coerceIn(0f, 1f)
                    }
                    .glass(glass, 28.dp, c.background, listOf(dev.chrisbanes.haze.HazeTint(c.surfaceContainerHigh.copy(alpha = 0.62f))), Color.Transparent)
                    .drawBehind {
                        // Starts as the button's own color, then gives way to the frosted glass.
                        val k = (progress.value * 1.6f).coerceIn(0f, 1f)
                        val glassFill = if (glass != null && PrismaLook.blur > 0) Color.White.copy(alpha = 0.04f) else c.surfaceContainerHigh
                        drawRect(androidx.compose.ui.graphics.lerp(from, glassFill, k))
                    }
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                Column(
                    Modifier
                        .graphicsLayer {
                            val p = progress.value
                            alpha = ((p - 0.3f) / 0.6f).coerceIn(0f, 1f)
                            translationY = (1f - p.coerceIn(0f, 1f)) * -10.dp.toPx()
                        }
                        .padding(vertical = 6.dp),
                ) {
                    title?.let { Text(it, Modifier.padding(start = 18.dp, top = 8.dp, bottom = 4.dp), color = c.onSurface.copy(alpha = 0.6f), style = body(12.sp, FontWeight.Bold)) }
                    content(dismiss)
                }
            }
        },
        modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { dismiss {} } },
    ) { measurables, constraints ->
        val p = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val margin = 12.dp.roundToPx()
        // Shares a corner with its button, opening toward the side with room.
        val x = (if (local.center.x > constraints.maxWidth / 2f) local.right - p.width else local.left).roundToInt().coerceIn(margin, (constraints.maxWidth - p.width - margin).coerceAtLeast(margin))
        val below = local.top + p.height < constraints.maxHeight - margin
        val y = (if (below) local.top else local.bottom - p.height).roundToInt().coerceIn(margin, (constraints.maxHeight - p.height - margin).coerceAtLeast(margin))
        spot.x = x.toFloat()
        spot.y = y.toFloat()
        layout(constraints.maxWidth, constraints.maxHeight) { p.place(x, y) }
    }
}

@Composable
private fun MenuRow(label: String, sub: String?, selected: Boolean, icon: ImageVector? = null, more: Boolean = false, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        icon?.let { Icon(it, null, tint = c.onSurface, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f)) {
            Text(label, color = c.onSurface, style = body(15.sp, if (selected) FontWeight.Bold else FontWeight.Medium))
            sub?.let { Text(it, color = c.onSurface.copy(alpha = 0.6f), style = body(12.sp)) }
        }
        if (selected) Icon(Icons.Rounded.Check, null, tint = c.primary, modifier = Modifier.size(20.dp))
        if (more) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.onSurface.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
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
                    // Under the play button, so the two never overlap.
                    Text(state.error, Modifier.padding(start = 24.dp, end = 24.dp, top = 150.dp), color = Color.White, style = body(15.sp, FontWeight.SemiBold))
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
                    CircleButton(Icons.Rounded.KeyboardArrowDown, "Shrink to the mini player", c.onBackground.copy(alpha = 0.08f), c.onBackground, 44.dp, actions.onClose)
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
 * Controls over the picture: laid out like YouTube (name and buttons along the top, previous · play ·
 * next in the middle, time and the line along the bottom) but made of Wave 2.0's parts: frosted
 * glass buttons, the song-colored play button, the wave you drag through. Quality, speed and loop
 * sit right on screen as glass pills. Tap to show or hide them (they tuck away while it plays);
 * double-tap either side to jump 10 seconds.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Controls(engine: VideoEngine, state: VideoState, target: VideoTarget, actions: VideoActions, full: Boolean, glassOver: dev.chrisbanes.haze.HazeState?, onMenu: (String, Rect) -> Unit, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    var shown by remember { mutableStateOf(true) }
    var touched by remember { mutableIntStateOf(0) }
    // They tuck away while it plays, unless they're kept on screen (Accessibility, or TalkBack).
    LaunchedEffect(shown, state.playing, touched, PrismaLook.keepControls) { if (shown && state.playing && !PrismaLook.keepControls) { delay(3500); shown = false } }
    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(target.videoId) { while (true) { position = engine.positionMs; delay(200) } }
    var jump by remember { mutableIntStateOf(0) }
    LaunchedEffect(jump) { if (jump != 0) { delay(650); jump = 0 } }
    val visible = shown || !state.playing || state.error != null || PrismaLook.keepControls
    val dim by animateFloatAsState(if (visible) 1f else 0f, tween(220), label = "dim")
    // Frosted glass over the video, like Wave's dock.
    val glass = Modifier
        .glass(glassOver, 26.dp, Color.Black, listOf(dev.chrisbanes.haze.HazeTint(Color.Black.copy(alpha = 0.3f))), Color.Black.copy(alpha = 0.5f))
        .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)

    BoxWithConstraints(modifier) {
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
        // A soft shade at the top and bottom so the controls read on any picture.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = dim }.background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.55f), 0.28f to Color.Black.copy(alpha = 0.12f), 0.7f to Color.Black.copy(alpha = 0.12f), 1f to Color.Black.copy(alpha = 0.65f))))
        // Jumped: a glass bubble on that side.
        AnimatedVisibility(jump != 0, Modifier.align(if (jump < 0) Alignment.CenterStart else Alignment.CenterEnd).padding(horizontal = if (full) 72.dp else 28.dp), enter = fadeIn(tween(90)) + androidx.compose.animation.scaleIn(initialScale = 0.8f), exit = fadeOut(tween(200))) {
            Row(Modifier.clip(CircleShape).then(glass).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(if (jump < 0) Icons.Rounded.Replay10 else Icons.Rounded.Forward10, null, tint = Color.White)
                Text("10 seconds", color = Color.White, style = body(14.sp, FontWeight.SemiBold))
            }
        }

        AnimatedVisibility(visible, Modifier.fillMaxSize(), enter = fadeIn(tween(160)), exit = fadeOut(tween(260))) {
            Box(Modifier.fillMaxSize()) {
                // Top: shrink and what's playing; Up next and ⋮ on the right.
                Row(
                    Modifier.align(Alignment.TopStart).fillMaxWidth().then(if (full) Modifier.topBarPadding().padding(start = 16.dp, end = 16.dp, top = 10.dp) else Modifier.padding(10.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GlassIcon(glass, Icons.Rounded.KeyboardArrowDown, "Shrink to the mini player") { actions.onClose() }
                    if (full) Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text(target.title, color = Color.White, style = display(18.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(target.artist, color = Color.White.copy(alpha = 0.72f), style = body(13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else Spacer(Modifier.weight(1f))
                    GlassIcon(glass, Icons.AutoMirrored.Rounded.QueueMusic, "Up next") { actions.onUpNext() }
                    GlassIcon(glass, Icons.Rounded.MoreVert, "More options") { at -> actions.onMenu(at) }
                }

                // Middle: previous · play · next, Wave's play button in the video's color.
                if (state.firstFrame || state.error != null) Row(
                    Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (full) 40.dp else 22.dp),
                ) {
                    val small = if (full) 56.dp else 48.dp
                    Box(Modifier.size(small).clip(CircleShape).then(glass).pressable(CircleShape, Color.Transparent, pressedScale = 0.86f, enabled = actions.hasPrevious, onClick = actions.onPrevious), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.SkipPrevious, "Previous video", tint = Color.White.copy(alpha = if (actions.hasPrevious) 1f else 0.35f), modifier = Modifier.size(small * 0.5f))
                    }
                    val big = if (full) 76.dp else 64.dp
                    val corner by animateDpAsState(if (state.playing) big * 0.32f else big / 2, WaveMotion.bouncy(), label = "videoPlayShape")
                    Box(
                        Modifier.size(width = big * 1.25f, height = big).pressable(RoundedCornerShape(PrismaLook.corner(corner)), c.primary, pressedScale = 0.9f) {
                            if (state.ended) engine.seekTo(0)
                            engine.togglePlay(); touched++
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Crossfade(when { state.error != null -> 2; state.ended -> 3; else -> 0 }, label = "videoPlay") { s ->
                            when (s) {
                                2 -> Icon(Icons.Rounded.Refresh, "Try again", tint = c.onPrimary, modifier = Modifier.size(big * 0.45f))
                                3 -> Icon(Icons.Rounded.Replay, "Watch again", tint = c.onPrimary, modifier = Modifier.size(big * 0.45f))
                                else -> PlayPauseIcon(state.playing, c.onPrimary, size = big * 0.45f)
                            }
                        }
                    }
                    Box(Modifier.size(small).clip(CircleShape).then(glass).pressable(CircleShape, Color.Transparent, pressedScale = 0.86f, enabled = actions.hasNext, onClick = actions.onNext), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.SkipNext, "Next video", tint = Color.White.copy(alpha = if (actions.hasNext) 1f else 0.35f), modifier = Modifier.size(small * 0.5f))
                    }
                }

                // Bottom: the time, then quality, speed, loop and full screen right there; the wave below.
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth().then(if (full) Modifier.navigationBarsPadding().padding(start = 20.dp, end = 16.dp, bottom = 8.dp) else Modifier.padding(start = 14.dp, end = 10.dp, bottom = 2.dp)),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(formatTime(position), color = Color.White, style = body(13.sp, FontWeight.SemiBold))
                        Text("/  " + formatTime(state.durationMs), color = Color.White.copy(alpha = 0.7f), style = body(13.sp))
                        Spacer(Modifier.weight(1f))
                        if (state.heights.any { it >= 144 }) GlassPill(glass, Icons.Rounded.Tune, state.cap?.let { "${it}p" } ?: ("Auto" + (state.showing?.let { " · ${it}p" } ?: ""))) { at -> onMenu("quality", at) }
                        GlassPill(glass, Icons.Rounded.Speed, speedName(state.speed), on = state.speed != 1f) { at -> onMenu("speed", at) }
                        GlassIcon(glass, Icons.Rounded.RepeatOne, if (state.loop) "Stop looping" else "Loop this video", on = state.loop) { engine.setLoop(!state.loop); touched++ }
                        GlassIcon(glass, if (full) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, if (full) "Leave full screen" else "Full screen") { if (full) actions.onUpright() else actions.onFullscreen() }
                    }
                    WavyProgress(
                        progress = { if (state.durationMs > 0) (engine.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f },
                        playing = state.playing && !PrismaLook.calm,
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.28f),
                        stroke = 4.dp,
                        amplitude = 3.dp,
                        wavelength = 24.dp,
                        showThumb = true,
                        onSeek = { f -> engine.seekTo((f * state.durationMs).toLong()); touched++ },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(30.dp),
                    )
                }
            }
        }
    }
}

/** A round frosted-glass button over the video; says where it is (for its menu). Lit when [on]. */
@Composable
private fun GlassIcon(glass: Modifier, icon: ImageVector, label: String, on: Boolean = false, onClick: (Rect) -> Unit) {
    val c = MaterialTheme.colorScheme
    var at by remember { mutableStateOf(Rect.Zero) }
    Box(
        Modifier.size(44.dp).onGloballyPositioned { at = it.boundsInRoot() }.clip(CircleShape)
            .then(if (on) Modifier.background(c.primary) else glass)
            .pressable(CircleShape, Color.Transparent, pressedScale = 0.86f) { onClick(at) },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = if (on) c.onPrimary else Color.White, modifier = Modifier.size(22.dp)) }
}

/** A frosted-glass pill with an icon and what it's set to (quality, speed); its menu grows out of it. */
@Composable
private fun GlassPill(glass: Modifier, icon: ImageVector, label: String, on: Boolean = false, onClick: (Rect) -> Unit) {
    val c = MaterialTheme.colorScheme
    var at by remember { mutableStateOf(Rect.Zero) }
    Row(
        Modifier.height(44.dp).onGloballyPositioned { at = it.boundsInRoot() }.clip(CircleShape)
            .then(if (on) Modifier.background(c.primary) else glass)
            .pressable(CircleShape, Color.Transparent, pressedScale = 0.94f) { onClick(at) }
            .padding(start = 12.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = if (on) c.onPrimary else Color.White, modifier = Modifier.size(18.dp))
        Text(label, color = if (on) c.onPrimary else Color.White, style = body(14.sp, FontWeight.SemiBold), maxLines = 1)
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
