package app.wave.music.ui.home

import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.runtime.LaunchedEffect
import app.wave.music.ui.components.sharedContainer
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import app.wave.music.ui.components.glass
import app.wave.music.ui.menu.menuAnchor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wave.music.data.HomeFeed
import app.wave.music.data.Session
import app.wave.music.platform.prismaDisplayFont
import app.wave.music.platform.prismaTextFont
import app.wave.music.ui.components.WaveMark
import app.wave.music.ui.components.atSize
import app.wave.music.ui.components.pressable
import app.wave.music.ui.components.subtitle
import app.wave.music.ui.detail.Destination
import app.wave.music.ui.menu.LocalItemMenu
import app.wave.music.ui.menu.openFor
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem

/*
 * Wave Prisma (Wave 2.0, alpha): a bottom bar instead of the search bar at the top, and a denser
 * home of big picks, song mixes and rows. Wave's own look, looser than strict M3E.
 */

/** Height of the bottom bar, not counting the gesture bar under it. */
val PrismaBarHeight: Dp = 86.dp

/** Height of the bar at the top (the Wave mark and your picture) under the status bar. */
val PrismaTopHeight: Dp = 60.dp

/** Headings in Google Sans, everything else in Google Sans Text (where the phone has them). */
private fun display(size: TextUnit, weight: FontWeight = FontWeight.Bold, spacing: TextUnit = 0.sp) =
    TextStyle(fontFamily = prismaDisplayFont, fontSize = size, fontWeight = weight, letterSpacing = spacing)

private fun body(size: TextUnit, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = prismaTextFont, fontSize = size, fontWeight = weight)

// ---------------------------------------------------------------- The top

/** Wave's mark on the left and your picture on the right, the same size. Scrolls away with the page. */
@Composable
fun PrismaTopBar(
    session: Session,
    onAvatar: () -> Unit,
    modifier: Modifier = Modifier,
    /** Top search mode: a search bar here instead of the Wave mark (it also opens your library). */
    search: (() -> Unit)? = null,
    inLibrary: Boolean = false,
    onLibrary: () -> Unit = {},
    /** Space at the sides (0 when the parent already pads). */
    edge: Dp = 16.dp,
) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().height(PrismaTopHeight).padding(horizontal = edge),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (search != null) {
            // The search bar: search everything (your library too), and a button that flips
            // between home and your library.
            Row(
                Modifier
                    .weight(1f)
                    .tourTarget("search")
                    .height(52.dp)
                    .clip(CircleShape)
                    .background(c.onBackground.copy(alpha = 0.09f))
                    .border(1.dp, c.onBackground.copy(alpha = 0.1f), CircleShape)
                    .clickable(onClick = search)
                    .padding(start = 16.dp, end = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = c.onBackground.copy(alpha = 0.8f))
                Text(
                    if (inLibrary) "Search your library" else "Search music & your library",
                    Modifier.weight(1f).padding(start = 10.dp),
                    color = c.onBackground.copy(alpha = 0.6f),
                    style = body(15.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val fill by animateColorAsState(if (inLibrary) c.primary else c.onBackground.copy(alpha = 0.1f), tween(180), label = "libFill")
                val ink by animateColorAsState(if (inLibrary) c.onPrimary else c.onBackground, tween(180), label = "libInk")
                Row(
                    Modifier.height(42.dp).pressable(CircleShape, fill, pressedScale = 0.92f, onClick = onLibrary).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.animation.Crossfade(inLibrary, label = "libIcon") { lib ->
                        Icon(if (lib) Icons.Rounded.Home else Icons.Rounded.LibraryMusic, if (lib) "Home" else "Library", tint = ink, modifier = Modifier.size(22.dp))
                    }
                    Text(if (inLibrary) " Home" else " Library", color = ink, style = body(14.sp, FontWeight.SemiBold))
                }
            }
        } else {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { WaveMark(c.onBackground, size = 40.dp) }
            Spacer(Modifier.weight(1f))
        }
        // Your picture, with the update badge on its corner.
        Box {
        Box(
            Modifier
                .tourTarget("avatar")
                .size(44.dp)
                .clip(CircleShape)
                .background(c.onBackground.copy(alpha = 0.1f))
                .border(1.dp, c.onBackground.copy(alpha = 0.16f), CircleShape)
                .clickable(onClick = onAvatar),
            contentAlignment = Alignment.Center,
        ) {
            if (session.photoUrl != null) {
                AsyncImage(session.photoUrl, "Your account", Modifier.size(44.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Rounded.AccountCircle, "Your account", Modifier.size(28.dp), tint = c.onBackground)
            }
        }
        // A new version of Wave: a little badge on your picture until you've updated.
        val update by app.wave.music.data.Updater.state.collectAsState()
        val hasUpdate = update is app.wave.music.data.Updater.State.Available || update is app.wave.music.data.Updater.State.Downloading || update is app.wave.music.data.Updater.State.Ready
        androidx.compose.animation.AnimatedVisibility(
            hasUpdate,
            Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp),
            enter = androidx.compose.animation.scaleIn(spring(dampingRatio = 0.5f, stiffness = 400f)),
            exit = androidx.compose.animation.scaleOut(),
        ) {
            Box(
                Modifier.size(20.dp).clip(CircleShape).background(c.primary).border(2.dp, c.background, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.ArrowUpward, "Update ready", Modifier.size(12.dp), tint = c.onPrimary) }
        }
        }
    }
}

// ---------------------------------------------------------------- The bottom bar

/**
 * Three tabs on a floating, rounded bar. The open tab grows into a filled shape with its name
 * (like WaveMini's tabs); the others stay as icons.
 */
/** How much the mini player adds to the dock when something is playing. */
val PrismaDockSongHeight: Dp = 66.dp

/**
 * The bottom of the app: one floating, rounded dock. When something plays, the mini player sits
 * in its top half and the tabs in its bottom half, one piece over a blur of the page behind
 * it. The open tab grows into a filled shape with its name; the others stay as icons.
 */
@Composable
fun PrismaTabBar(
    tab: Int,
    onTab: (Int) -> Unit,
    modifier: Modifier = Modifier,
    haze: dev.chrisbanes.haze.HazeState? = null,
    now: app.wave.music.playback.NowPlaying? = null,
    playing: Boolean = false,
    onOpenPlayer: () -> Unit = {},
    /** A song you just picked is getting ready: its play button spins. */
    loading: Boolean = false,
    /** The tabs (off with the top search, leaving just the mini player). */
    showTabs: Boolean = true,
    /** The mini player floats on its own above the tabs instead of joining them. */
    split: Boolean = false,
    /** Big tablets: the mini player and the tabs side by side in one row. */
    oneRow: Boolean = false,
    /** For the player to grow out of the mini player (and fold back into it). */
    visibility: androidx.compose.animation.AnimatedVisibilityScope? = null,
    /** [now] is a music video shrunk to the dock: its picture plays where the cover goes. */
    video: Boolean = false,
) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(app.wave.music.platform.PrismaLook.corner(32.dp))
    // Frosted glass (Modern) or a solid bar (Legacy).
    val glass = Modifier
        .clip(shape)
        .glass(haze, 30.dp, c.background, listOf(dev.chrisbanes.haze.HazeTint(c.surfaceContainer.copy(alpha = 0.6f))), c.surfaceContainer.copy(alpha = 0.97f))
        .border(1.dp, c.onSurface.copy(alpha = 0.1f), shape)
    // Swiped away: the song folds out of the dock right away, without waiting for the player to catch up.
    var swipedAway by remember { mutableStateOf<String?>(null) }
    // Once the player has really stopped, the same song can show again if you play it.
    if (now == null && swipedAway != null) swipedAway = null
    val now = now?.takeIf { it.videoId != swipedAway }
    var lastSong by remember { mutableStateOf(now) }
    if (now != null) lastSong = now
    val songShape = RoundedCornerShape(app.wave.music.platform.PrismaLook.corner(if (split || oneRow) 32.dp else 26.dp))
    // The song: the player grows out of exactly this.
    val song: @Composable (Modifier) -> Unit = { m ->
        lastSong?.let { s ->
            DockSong(s, playing, onOpenPlayer, loading, if (video) m else m.then(Modifier.sharedContainer("player", visibility, songShape)), onSwipedAway = { swipedAway = s.videoId }, video = video)
        }
    }
    val enter = androidx.compose.animation.expandVertically(tween(320, easing = app.wave.music.ui.components.WaveMotion.EmphasizedDecelerate)) + androidx.compose.animation.fadeIn(tween(220))
    val exit = androidx.compose.animation.shrinkVertically(tween(240)) + androidx.compose.animation.fadeOut(tween(160))
    val tabsRow: @Composable (Modifier) -> Unit = { m -> DockTabs(tab, onTab, m) }

    if (oneRow && showTabs) {
        // Big tablets: one row, so the dock doesn't take two rows of height for no reason. The tabs
        // come first, then a compact mini player.
        Row(
            modifier
                .widthIn(max = 900.dp)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (split) {
                Box(Modifier.width(420.dp).then(glass)) { tabsRow(Modifier.fillMaxWidth()) }
                androidx.compose.animation.AnimatedVisibility(now != null, Modifier.widthIn(max = 440.dp).weight(1f, fill = false), enter = androidx.compose.animation.fadeIn(tween(220)), exit = androidx.compose.animation.fadeOut(tween(160))) {
                    Box(Modifier.fillMaxWidth().then(glass)) { song(Modifier) }
                }
            } else {
                Row(Modifier.then(glass), verticalAlignment = Alignment.CenterVertically) {
                    tabsRow(Modifier.width(420.dp))
                    androidx.compose.animation.AnimatedVisibility(now != null, enter = androidx.compose.animation.fadeIn(tween(220)) + androidx.compose.animation.expandHorizontally(), exit = androidx.compose.animation.fadeOut(tween(160)) + androidx.compose.animation.shrinkHorizontally()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(1.dp).height(36.dp).background(c.onSurface.copy(alpha = 0.1f)))
                            Box(Modifier.width(400.dp)) { song(Modifier) }
                        }
                    }
                }
            }
        }
        return
    }
    Column(
        modifier
            // Tablets: a comfortable dock in the middle instead of one stretched edge to edge.
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The song grows into the dock when something starts playing, and folds away when it stops.
        androidx.compose.animation.AnimatedVisibility(visible = split && now != null, enter = enter, exit = exit) {
            Box(Modifier.fillMaxWidth().then(glass)) { song(Modifier) }
        }
        if (showTabs) Column(Modifier.fillMaxWidth().then(glass)) {
            androidx.compose.animation.AnimatedVisibility(visible = !split && now != null, enter = enter, exit = exit) {
                Column {
                    song(Modifier)
                    Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(1.dp).background(c.onSurface.copy(alpha = 0.08f)))
                }
            }
            tabsRow(Modifier.fillMaxWidth())
        }
    }
}

/** Home, Library and Search: the open one grows into a filled shape with its name. */
@Composable
private fun DockTabs(tab: Int, onTab: (Int) -> Unit, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier.tourTarget("tabs").height(PrismaBarHeight - 16.dp).padding(7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tabs = listOf(
            Triple("Home", Icons.Rounded.Home, Icons.Outlined.Home),
            Triple("Library", Icons.Rounded.LibraryMusic, Icons.Outlined.LibraryMusic),
            Triple("Search", Icons.Rounded.Search, Icons.Rounded.Search),
        )
        tabs.forEachIndexed { i, (label, on, off) ->
            val selected = i == tab
            val weight by animateFloatAsState(if (selected) 1.8f else 1f, app.wave.music.ui.components.WaveMotion.bouncy(), label = "tabWeight")
            val fill by animateColorAsState(if (selected) c.primary else Color.Transparent, tween(180), label = "tabFill")
            val ink by animateColorAsState(if (selected) c.onPrimary else c.onSurface.copy(alpha = 0.75f), tween(180), label = "tabInk")
            Row(
                Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .tourTarget("tab-$i")
                    .pressable(RoundedCornerShape(app.wave.music.platform.PrismaLook.corner(26.dp)), fill, pressedScale = 0.92f) { onTab(i) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (selected) on else off, label, tint = ink, modifier = Modifier.size(26.dp))
                if (selected) {
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = ink, style = display(16.sp, FontWeight.Bold), maxLines = 1)
                }
            }
        }
    }
}

/** The mini player inside the dock: cover, song, and a play button in the song's color. */
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
private fun DockSong(now: app.wave.music.playback.NowPlaying, playing: Boolean, onOpen: () -> Unit, loading: Boolean = false, modifier: Modifier = Modifier, onSwipedAway: () -> Unit = {}, video: Boolean = false) {
    val c = MaterialTheme.colorScheme
    val song = app.wave.music.ui.components.rememberArtColorScheme(now.artworkUrl)
    // Swipe it down to close the song: it follows your finger, and past a point it drops away
    // and playback stops. Not far enough, it springs back.
    // Followed directly while dragging (no queued updates that could cancel the spring back).
    var dragPx by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val closeAt = with(density) { 56.dp.toPx() }
    LaunchedEffect(now.videoId) { dragPx = 0f }
    val live = app.wave.music.data.isRadioId(now.videoId)
    Row(
        modifier
            .tourTarget("song")
            .fillMaxWidth()
            .height(PrismaDockSongHeight)
            .graphicsLayer {
                translationY = dragPx
                alpha = 1f - (dragPx / (closeAt * 2.2f)).coerceIn(0f, 0.9f)
            }
            .draggable(
                androidx.compose.foundation.gestures.rememberDraggableState { d -> dragPx = (dragPx + d).coerceAtLeast(0f) },
                androidx.compose.foundation.gestures.Orientation.Vertical,
                onDragStopped = { velocity ->
                    if (dragPx > closeAt || velocity > 1800f) {
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.GestureEnd)
                        androidx.compose.animation.core.animate(dragPx, closeAt * 1.6f, animationSpec = tween(140)) { v, _ -> dragPx = v }
                        onSwipedAway()
                        if (video) app.wave.music.ui.video.closeVideo() else app.wave.music.playback.PlayerConnection.reset()
                    } else {
                        // Not far enough: it springs back home.
                        androidx.compose.animation.core.animate(dragPx, 0f, animationSpec = app.wave.music.ui.components.WaveMotion.bouncy()) { v, _ -> dragPx = v }
                    }
                },
            )
            .clickable(onClick = onOpen)
            .padding(start = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (video) {
            // The video itself plays here (Wave moves its picture into this spot).
            Box(
                Modifier
                    .size(width = 82.dp, height = 46.dp)
                    .onGloballyPositioned { app.wave.music.ui.video.VideoDock.spot.value = it.boundsInRoot() }
                    .clip(RoundedCornerShape(app.wave.music.platform.PrismaLook.corner(12.dp)))
                    .background(Color.Black),
            )
            androidx.compose.runtime.DisposableEffect(Unit) { onDispose { app.wave.music.ui.video.VideoDock.spot.value = null } }
        } else AsyncImage(now.artworkUrl?.atSize(226), null, Modifier.size(46.dp).clip(RoundedCornerShape(app.wave.music.platform.PrismaLook.corner(14.dp))).background(c.surfaceContainerHigh), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f)) {
            Text(now.title, color = c.onSurface, style = body(15.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (live) app.wave.music.ui.radio.LivePill(Modifier.padding(top = 2.dp))
            else Text(now.artist, color = c.onSurface.copy(alpha = 0.65f), style = body(13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // A video: just play and pause, in the video's colors, with room to breathe.
        if (video) {
            Box(
                Modifier.padding(start = 14.dp, end = 6.dp).size(46.dp).pressable(RoundedCornerShape(app.wave.music.platform.PrismaLook.corner(16.dp)), song.primary, pressedScale = 0.9f) { app.wave.music.playback.Video.engine?.togglePlay() },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.animation.Crossfade(loading, label = "dockVideoLoading") { busy ->
                    if (busy) androidx.compose.material3.LoadingIndicator(Modifier.size(34.dp), color = song.onPrimary)
                    else app.wave.music.ui.components.PlayPauseIcon(playing, song.onPrimary)
                }
            }
            return@Row
        }
        // Live radio: nothing to skip, one round button that stops and rejoins live.
        if (live) {
            val state by app.wave.music.playback.PlayerConnection.state.collectAsState()
            app.wave.music.ui.radio.LiveButton(state, 46.dp, fill = song.primary, ink = song.onPrimary)
            return@Row
        }
        // Customize: previous and next beside play, or just play.
        val skip = app.wave.music.data.WaveSettings.state.collectAsState().value.dockSkip
        if (skip) Box(
            Modifier.size(40.dp).clip(CircleShape).clickable { app.wave.music.playback.PlayerConnection.previous() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.SkipPrevious, "Previous song", tint = c.onSurface) }
        Box(
            Modifier.size(46.dp).pressable(RoundedCornerShape(app.wave.music.platform.PrismaLook.corner(16.dp)), song.primary, pressedScale = 0.9f) { app.wave.music.playback.PlayerConnection.togglePlay() },
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.animation.Crossfade(loading, label = "dockLoading") { busy ->
                if (busy) androidx.compose.material3.LoadingIndicator(Modifier.size(34.dp), color = song.onPrimary)
                else app.wave.music.ui.components.PlayPauseIcon(playing, song.onPrimary)
            }
        }
        if (skip) Box(
            Modifier.size(40.dp).clip(CircleShape).clickable { app.wave.music.playback.PlayerConnection.next() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.SkipNext, "Next song", tint = c.onSurface) }
    }
}

// ---------------------------------------------------------------- Home

private val thisYear = java.time.Year.now().value

/** The small line over a pick's title: why it's there. */
private fun captionFor(item: YTItem): String = when (item) {
    is AlbumItem -> {
        val artist = item.artists?.firstOrNull()?.name
        if (item.year != null && item.year!! >= thisYear - 1 && artist != null) "New from $artist" else "Album for you"
    }
    is PlaylistItem -> "Made for you"
    is ArtistItem -> "Artist for you"
    is SongItem -> "Song for you"
    else -> "For you"
}

/**
 * Twelve picks if there are that many, never fewer than five: the day's pick, then albums and
 * playlists from anywhere on home (newest albums first), then artists, then songs, then what you
 * played lately.
 */
private fun picksFrom(feed: HomeFeed, recent: List<SongItem>): List<YTItem> {
    val all = feed.sections.flatMap { it.items }
    val albums = all.filterIsInstance<AlbumItem>().sortedByDescending { it.year ?: 0 }
    val lists = all.filterIsInstance<PlaylistItem>()
    val artists = all.filterIsInstance<ArtistItem>()
    val songs = all.filterIsInstance<SongItem>()
    // Albums and playlists take turns, so it isn't a wall of one kind.
    val mixed = buildList {
        val a = albums.iterator()
        val p = lists.iterator()
        while (a.hasNext() || p.hasNext()) {
            if (a.hasNext()) add(a.next())
            if (p.hasNext()) add(p.next())
        }
    }
    val picks = (listOfNotNull(feed.hero?.item) + mixed + artists.take(2)).distinctBy { it.id }.take(12)
    if (picks.size >= 5) return picks
    return (picks + songs + recent).distinctBy { it.id }.take(12)
}

/** Home's rows, added to the list that scrolls under the top bar. */
fun LazyListScope.prismaHome(
    feed: HomeFeed,
    recent: List<SongItem>,
    playlists: List<PlaylistItem>,
    guest: Boolean,
    playItem: (YTItem, Boolean) -> Unit,
    openItem: (YTItem) -> Unit,
    playSongs: (String, List<SongItem>, Int) -> Unit,
    topBar: @Composable () -> Unit,
    /** Legacy puts 1.0's big banner on top instead, so it leaves these out. */
    showPicks: Boolean = true,
    /** Settings › Customize › Home: the rows that show, in order. */
    rows: List<String> = app.wave.music.data.HomeRows.parse(app.wave.music.data.HomeRows.DEFAULT).filter { it.second }.map { it.first },
    /** YouTube Music rows hidden by name. */
    hiddenSections: Set<String> = emptySet(),
    /** Radio for you. */
    stations: List<app.wave.music.data.Station> = emptyList(),
    onRadio: () -> Unit = {},
    /** Music videos for you. */
    videos: List<SongItem> = emptyList(),
    onVideos: () -> Unit = {},
) {
    item(key = "top") { topBar() }
    val playlistsShown = playlists.withoutLiked()
    // Radio for you right after the YouTube rows: it slots in after the third, not at the very end.
    val radioInFeed = rows.indexOf("radio").let { it > 0 && rows[it - 1] == "feed" }
    var first = true
    fun title(text: String, label: String? = null): @Composable () -> Unit {
        val isFirst = first
        first = false
        return { PrismaSectionTitle(text, label, first = isFirst) }
    }
    val radioRow: LazyListScope.() -> Unit = {
        if (stations.isNotEmpty()) {
            val t = title("Radio for you")
            item(key = "radio-h") { t() }
            item(key = "radio") {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(stations, key = { "st" + it.uuid }) { st -> app.wave.music.ui.radio.StationCard(st, squareSize()) { app.wave.music.ui.radio.playStation(st) } }
                    item(key = "radio-all") { app.wave.music.ui.radio.RadioMoreCard(squareSize(), onRadio) }
                }
            }
        }
    }
    rows.forEach { key ->
        when (key) {
            "picks" -> {
                val picks = if (showPicks) picksFrom(feed, recent) else emptyList()
                if (picks.isNotEmpty()) {
                    val t = title("Top picks for you")
                    item(key = "picks-h") { t() }
                    item(key = "picks") {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(picks, key = { "pick" + it.id }) { pick ->
                                PickCard(pick, onClick = { if (it is SongItem) playItem(it, false) else openItem(it) }, onPlay = { playItem(pick, false) })
                            }
                        }
                    }
                }
            }
            "recent" -> if (recent.isNotEmpty()) {
                val t = title("Recently played")
                item(key = "recent-h") { t() }
                item(key = "recent") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(recent, key = { "r" + it.id }) { song -> SquareCard(song) { playItem(song, false) } }
                    }
                }
            }
            "playlists" -> if (!guest && playlistsShown.isNotEmpty()) {
                val t = title("Your playlists")
                item(key = "mine-h") { t() }
                item(key = "mine") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(playlistsShown, key = { "p" + it.id }) { list -> SquareCard(list, onClick = openItem) }
                    }
                }
            }
            "radio" -> if (!radioInFeed) radioRow()
            "videos" -> if (videos.isNotEmpty()) {
                val t = title("Music videos")
                item(key = "videos-h") { t() }
                item(key = "videos") {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(videos, key = { "vid" + it.id }) { v -> app.wave.music.ui.video.VideoCard(v, 248.dp) { app.wave.music.ui.video.watchFrom(v, videos) } }
                        item(key = "videos-all") { app.wave.music.ui.video.VideoMoreCard(248.dp, onVideos) }
                    }
                }
            }
            "feed" -> {
                val shown = feed.sections.withIndex().filter { it.value.title !in hiddenSections }
                shown.forEachIndexed { n, (index, section) ->
                    if (radioInFeed && n == 3) radioRow()
                    val songs = section.items.filterIsInstance<SongItem>()
                    val t = title(section.title, section.label)
                    item(key = "s-h-$index") { t() }
                    if (songs.size == section.items.size && songs.size >= 4) {
                        // Song mixes: columns of four rows you swipe through.
                        item(key = "s-$index") {
                            val columns = songs.chunked(4)
                            LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                                items(columns.size) { col ->
                                    Column(Modifier.width(if (app.wave.music.platform.PrismaLook.dense) 280.dp else 312.dp)) {
                                        columns[col].forEachIndexed { row, song ->
                                            SongLine(song) { playSongs(section.title, songs, col * 4 + row) }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        item(key = "s-$index") {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(section.items, key = { "s$index" + it.id }) { entry -> SquareCard(entry, onClick = openItem) }
                            }
                        }
                    }
                }
                if (radioInFeed && shown.size <= 3) radioRow()
            }
        }
    }
}

@Composable
private fun PrismaSectionTitle(title: String, label: String? = null, first: Boolean = false, onClick: (() -> Unit)? = null) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 16.dp, top = if (first) 4.dp else if (app.wave.music.platform.PrismaLook.dense) 14.dp else 22.dp, bottom = if (app.wave.music.platform.PrismaLook.dense) 6.dp else 10.dp),
    ) {
        if (!label.isNullOrBlank()) Text(label.uppercase(), color = c.onBackground.copy(alpha = 0.6f), style = body(11.sp, FontWeight.Bold).copy(letterSpacing = 1.sp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = c.onBackground, style = display(21.sp, FontWeight.Bold, (-0.2).sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (onClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "See all", tint = c.onBackground.copy(alpha = 0.6f))
        }
    }
}

/**
 * A tall card: the art fills it, and its bottom part turns into a blur of the same art, darkened,
 * so the words over it always read.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PickCard(item: YTItem, onClick: (YTItem) -> Unit, onPlay: () -> Unit) {
    val menu = LocalItemMenu.current
    val art = item.thumbnail?.atSize(1080)
    Box(
        Modifier.tourTarget("pick")
            // Tablets: bigger picks, since there's room.
            .width((if (app.wave.music.platform.PrismaLook.dense) 176.dp else 224.dp) * (if (app.wave.music.platform.currentWindowDp().screenWidthDp >= 600) 1.25f else 1f))
            .height((if (app.wave.music.platform.PrismaLook.dense) 232.dp else 296.dp) * (if (app.wave.music.platform.currentWindowDp().screenWidthDp >= 600) 1.25f else 1f))
            .clip(RoundedCornerShape(app.wave.music.platform.PrismaLook.corner(26.dp)))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(onClick = { onClick(item) }, onLongClick = { menu.openFor(item) }),
    ) {
        AsyncImage(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        // The same art, blurred, showing only toward the bottom.
        AsyncImage(
            art,
            null,
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(Brush.verticalGradient(0.52f to Color.Transparent, 0.72f to Color.Black), blendMode = BlendMode.DstIn)
                }
                .blur(28.dp),
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))))
        Row(Modifier.align(Alignment.BottomStart).padding(14.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(captionFor(item), color = Color.White.copy(alpha = 0.82f), style = body(12.sp, FontWeight.Medium).copy(shadow = app.wave.music.ui.player.ReadableShadow), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.title, color = Color.White, style = display(19.sp, FontWeight.Bold, (-0.2).sp).copy(lineHeight = 22.sp, shadow = app.wave.music.ui.player.ReadableShadow), maxLines = 2, overflow = TextOverflow.Ellipsis)
                subtitle(item)?.let { Text(it, color = Color.White.copy(alpha = 0.75f), style = body(12.sp).copy(shadow = app.wave.music.ui.player.ReadableShadow), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            val tone = app.wave.music.ui.components.rememberArtColorScheme(item.thumbnail?.atSize(226), app.wave.music.ui.theme.darkBaseScheme())
            Box(
                Modifier
                    .padding(start = 8.dp)
                    .size(42.dp)
                    .pressable(RoundedCornerShape(app.wave.music.platform.PrismaLook.corner(16.dp)), tone.primary, pressedScale = 0.9f, onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, "Play", tint = tone.onPrimary) }
        }
    }
}

/** A square card with its name under it. Artists are round. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SquareCard(item: YTItem, onClick: (YTItem) -> Unit) {
    val c = MaterialTheme.colorScheme
    val menu = LocalItemMenu.current
    val round = item is ArtistItem
    Column(
        Modifier
            .width(squareSize())
            .combinedClickable(onClick = { onClick(item) }, onLongClick = { menu.openFor(item) }),
    ) {
        AsyncImage(
            item.thumbnail?.atSize(400),
            null,
            Modifier.size(squareSize()).clip(if (round) CircleShape else RoundedCornerShape(app.wave.music.platform.PrismaLook.corner(18.dp))).background(c.surfaceContainerHigh),
            contentScale = ContentScale.Crop,
        )
        Text(item.title, color = c.onBackground, style = body(14.sp, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        subtitle(item)?.let { Text(it, color = c.onBackground.copy(alpha = 0.6f), style = body(12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** A song in a mix: cover, title, artist, and its menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongLine(song: SongItem, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val menu = LocalItemMenu.current
    Row(
        Modifier.tourTarget("songrow")
            .fillMaxWidth()
            .height(if (app.wave.music.platform.PrismaLook.dense) 52.dp else 60.dp)
            .clip(RoundedCornerShape(app.wave.music.platform.PrismaLook.corner(14.dp)))
            .combinedClickable(onClick = onClick, onLongClick = { menu.openFor(song) })
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(song.thumbnail?.atSize(226), null, Modifier.size(48.dp).clip(RoundedCornerShape(app.wave.music.platform.PrismaLook.corner(12.dp))).background(c.surfaceContainerHigh), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f)) {
            Text(song.title, color = c.onBackground, style = body(15.sp, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artists.joinToString { it.name }, color = c.onBackground.copy(alpha = 0.6f), style = body(13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(36.dp).tourTarget("menu").menuAnchor(c.background, c.onBackground.copy(alpha = 0.6f)).clip(CircleShape).clickable { menu.openFor(song) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MoreVert, "More", tint = c.onBackground.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
        }
    }
}

// ---------------------------------------------------------------- Library

/**
 * Your library: the top bar and a title that scroll away, tiles for the main parts, your
 * playlists in a row (the arrow opens all of them), then what you played lately (the arrow opens
 * your history).
 */
@Composable
fun PrismaLibrary(
    session: Session,
    playlists: List<PlaylistItem>,
    recent: List<SongItem>,
    topPadding: Dp,
    bottomPadding: Dp,
    onOpen: (Destination) -> Unit,
    openItem: (YTItem) -> Unit,
    playItem: (YTItem, Boolean) -> Unit,
    onSignIn: () -> Unit,
    onNewPlaylist: () -> Unit,
    topBar: @Composable () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding)) {
        item { topBar() }
        item { Text("Library", color = c.onBackground, style = display(32.sp, FontWeight.Bold, (-0.6).sp), modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 14.dp)) }
        if (session.guest) {
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Your playlists, likes and history show up here once you're signed in.", color = c.onBackground.copy(alpha = 0.75f), style = body(15.sp))
                    Box(
                        Modifier
                            .height(44.dp)
                            .border(1.dp, c.outline, CircleShape)
                            .clip(CircleShape)
                            .clickable(onClick = onSignIn)
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Sign in", color = c.primary, style = body(15.sp, FontWeight.SemiBold)) }
                }
            }
            return@LazyColumn
        }
        item {
            // Two by two on phones, one row of four on tablets.
            val tiles: List<@Composable (Modifier) -> Unit> = listOf(
                { m -> LibraryTile(Icons.Rounded.Favorite, "Liked songs", c.primary, c.onPrimary, m) { onOpen(Destination.Playlist("LM")) } },
                { m -> LibraryTile(Icons.Rounded.LibraryMusic, "Playlists", c.secondaryContainer, c.onSecondaryContainer, m) { onOpen(Destination.Playlists) } },
                { m -> LibraryTile(Icons.Rounded.Album, "Albums & artists", c.tertiaryContainer, c.onTertiaryContainer, m) { onOpen(Destination.Saved) } },
                { m -> LibraryTile(Icons.Rounded.History, "History", c.surfaceContainerHighest, c.onSurface, m) { onOpen(Destination.History) } },
            )
            val perRow = if (app.wave.music.platform.currentWindowDp().screenWidthDp >= 600) 4 else 2
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tiles.chunked(perRow).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { tile -> tile(Modifier.weight(1f)) } }
                }
            }
        }
        item { PrismaSectionTitle("Your playlists", onClick = { onOpen(Destination.Playlists) }) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item { NewPlaylistCard(onNewPlaylist) }
                items(playlists.withoutLiked(), key = { "lp" + it.id }) { list -> SquareCard(list, onClick = openItem) }
            }
        }
        if (recent.isNotEmpty()) {
            item { PrismaSectionTitle("Played lately", onClick = { onOpen(Destination.History) }) }
            // Tablets: two or three columns side by side (by width), so the space isn't wasted.
            item(key = "lr-grid") {
                val width = app.wave.music.platform.currentWindowDp().screenWidthDp
                val columns = when { width >= 1100 -> 3; width >= 600 -> 2; else -> 1 }
                val shown = recent.take(8 * columns)
                Column {
                    shown.chunked(columns).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { song -> Box(Modifier.weight(1f)) { LibraryRow(song) { playItem(song, false) } } }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

/** The first card in your playlists: makes a new one. */
@Composable
private fun NewPlaylistCard(onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.width(128.dp).clickable(onClick = onClick)) {
        Box(
            Modifier.size(128.dp).clip(RoundedCornerShape(app.wave.music.platform.PrismaLook.corner(18.dp))).background(c.surfaceContainerHigh).border(1.dp, c.onSurface.copy(alpha = 0.1f), RoundedCornerShape(app.wave.music.platform.PrismaLook.corner(18.dp))),
            contentAlignment = Alignment.Center,
        ) { Icon(androidx.compose.material.icons.Icons.Rounded.Add, null, tint = c.primary, modifier = Modifier.size(40.dp)) }
        Text("New playlist", color = c.onBackground, style = body(14.sp, FontWeight.Medium), modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun LibraryTile(icon: ImageVector, label: String, fill: Color, ink: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(64.dp)
            .pressable(RoundedCornerShape(app.wave.music.platform.PrismaLook.corner(22.dp)), fill, pressedScale = 0.95f, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = ink, modifier = Modifier.size(24.dp))
        Text(label, color = ink, style = display(15.sp, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryRow(item: YTItem, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val menu = LocalItemMenu.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .clip(RoundedCornerShape(app.wave.music.platform.PrismaLook.corner(16.dp)))
            .combinedClickable(onClick = onClick, onLongClick = { menu.openFor(item) })
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(item.thumbnail?.atSize(226), null, Modifier.size(50.dp).clip(if (item is ArtistItem) CircleShape else RoundedCornerShape(app.wave.music.platform.PrismaLook.corner(12.dp))).background(c.surfaceContainerHigh), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f)) {
            Text(item.title, color = c.onBackground, style = body(15.sp, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle(item)?.let { Text(it, color = c.onBackground.copy(alpha = 0.6f), style = body(13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}


/** Liked songs has its own tile, so it isn't listed again with your playlists. */
private fun List<PlaylistItem>.withoutLiked() = filterNot { it.id == "LM" || it.id == "VLLM" || it.title.equals("Liked music", true) || it.title.equals("Liked songs", true) }


/**
 * The app's background with song colors on (Modern): the playing cover blurred until only its
 * colors are left, dimmed so everything on it reads. It stays still while pages scroll over it.
 */
@Composable
fun AppBackdrop(art: String?) {
    val c = MaterialTheme.colorScheme
    // Customize › Background: the whole cover (0), a glow down from the top (1), or nothing (2).
    val style = app.wave.music.data.WaveSettings.state.collectAsState().value.backdropStyle
    if (style == 2 || app.wave.music.platform.PrismaLook.saving) return
    androidx.compose.animation.Crossfade(art, animationSpec = tween(700), label = "backdrop") { a ->
        if (a != null && style == 1) app.wave.music.ui.components.BlurredImage(
            a.atSize(app.wave.music.ui.components.ArtSize.BLUR),
            Modifier.fillMaxWidth().height(520.dp).graphicsLayer { alpha = 0.55f; scaleX = 1.6f; scaleY = 1.3f },
            radius = 100.dp,
            fadeBottom = 0.75f,
        ) else if (a != null) Box(Modifier.fillMaxSize()) {
            app.wave.music.ui.components.BlurredImage(
                a.atSize(app.wave.music.ui.components.ArtSize.BLUR),
                Modifier.fillMaxSize().graphicsLayer { scaleX = 1.4f; scaleY = 1.4f },
                radius = 120.dp,
            )
            Box(Modifier.fillMaxSize().background(c.background.copy(alpha = 0.66f)))
        }
    }
}


/** Square cards: smaller on a dense home. */
private fun squareSize() = if (app.wave.music.platform.PrismaLook.dense) 104.dp else 128.dp
