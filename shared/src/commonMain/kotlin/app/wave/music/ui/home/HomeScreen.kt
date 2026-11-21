package app.wave.music.ui.home

import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import app.wave.music.ui.components.glass
import dev.chrisbanes.haze.hazeSource
import app.wave.music.platform.topBarPadding
import app.wave.music.platform.topInset
import app.wave.music.ui.components.WheelRow
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.shadow
import app.wave.music.ui.components.ArtSize
import app.wave.music.ui.components.BlurredImage
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.widthIn
import app.wave.music.platform.currentWindowDp
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.Close
import app.wave.music.data.Updater
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wave.music.data.HomeFeed
import app.wave.music.data.HomeRepository
import app.wave.music.data.Session
import app.wave.music.playback.PlayRequest
import app.wave.music.playback.PlayerConnection
import app.wave.music.playback.QueueBuilder
import app.wave.music.playback.ResumeStore
import app.wave.music.ui.components.ItemCard
import app.wave.music.ui.components.ProgressiveBackdropBlur
import app.wave.music.ui.components.ProgressiveBlurImage
import app.wave.music.ui.components.backdropSource
import app.wave.music.ui.components.rememberBackdrop
import app.wave.music.ui.components.WaveMark
import app.wave.music.ui.components.subtitle
import app.wave.music.ui.components.atSize
import app.wave.music.ui.components.rememberArtColorScheme
import app.wave.music.ui.player.MiniPlayer
import app.wave.music.ui.player.NowPlayingScreen
import app.wave.music.ui.search.SearchScreen
import app.wave.music.data.LibraryRepository
import app.wave.music.data.WaveSettings
import app.wave.music.playback.BeatBus
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import app.wave.music.ui.menu.ItemMenuHost
import app.wave.music.ui.menu.LocalItemMenu
import app.wave.music.ui.menu.MenuActions
import app.wave.music.ui.menu.MenuOpener
import app.wave.music.ui.menu.MenuTarget
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material.icons.rounded.Add
import app.wave.music.ui.detail.Destination
import app.wave.music.ui.detail.DetailScreen
import app.wave.music.ui.detail.PageActions
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import app.wave.music.ui.components.LocalSharedTransition
import app.wave.music.ui.components.LocalPeeking
import app.wave.music.ui.components.rememberCovered
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.draw.drawWithContent
import app.wave.music.ui.components.MorphVisibility
import app.wave.music.ui.components.WaveMotion
import app.wave.music.ui.components.pressable
import app.wave.music.ui.components.sharedContainer
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.mutableStateListOf
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.pages.HomePage
import kotlinx.coroutines.launch
import java.time.LocalTime

private sealed interface HomeUi {
    data object Loading : HomeUi
    data object Failed : HomeUi
    data class Ready(val feed: HomeFeed) : HomeUi
}

/** Everything the top of home needs, whichever of the three cases we're in. */
private class HeroContent(
    val label: String,
    val title: String,
    val art: String?,
    val detail: String?,
    val progress: Float?,
    val primary: Pair<String, ImageVector>,
    val onPrimary: () -> Unit,
    val secondary: Pair<String, ImageVector>,
    val onSecondary: () -> Unit,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class, androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(session: Session, onSignOut: () -> Unit) {
    val scope = rememberCoroutineScope()
    var ui by remember(session) { mutableStateOf<HomeUi>(HomeUi.Loading) }
    var attempt by remember { mutableIntStateOf(0) }
    var askSignOut by remember { mutableStateOf(false) }
    val desktop = app.wave.music.ui.components.LocalDesktop.current
    val settings by WaveSettings.state.collectAsState()
    LaunchedEffect(session) { PlayerConnection.loadLikes() }
    LaunchedEffect(Unit) {
        WaveSettings.load()
        if (WaveSettings.state.value.checkUpdates) Updater.check()
    }
    var showPlayer by rememberSaveable { mutableStateOf(RenderStart.player) }
    // Asked to open from home's banner; handled once the player's layout state exists below.
    var openRequested by remember { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    // The whole app, so menus can blur whatever is behind them.
    val appHaze = dev.chrisbanes.haze.rememberHazeState()
    // What you just picked, shown in the dock right away (with a loading play button) until it plays.
    var pendingSong by remember { mutableStateOf<app.wave.music.playback.NowPlaying?>(null) }
    // Customize › Open Wave on: Home, Library or Search.
    var showSearch by rememberSaveable { mutableStateOf(WaveSettings.state.value.startTab == 2) }
    // Prisma: Home (0), Library (1) or Search (2), from the bottom bar.
    var tab by rememberSaveable { mutableIntStateOf(WaveSettings.state.value.startTab) }
    LaunchedEffect(showSearch) { if (showSearch) tab = 2 else if (tab == 2) tab = 0 }
    // Back on Library goes home first, instead of closing Wave (pages, search and the player
    // handle their own back before this).
    androidx.compose.ui.backhandler.BackHandler(enabled = tab == 1 && !showSearch) { tab = 0 }
    // Ctrl+F or "/" on a keyboard opens search.
    LaunchedEffect(Unit) { app.wave.music.ui.components.Shortcuts.openSearch.collect { showSearch = true } }
    val player by PlayerConnection.state.collectAsState()
    val playedHere by app.wave.music.data.RecentPlays.songs.collectAsState()
    val resume by remember { ResumeStore.observe() }.collectAsState(null)

    LaunchedEffect(session, attempt) {
        if (ui !is HomeUi.Ready) ui = HomeUi.Loading
        // First page shows as soon as it lands; more sections fill in underneath.
        runCatching { HomeRepository.feed(refresh = attempt > 0).collect { ui = HomeUi.Ready(it) } }
            .onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                if (ui !is HomeUi.Ready) ui = HomeUi.Failed
            }
    }

    fun start(build: suspend () -> Result<PlayRequest>, startPositionMs: Long = 0, pending: app.wave.music.playback.NowPlaying? = null) {
        scope.launch {
            preparing = true
            pendingSong = pending
            build()
                .onSuccess { PlayerConnection.play(it, startPositionMs) }
                .onFailure { app.wave.music.ui.components.Notices.show("Couldn't play that right now") }
            preparing = false
            pendingSong = null
        }
    }
    val playItem: (YTItem, Boolean) -> Unit = { item, shuffle -> start({ QueueBuilder.forItem(item, shuffle) }, pending = pendingFor(item)) }
    val stack = remember { mutableStateListOf<Destination>().apply { RenderStart.page?.let { add(it) } } }
    // Which way pages move: deeper slides in from the right, back slides in from the left.
    var forward by remember { mutableStateOf(true) }
    fun push(dest: Destination) {
        forward = true
        stack.add(dest)
    }
    // Each open page keeps its scroll spot while it's in the stack; leaving a page for good drops it.
    val pageStates = rememberSaveableStateHolder()
    fun pop() {
        forward = false
        if (stack.isEmpty()) return
        val gone = stack.removeAt(stack.lastIndex)
        if (gone !in stack) pageStates.removeState(gone.toString())
    }
    // Songs play; artists, albums and playlists open their page.
    val openItem: (YTItem) -> Unit = { item -> Destination.of(item)?.let { push(it) } ?: playItem(item, false) }
    // A YouTube Music link opened in (or shared to) Wave: songs play, pages open.
    val link by app.wave.music.data.Links.pending.collectAsState()
    LaunchedEffect(link) {
        val opened = link ?: return@LaunchedEffect
        app.wave.music.data.Links.consume()
        showSearch = false
        when (opened) {
            is app.wave.music.data.Link.Song -> start({
                val song = app.wave.music.data.SongLinks.lookup(opened.videoId).await()
                    ?: SongItem(id = opened.videoId, title = "", artists = emptyList(), thumbnail = "")
                QueueBuilder.forItem(song)
            })
            // Mixes and radios play; real playlists open their page.
            is app.wave.music.data.Link.Playlist -> if (opened.id.startsWith("RD")) {
                start({ QueueBuilder.forItem(PlaylistItem(opened.id, "Mix", null, null, null, null, null, null)) })
            } else {
                showPlayer = false
                push(Destination.Playlist(opened.id))
            }
            is app.wave.music.data.Link.Album -> { showPlayer = false; push(Destination.Album(opened.browseId)) }
            is app.wave.music.data.Link.Artist -> { showPlayer = false; push(Destination.Artist(opened.channelId)) }
        }
    }
    var menuTarget by remember { mutableStateOf<MenuTarget?>(RenderStart.menu) }
    // A menu opened on the playing song moves on with the music (its colors and name too).
    LaunchedEffect(player.current?.videoId) {
        val open = menuTarget as? MenuTarget.Song ?: return@LaunchedEffect
        val now = player.current ?: return@LaunchedEffect
        if (open.nowPlaying && open.videoId != now.videoId) {
            menuTarget = MenuTarget.Song(now.videoId, now.title, now.artist, now.artworkUrl, album = now.album, nowPlaying = true)
        }
    }
    var createPlaylist by remember { mutableStateOf(false) }
    val myPlaylists by LibraryRepository.playlists.collectAsState()
    LaunchedEffect(session) { LibraryRepository.refresh() }
    val addToQueue: (YTItem) -> Unit = { item ->
        scope.launch {
            val request = if (item is SongItem) Result.success(QueueBuilder.single(item)) else QueueBuilder.forItem(item)
            request.onSuccess {
                PlayerConnection.addToQueue(it)
                app.wave.music.ui.components.Notices.show("Added to queue", Icons.AutoMirrored.Rounded.QueueMusic)
            }
        }
    }
    val playNext: (YTItem) -> Unit = { item ->
        scope.launch {
            val request = if (item is SongItem) Result.success(QueueBuilder.single(item)) else QueueBuilder.forItem(item)
            request.onSuccess {
                PlayerConnection.playNext(it)
                app.wave.music.ui.components.Notices.show("Playing next", Icons.Rounded.PlayArrow)
            }
        }
    }

    val feed = (ui as? HomeUi.Ready)?.feed
    val current = player.current
    val ctx = player.context
    val saved = resume
    // How far through the queue you are, in the order it actually plays (shuffle included).
    val playedSoFar = (player.playOrder.indexOf(player.index).takeIf { it >= 0 } ?: player.index) + 1
    // Top of home, in priority order: what's playing → an unfinished playlist → today's #1 pick.
    val hero: HeroContent? = when {
        current != null && ctx != null -> HeroContent(
            label = "PLAYING NOW",
            title = ctx.title,
            art = (ctx.artworkUrl ?: current.artworkUrl)?.atSize(1080),
            detail = "Song $playedSoFar of ${player.queue.size} · ${current.title}",
            progress = if (player.queue.isNotEmpty()) playedSoFar.toFloat() / player.queue.size else null,
            primary = "Open player" to Icons.AutoMirrored.Rounded.QueueMusic,
            onPrimary = { openRequested = true },
            secondary = (if (player.shuffle) "Remix on" else "Remix") to Icons.Rounded.Shuffle,
            onSecondary = { PlayerConnection.toggleShuffle() },
        )
        saved != null && saved.isUnfinished -> {
            val days = ((System.currentTimeMillis() - saved.updatedAt) / 86_400_000L).toInt()
            val ago = when (days) { 0 -> "today"; 1 -> "yesterday"; else -> "$days days ago" }
            HeroContent(
                label = "PICK UP WHERE YOU LEFT OFF",
                title = saved.title,
                art = saved.artworkUrl?.atSize(1080),
                detail = "Stopped at song ${saved.index + 1} of ${saved.total} · $ago",
                progress = (saved.index + 1f) / saved.total,
                primary = "Resume at song ${saved.index + 1}" to Icons.Rounded.PlayArrow,
                onPrimary = { start({ QueueBuilder.forResume(saved) }, saved.positionMs) },
                secondary = "Start over" to Icons.Rounded.RestartAlt,
                onSecondary = { start({ QueueBuilder.forResume(saved.copy(index = 0)) }) },
            )
        }
        feed?.hero != null -> {
            val pick = feed.hero.item
            HeroContent(
                label = "YOUR #1 PICK TODAY",
                title = pick.title,
                art = pick.thumbnail?.atSize(1080),
                detail = subtitle(pick),
                progress = null,
                primary = "Play" to Icons.Rounded.PlayArrow,
                onPrimary = { playItem(pick, false) },
                secondary = "Shuffle" to Icons.Rounded.Shuffle,
                onSecondary = { playItem(pick, true) },
            )
        }
        else -> null
    }

    // The whole page takes its colors from the hero art.
    val peeking = remember { mutableStateOf(false) }
    // Big windows get the big-screen layout: a player bar along the bottom, grids instead of
    // sideways rows. Narrow ones (a computer window dragged small, phones, a Fold's cover screen)
    // get the phone layout, so nothing is ever squeezed.
    val conf = currentWindowDp()
    // Wave Prisma: tablets use the whole new interface (the dock, Prisma's pages and player),
    // sized for the bigger screen, instead of the old big-screen layout.
    val tabletShell = false
    // The computer layout was retired with the computer app.
    val desktopShell = false
    val shell = desktopShell || tabletShell
    // Prisma's home, library, search and backgrounds: phones and tablets. Tablets keep their own
    // player bar along the bottom and the player docking beside the app.
    val prisma = !desktopShell
    // Legacy: home, its header and search go back to Wave 1.0's (a modern take on them).
    val legacyHome = prisma && settings.legacyUi
    val prismaHomeUi = prisma && !settings.legacyUi
    // Top search: the search bar stays pinned at the top while home and library scroll under it.
    val pinnedSearch = prismaHomeUi && (settings.topSearch || shell)
    // The player can sit beside the app: drag the player bar up to the left or right half (tablets,
    // and computers with room for both). Clicking it opens the player over everything.
    // Docking the player beside the app was for the old big-screen layout.
    val canDock = false
    // Which side it docks on: -1 left, 1 right, 0 none (it opens full).
    var dockSide by rememberSaveable { mutableIntStateOf(0) }
    var playerFull by rememberSaveable { mutableStateOf(false) }
    val wouldDock = canDock && dockSide != 0 && !playerFull && player.current != null
    val docked = wouldDock && showPlayer
    // How much of the window the docked player takes. Only a few sizes, so neither side is ever
    // caught mid-resize looking broken; dragging the divider jumps between them.
    var split by rememberSaveable { mutableFloatStateOf(0.5f) }
    // Opened by dropping the player bar on a side: it slides in from there instead of growing.
    var droppedIn by remember { mutableStateOf(false) }
    // Where the player bar is being dragged (tablets), to show where it can be dropped.
    // Which panel the player should open to (from the player bar's Lyrics / Up next buttons).
    var playerPanel by remember { mutableStateOf<String?>(null) }
    val dockSpring = spring<Float>(dampingRatio = 0.86f, stiffness = 360f)
    val homeWidth = animateFloatAsState(if (docked) 1f - split else 1f, dockSpring, label = "dock")
    // While the player is closed its size just jumps to whatever it'll open at.
    val playerWidth = animateFloatAsState(if (wouldDock) split else 1f, if (showPlayer) dockSpring else snap(), label = "dockPlayer")
    fun closePlayer() {
        // The side stays as it was while the player leaves, so it slides away from where it is
        // instead of stretching across the window on its way out.
        showPlayer = false
        playerFull = false
    }
    fun openPlayer(panel: String?) {
        playerPanel = panel
        droppedIn = false
        // Tapping (or clicking) the player bar opens it full; drag the bar to a side to dock it.
        if (shell) dockSide = 0
        showPlayer = true
    }
    LaunchedEffect(openRequested) {
        if (openRequested) {
            openRequested = false
            openPlayer(null)
        }
    }
    val pageLook = remember { app.wave.music.ui.components.PageLook() }
    val artScheme = rememberArtColorScheme(if (settings.matchSongColors) player.current?.artworkUrl ?: (if (shell) hero?.art else null) else null)
    val appScheme = if (settings.amoled && artScheme.background.luminance() < 0.5f) artScheme.copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0B0B0B),
        surfaceContainer = Color(0xFF121212),
        surfaceContainerHigh = Color(0xFF1B1B1B),
        surfaceContainerHighest = Color(0xFF242424),
    ) else if (prisma && !settings.legacyUi && player.current != null && settings.matchSongColors && artScheme.background.luminance() < 0.5f) {
        // Phones: a calmer, mostly neutral dark instead of a strong tint (a green page for a green
        // cover); the cover's own colors come through as a soft glow behind home instead.
        val calm = Color(0xFF0E0F12)
        artScheme.copy(
            background = androidx.compose.ui.graphics.lerp(artScheme.background, calm, 0.6f),
            surface = androidx.compose.ui.graphics.lerp(artScheme.surface, calm, 0.6f),
        )
    } else artScheme
    val pageCovers = rememberCovered(stack.isNotEmpty())
    val playerCovers = rememberCovered(showPlayer && player.current != null && !docked)
    val searchCovers = rememberCovered(showSearch)

    // Everything (menus, settings, sheets too) takes the song's colors when that's turned on.
    MaterialTheme(colorScheme = appScheme) {
    // Remember where each click lands, so a computer's dropdown menus open right under the pointer.
    SharedTransitionLayout(
        Modifier.hazeSource(appHaze).pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Press) {
                        event.changes.firstOrNull()?.let { app.wave.music.ui.menu.PointerSpot.position = it.position }
                    }
                }
            }
        },
    ) {
    CompositionLocalProvider(
        LocalItemMenu provides MenuOpener { menuTarget = it },
        LocalSharedTransition provides this,
        LocalPeeking provides peeking,
        // The mini player floats over the bottom: pages keep their buttons clear of it.
        app.wave.music.ui.components.LocalMiniPlayerSpace provides if (shell || showPlayer) 0.dp else if (!settings.legacyUi && settings.dockOneRow && conf.screenWidthDp >= 840 && !settings.topSearch) PrismaBarHeight + 20.dp else (if (player.current != null || pendingSong != null) PrismaDockSongHeight + 28.dp else 20.dp) + (if (settings.topSearch) 0.dp else PrismaBarHeight),
        app.wave.music.ui.components.LocalShell provides shell,
        app.wave.music.ui.components.LocalPageLook provides pageLook,
    ) {
    // Colors follow the song that's playing; otherwise the art at the top of home.
    MaterialTheme(colorScheme = appScheme) {
        val c = MaterialTheme.colorScheme
        // What darkens (dark mode) or lightens (light mode) a busy picture under text.
        val scrim = if (c.background.luminance() < 0.5f) Color.Black else Color.White
        val backdrop = rememberBackdrop()
        val barHaze = dev.chrisbanes.haze.rememberHazeState()
        val listState = rememberLazyListState()
        val greetingAlpha by remember {
            derivedStateOf {
                if (listState.firstVisibleItemIndex > 0) 0f else (1f - listState.firstVisibleItemScrollOffset / 260f).coerceIn(0f, 1f)
            }
        }
        val statusTop = topInset()

        // A page on top (an artist, an album) brings its own art and colors to the sidebar (and the
        // top of the window) too.
        val page = pageLook.current?.takeIf { stack.isNotEmpty() || showSearch }
        // Fades between home's colors and the page's, never jumps.
        val sideScheme = app.wave.music.ui.components.animateScheme(page?.scheme ?: c)
        val sideBackground = sideScheme.background
        BoxWithConstraints(Modifier.fillMaxSize().background(sideBackground)) {
        // With the player docked, the app reflows into the rest of the window. It reaches a little
        // under the player's rounded edge, so the corners show the app, not an empty gap.
        val tuck = with(LocalDensity.current) { 32.dp.toPx() }
        Column(
            Modifier
                .fillMaxHeight()
                .layout { measurable, cons ->
                    val f = homeWidth.value
                    val w = (cons.maxWidth * f + tuck * ((1f - f) / 0.3f).coerceIn(0f, 1f)).roundToInt().coerceIn(1, cons.maxWidth)
                    val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w, cons.maxHeight))
                    layout(cons.maxWidth, cons.maxHeight) { placeable.place(if (dockSide < 0) cons.maxWidth - w else 0, 0) }
                },
        ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
        // Computers: the playing song's art, blurred, across the top of the window, behind the
        // sidebar and home alike, fading into the solid background below (like home on a phone).
        // It scrolls away with home.
        if (desktopShell) {
            val ambient = page?.art ?: player.current?.artworkUrl ?: (ui as? HomeUi.Ready)?.feed?.recent?.firstOrNull()?.thumbnail ?: hero?.art
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(640.dp)
                    .graphicsLayer {
                        val onHome = stack.isEmpty() && !showSearch
                        translationY = if (!onHome) 0f
                        else if (listState.firstVisibleItemIndex > 0) -size.height
                        else -listState.firstVisibleItemScrollOffset.toFloat()
                    },
            ) {
                Crossfade(ambient?.atSize(ArtSize.BLUR), Modifier.matchParentSize(), animationSpec = tween(500), label = "ambient") { a ->
                    BlurredImage(a, Modifier.fillMaxSize().clipToBounds().graphicsLayer { scaleX = 1.25f; scaleY = 1.25f }, radius = 96.dp, fadeBottom = 0.45f)
                }
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to scrim.copy(alpha = 0.28f), 0.5f to sideBackground.copy(alpha = 0.35f), 1f to sideBackground)))
            }
        }
        // Docked: the app's content keeps clear of the player's edge (the strip tucked under its
        // corner plus some breathing room), whichever side the player is on.
        val clearStart by androidx.compose.animation.core.animateDpAsState(if (docked && dockSide < 0) 44.dp else 0.dp, tween(260, easing = WaveMotion.Emphasized), label = "clearStart")
        val clearEnd by androidx.compose.animation.core.animateDpAsState(if (docked && dockSide > 0) 44.dp else 0.dp, tween(260, easing = WaveMotion.Emphasized), label = "clearEnd")
        Row(Modifier.fillMaxSize().padding(start = clearStart, end = clearEnd)) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            // Home (feed + frosted header) stops drawing while a page, search or the player fully
            // covers it, and is drawn again the moment a back swipe could reveal it.
            val homeHidden = (pageCovers || playerCovers || searchCovers) && !peeking.value
            Box(Modifier.fillMaxSize().then(if (prisma) Modifier.hazeSource(barHaze) else Modifier)) {
            Box(Modifier.fillMaxSize().drawWithContent { if (!homeHidden) drawContent() }) {
            // Modern with song colors: the whole background is the playing cover, blurred to nothing but color.
            if (prisma && !settings.legacyUi && settings.matchSongColors) AppBackdrop(player.current?.artworkUrl)
            // Home and Library cross over with a little lift instead of swapping in place.
            AnimatedContent(
                prisma && tab == 1,
                transitionSpec = {
                    (fadeIn(tween(240, delayMillis = 40)) + scaleIn(tween(300, easing = WaveMotion.EmphasizedDecelerate), initialScale = 0.97f)) togetherWith
                        fadeOut(tween(140))
                },
                label = "tab",
            ) { showLibrary ->
            if (showLibrary && legacyHome) {
                // Legacy: a library in Wave 1.0's style (1.0 never had one, so this is what it would have been).
                LegacyLibrary(
                    session = session,
                    playlists = myPlaylists,
                    recent = (playedHere + ((ui as? HomeUi.Ready)?.feed?.recent ?: emptyList())).distinctBy { it.id },
                    topPadding = statusTop + 4.dp,
                    bottomPadding = 150.dp + PrismaBarHeight,
                    onOpen = { push(it) },
                    openItem = openItem,
                    playItem = playItem,
                    onNewPlaylist = { createPlaylist = true },
                    topBar = { PrismaTopBar(session = session, onAvatar = { push(Destination.Settings) }, search = { showSearch = true }, inLibrary = true, onLibrary = { tab = 0 }) },
                )
            } else if (showLibrary) {
                PrismaLibrary(
                    session = session,
                    playlists = myPlaylists,
                    recent = (playedHere + ((ui as? HomeUi.Ready)?.feed?.recent ?: emptyList())).distinctBy { it.id },
                    topPadding = statusTop + 4.dp,
                    bottomPadding = 150.dp + PrismaBarHeight,
                    onOpen = { push(it) },
                    openItem = openItem,
                    playItem = playItem,
                    onSignIn = onSignOut,
                    onNewPlaylist = { createPlaylist = true },
                    topBar = { if (pinnedSearch) Spacer(Modifier.height(PrismaTopHeight + 14.dp)) else if (legacyHome) PrismaTopBar(session = session, onAvatar = { push(Destination.Settings) }, search = { showSearch = true }, inLibrary = tab == 1, onLibrary = { tab = if (tab == 1) 0 else 1 }) else PrismaTopBar(session = session, onAvatar = { push(Destination.Settings) }) },
                )
            } else
            // Loading, failure and the feed fade into each other instead of snapping.
            AnimatedContent(
                targetState = ui,
                contentKey = { it::class },
                transitionSpec = { fadeIn(tween(260, delayMillis = 40)) togetherWith fadeOut(tween(120)) },
                label = "home",
            ) { state ->
            Box(Modifier.fillMaxSize()) {
            when (state) {
                HomeUi.Loading -> LoadingIndicator(Modifier.size(80.dp).align(Alignment.Center))
                HomeUi.Failed -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Couldn't load your home", style = MaterialTheme.typography.titleMedium, color = c.onBackground)
                    Button(onClick = { attempt++ }) { Text("Try again") }
                }
                is HomeUi.Ready -> LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .backdropSource(backdrop),
                    contentPadding = if (!prismaHomeUi) PaddingValues(bottom = if (legacyHome && !shell) 150.dp + PrismaBarHeight else 140.dp) else PaddingValues(top = statusTop + 4.dp, bottom = if (shell) 140.dp else 150.dp + PrismaBarHeight),
                ) {
                  if (prismaHomeUi) {
                    prismaHome(
                        feed = state.feed,
                        recent = (playedHere + state.feed.recent).distinctBy { it.id }.take(16),
                        playlists = myPlaylists,
                        guest = session.guest,
                        playItem = playItem,
                        openItem = openItem,
                        playSongs = { title, songs, index -> QueueBuilder.forSongs(title, songs, false).let { r -> PlayerConnection.play(app.wave.music.playback.PlayRequest(r.items, index, r.context, false)) } },
                        topBar = { if (pinnedSearch) Spacer(Modifier.height(PrismaTopHeight + 14.dp)) else if (legacyHome) PrismaTopBar(session = session, onAvatar = { push(Destination.Settings) }, search = { showSearch = true }, inLibrary = tab == 1, onLibrary = { tab = if (tab == 1) 0 else 1 }) else PrismaTopBar(session = session, onAvatar = { push(Destination.Settings) }) },
                    )
                  } else if (legacyHome) {
                    // Legacy: 1.0's greeting header and big banner on top, then Prisma's sections
                    // (in Legacy's fonts and squarer corners) underneath: old and new together.
                    item { Hero(hero, jump = emptyList(), onJump = openItem, onSharedBackdrop = false, modernButtons = true) }
                    prismaHome(
                        feed = state.feed,
                        recent = (playedHere + state.feed.recent).distinctBy { it.id }.take(16),
                        playlists = myPlaylists,
                        guest = session.guest,
                        playItem = playItem,
                        openItem = openItem,
                        playSongs = { title, songs, index -> QueueBuilder.forSongs(title, songs, false).let { r -> PlayerConnection.play(app.wave.music.playback.PlayRequest(r.items, index, r.context, false)) } },
                        topBar = {},
                        showPicks = false,
                    )
                  } else {
                    item {
                        // Big screens: quick "jump back in" tiles sit beside the banner, filling the space.
                        val jump = if (shell) myPlaylists.take(6).ifEmpty { state.feed.recent.take(6) } else emptyList()
                        Hero(hero, jump = jump, onJump = openItem, onSharedBackdrop = desktopShell)
                    }
                    // Guests have no playlists to show (or make).
                    if (!shell && !session.guest) {
                        item { SectionHeader("Your library", "Your playlists") }
                        item {
                            WheelRow {
                                item { NewPlaylistTile { createPlaylist = true } }
                                items(myPlaylists, key = { it.id }) { ItemCard(it, onClick = openItem) }
                            }
                        }
                    }
                    // What you played in Wave just now comes first; YouTube's history fills in the rest.
                    val recent = (playedHere + state.feed.recent).distinctBy { it.id }.take(16)
                    if (recent.isNotEmpty()) {
                        item { SectionHeader("Your recent spins", "Recently played") }
                        item {
                            WheelRow {
                                items(recent, key = { it.id }) { song -> ItemCard(song) { playItem(it, false) } }
                            }
                        }
                    }
                    state.feed.sections.forEachIndexed { index, section ->
                        item(key = "header-$index") { SectionHeader(section.label, section.title) }
                        item(key = "row-$index") {
                            SectionRow(
                                section = section,
                                shapeIndex = index,
                                grid = shell,
                                onPlayAll = { shuffle ->
                                    val songs = section.items.filterIsInstance<SongItem>()
                                    if (songs.size == section.items.size) {
                                        PlayerConnection.play(QueueBuilder.forSongs(section.title, songs, shuffle))
                                    } else {
                                        section.items.firstOrNull()?.let { playItem(it, true) }
                                    }
                                },
                                onItem = openItem,
                            )
                        }
                    }
                  }
                }
            }
            }
            }
            }

            // Sticky header: a frosted band that's blurriest at the very top and fades out downward,
            // so the search bar and greeting always read cleanly over the art.
            // Real backdrop blur behind the sticky header: fully frosted behind the search bar and
            // greeting, then easing off in stages until the content is sharp again.
            // A shorter frosted band on short (landscape) screens, so it doesn't cover the page.
            // Computers: just behind the search bar (the art banner sits right under it and stays sharp).
            val headerBand = when {
                desktopShell -> 96.dp
                !shell -> 150.dp
                currentWindowDp().screenHeightDp < 600 -> 150.dp
                else -> 230.dp
            }
            if (prismaHomeUi) {
                // A little shade under the status bar (the pinned search bar brings its own glass).
                if (!pinnedSearch) Box(
                    Modifier
                        .fillMaxWidth()
                        .height(statusTop + 64.dp)
                        .background(Brush.verticalGradient(0f to (if (c.background.luminance() < 0.5f) Color.Black.copy(alpha = 0.6f) else c.background.copy(alpha = 0.85f)), 1f to Color.Transparent)),
                )
            } else if (!(legacyHome && tab == 1)) ProgressiveBackdropBlur(
                backdrop = backdrop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusTop + headerBand),
            )
            // Tint on top of the blur so white text always has contrast.
            if (!prismaHomeUi && !(legacyHome && tab == 1)) Box(
                Modifier
                    .fillMaxWidth()
                    .height(statusTop + headerBand)
                    .background(
                        // Computers: the shared art background already carries the header (and the
                        // sidebar beside it); a tint here would set home apart from the sidebar.
                        if (desktopShell) {
                            androidx.compose.ui.graphics.SolidColor(Color.Transparent)
                        } else {
                            Brush.verticalGradient(
                                0f to c.background.copy(alpha = 0.72f),
                                0.6f to c.background.copy(alpha = 0.45f),
                                1f to Color.Transparent,
                            )
                        },
                    ),
            )
            if (prismaHomeUi || (legacyHome && tab == 1)) {
                // Prisma: the top bar is the first row of the page and scrolls away with it.
            } else
            Column(
                Modifier
                    .topBarPadding()
                    .widthIn(max = 760.dp)
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                // Legacy (phones): the modern search bar, opening your library too.
                if (legacyHome) PrismaTopBar(
                    session = session,
                    onAvatar = { push(Destination.Settings) },
                    search = { showSearch = true },
                    inLibrary = tab == 1,
                    onLibrary = { tab = if (tab == 1) 0 else 1 },
                    edge = 0.dp,
                ) else
                // The search bar opens into the search page: it morphs into the search field.
                Box(Modifier.fillMaxWidth().height(56.dp)) {
                MorphVisibility(!showSearch) {
                Row(
                    Modifier
                        .sharedContainer("search", this, CircleShape)
                        .fillMaxWidth()
                        .height(56.dp)
                        .pressable(CircleShape, c.onBackground.copy(alpha = 0.1f), pressedScale = 0.97f) { showSearch = true }
                        .border(1.dp, c.onBackground.copy(alpha = 0.14f), CircleShape)
                        .padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // The sidebar carries the Wave mark on big screens.
                    if (!shell) WaveMark(c.onBackground)
                    Text(
                        "Search songs, artists, albums",
                        color = c.onBackground.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    // Phones and tablets: the account panel slides up. Computers: a dropdown right here.
                    val avatar = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { push(Destination.Settings) }
                    Box {
                        if (session.photoUrl != null) {
                            AsyncImage(session.photoUrl, "Your Google account", avatar, contentScale = ContentScale.Crop)
                        } else {
                            Icon(Icons.Rounded.AccountCircle, "Your Google account", avatar, tint = c.onBackground)
                        }
                    }
                }
                }
                }
                Column(
                    Modifier
                        .padding(top = 18.dp, start = 4.dp)
                        .alpha(greetingAlpha),
                ) {
                    Text(greeting().let { if (session.guest) it.removeSuffix(",") else it }, color = c.onBackground.copy(alpha = 0.88f), fontSize = 22.sp, fontWeight = FontWeight.Medium)
                    if (!session.guest) Text(session.name.substringBefore(' '), color = c.onBackground, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            }

            FadeVisibility(
                visible = showSearch,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(150, delayMillis = 70)),
                // Same for search when a page opened from it covers it.
                modifier = Modifier.drawWithContent { if (!(pageCovers && !peeking.value)) drawContent() },
            ) {
                if (prismaHomeUi) PrismaSearch(
                    session = session,
                    onClose = { showSearch = false },
                    onAvatar = { showSearch = false; push(Destination.Settings) },
                    onPlay = playItem,
                    onOpen = openItem,
                    bottomPadding = 150.dp + PrismaBarHeight,
                ) else SearchScreen(
                    onClose = { showSearch = false },
                    onPlay = playItem,
                    onPlayNext = playNext,
                    onOpen = openItem,
                    ambientArt = player.current?.artworkUrl ?: hero?.art,
                    fieldModifier = Modifier.sharedContainer("search", this, CircleShape),
                )
            }

            AnimatedContent(
                targetState = stack.lastOrNull(),
                transitionSpec = {
                    // Deeper: the new page slides in over the old one, which sinks back a touch.
                    // Back: the page slides off to the right, and the one under it rises back up.
                    if (forward) {
                        ((slideInHorizontally(tween(340, easing = WaveMotion.EmphasizedDecelerate)) { it / 3 } + fadeIn(tween(200, delayMillis = 20))) togetherWith
                            (scaleOut(tween(300, easing = WaveMotion.Emphasized), targetScale = 0.94f) + fadeOut(tween(220)))).apply { targetContentZIndex = 1f }
                    } else {
                        ((scaleIn(tween(320, easing = WaveMotion.EmphasizedDecelerate), initialScale = 0.94f) + fadeIn(tween(220))) togetherWith
                            (slideOutHorizontally(tween(260, easing = WaveMotion.EmphasizedAccelerate)) { it / 3 } + fadeOut(tween(220, delayMillis = 40)))).apply { targetContentZIndex = -1f }
                    } using SizeTransform(clip = false)
                },
                label = "pages",
            ) { dest ->
                if (dest != null) pageStates.SaveableStateProvider(dest.toString()) {
                    DetailScreen(
                        dest,
                        active = transition.targetState == androidx.compose.animation.EnterExitState.Visible,
                        actions = PageActions(
                            play = { PlayerConnection.play(it) },
                            playItem = playItem,
                            playNext = playNext,
                            open = openItem,
                            back = ::pop,
                            createPlaylist = { createPlaylist = true },
                            navigate = { push(it) },
                            signOut = onSignOut,
                        ),
                    )
                }
            }

            }
            // The pinned search bar, on frosted glass that fades out downward (no hard edge).
            FadeVisibility(
                visible = pinnedSearch && !showSearch && stack.isEmpty() && !(showPlayer && !docked),
                enter = fadeIn(tween(160)),
                exit = fadeOut(tween(120)),
            ) {
                Box(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(statusTop + PrismaTopHeight + 36.dp)
                            .glass(barHaze, 26.dp, c.background, listOf(dev.chrisbanes.haze.HazeTint(c.background.copy(alpha = 0.45f))), c.background.copy(alpha = 0.9f), progressive = true),
                    )
                    PrismaTopBar(
                        session = session,
                        onAvatar = { push(Destination.Settings) },
                        search = { showSearch = true },
                        inLibrary = tab == 1,
                        onLibrary = { tab = if (tab == 1) 0 else 1 },
                        modifier = Modifier.padding(top = statusTop + 4.dp),
                    )
                }
            }
            val keyboardUp = WindowInsets.ime.getBottom(LocalDensity.current) != 0
            // Customize: the bar at the bottom (or search at the top), the mini player joined to the bar
            // or on its own, and Legacy's floating song-colored mini player.
            val tabsShown = !shell && !showPlayer && !keyboardUp && !settings.topSearch
            val dockSong = pendingSong ?: player.current
            val legacyMini = settings.legacyUi
            val songOnItsOwn = !legacyMini && (settings.separateMiniPlayer || settings.topSearch)
            val barVisible = tabsShown || (!shell && !showPlayer && !keyboardUp && songOnItsOwn && dockSong != null)
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .padding(bottom = when {
                        legacyMini && tabsShown -> PrismaBarHeight - 8.dp
                        legacyMini -> 0.dp
                        barVisible && settings.dockOneRow && conf.screenWidthDp >= 840 && tabsShown -> PrismaBarHeight - 8.dp
                        barVisible -> (if (tabsShown) PrismaBarHeight - 8.dp else 0.dp) + (if (dockSong != null) PrismaDockSongHeight + (if (songOnItsOwn) 8.dp else 0.dp) else 0.dp)
                        else -> 0.dp
                    }),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Phones show the picked song in the dock instead, with a loading play button.
                if (preparing && (shell || legacyMini)) LoadingIndicator(Modifier.size(48.dp).padding(bottom = 8.dp))
                UpdatePill()
                // The player grows out of the mini player and tucks back into it.
                AnimatedVisibility(
                    visible = !showPlayer && player.current != null && !keyboardUp && (if (shell) false else legacyMini),
                    enter = EnterTransition.None,
                    exit = ExitTransition.None,
                ) {
                    MaterialTheme(colorScheme = rememberArtColorScheme(player.current?.artworkUrl)) {
                    MiniPlayer(
                        player,
                        onOpen = { showPlayer = true },
                        // A comfortable size on big screens instead of spanning the whole window.
                        modifier = Modifier.widthIn(max = 640.dp).sharedContainer("player", this, RoundedCornerShape(32.dp)),
                    )
                    }
                }
            }
            // Big tablets: the mini player beside the tabs, in one row (Customize).
            val oneRow = !legacyMini && settings.dockOneRow && conf.screenWidthDp >= 840 && tabsShown
            // Kept in an AnimatedVisibility so the player can grow out of the mini player and fold back.
            androidx.compose.animation.AnimatedVisibility(visible = barVisible, enter = EnterTransition.None, exit = ExitTransition.None, modifier = Modifier.align(Alignment.BottomCenter)) {
                PrismaTabBar(
                    tab = tab,
                    onTab = { t ->
                        if (t == 2) {
                            showSearch = true
                        } else {
                            showSearch = false
                            if (t == tab && stack.isEmpty()) scope.launch { listState.animateScrollToItem(0) }
                            while (stack.isNotEmpty()) pop()
                            tab = t
                        }
                    },
                    haze = if (settings.legacyUi) null else barHaze,
                    now = if (legacyMini) null else dockSong,
                    playing = player.isPlaying,
                    loading = preparing,
                    onOpenPlayer = { openPlayer(null) },
                    showTabs = tabsShown,
                    split = songOnItsOwn,
                    oneRow = oneRow,
                    visibility = this,
                )
            }
        }
        }
        }
        }


        AnimatedVisibility(
            visible = showPlayer && player.current != null,
            // Docked, the player takes its share of the window on its side.
            modifier = Modifier.layout { measurable, cons ->
                val w = (cons.maxWidth * playerWidth.value).roundToInt().coerceIn(1, cons.maxWidth)
                val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(w, cons.maxHeight))
                layout(cons.maxWidth, cons.maxHeight) { placeable.place(if (dockSide < 0) 0 else cons.maxWidth - w, 0) }
            },
            enter = if (droppedIn) slideInHorizontally(tween(420, easing = WaveMotion.EmphasizedDecelerate)) { dockSide * it } + fadeIn(tween(220)) else EnterTransition.None,
            // Closing a docked player (button, back, or dragging it down) carries on down toward
            // the player bar it came from.
            exit = if (droppedIn) slideOutVertically(tween(300, easing = WaveMotion.EmphasizedAccelerate)) { it / 2 } + fadeOut(tween(240, delayMillis = 40)) else ExitTransition.None,
        ) {
            NowPlayingScreen(
                player,
                onClose = ::closePlayer,
                modifier = if (droppedIn) Modifier else Modifier.sharedContainer("player", this, RoundedCornerShape(if (shell) 0.dp else 32.dp), fromTop = true),
                docked = docked,
                dockSide = dockSide,
                onToggleSize = when {
                    !shell -> null
                    // Docked → the whole window.
                    docked -> ({ playerFull = true })
                    // Full, but it was docked → back to its side.
                    canDock && dockSide != 0 -> ({ playerFull = false })
                    // Full from the bar → back to the bar.
                    else -> ({ closePlayer() })
                },
                openTo = playerPanel,
            )
        }
        // Wave's notices, just above whatever's at the bottom: the player bar, the mini player,
        // or the open player's tray.
        app.wave.music.ui.components.NoticeHost(
            enabled = menuTarget == null || desktop,
            glass = if (!shell && !showPlayer) barHaze else null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = (if (!shell && !showPlayer) PrismaBarHeight else 0.dp) + when {
                    showPlayer -> 96.dp
                    player.current == null -> 20.dp
                    shell -> 116.dp
                    else -> 112.dp
                }),
        )
        }
    }
    }
    }

    CompositionLocalProvider(app.wave.music.ui.menu.LocalAppHaze provides appHaze) {
    ItemMenuHost(
        target = menuTarget,
        onDismiss = { menuTarget = null },
        actions = MenuActions(
            playItem = playItem,
            playNext = playNext,
            addToQueue = addToQueue,
            navigate = { dest ->
                // Docked beside the app, the player stays put and the page opens on the other
                // side; a full-window player gets out of the way.
                if (!docked) showPlayer = false
                push(dest)
            },
        ),
        createRequested = createPlaylist,
        onCreateHandled = { createPlaylist = false },
    )
    }


    if (askSignOut) {
        AccountSheet(
            session = session,
            onDismiss = { askSignOut = false },
            onOpen = { dest ->
                askSignOut = false
                push(dest)
            },
            onSignOut = {
                askSignOut = false
                onSignOut()
            },
        )
    }
    }
}

/** AnimatedVisibility without a Row/Column scope in the way. */
@Composable
private fun FadeVisibility(
    visible: Boolean,
    enter: EnterTransition,
    exit: ExitTransition,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.animation.AnimatedVisibilityScope.() -> Unit,
) = AnimatedVisibility(visible, modifier, enter, exit, content = content)

/** The sizes the docked player can take (its share of the window). */




/** A new Wave release: a small pill above the mini player to download and install it. */
@Composable
private fun UpdatePill() {
    val scope = rememberCoroutineScope()
    val update by Updater.state.collectAsState()
    var dismissed by rememberSaveable { mutableStateOf<String?>(null) }
    val release = when (val u = update) {
        is Updater.State.Available -> u.release
        is Updater.State.Downloading -> u.release
        is Updater.State.Ready -> u.release
        else -> null
    }
    AnimatedVisibility(
        visible = release != null && dismissed != release.version,
        enter = fadeIn(tween(180)) + slideInVertically(tween(260, easing = WaveMotion.EmphasizedDecelerate)) { it / 2 },
        exit = fadeOut(tween(120)),
    ) {
        val c = MaterialTheme.colorScheme
        Row(
            Modifier
                .padding(bottom = 10.dp)
                .height(48.dp)
                .pressable(CircleShape, c.primaryContainer, pressedScale = 0.96f) {
                    when (val u = update) {
                        is Updater.State.Available -> scope.launch { Updater.download(u.release) }
                        is Updater.State.Ready -> Updater.install(u.file)
                        else -> Unit
                    }
                }
                .padding(start = 18.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when (val u = update) {
                    is Updater.State.Downloading -> "Downloading Wave ${u.release.version} · ${(u.progress * 100).toInt()}%"
                    is Updater.State.Ready -> "Wave ${u.release.version} is ready · Install"
                    else -> "Wave ${release?.version} is available · Update"
                },
                color = c.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            IconButton(onClick = { dismissed = release?.version }) {
                Icon(Icons.Rounded.Close, "Not now", tint = c.onPrimaryContainer)
            }
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning,"
    in 12..16 -> "Good afternoon,"
    else -> "Good evening,"
}

@Composable
private fun Hero(hero: HeroContent?, jump: List<YTItem> = emptyList(), onJump: (YTItem) -> Unit = {}, onSharedBackdrop: Boolean = false, modernButtons: Boolean = false) {
    val c = MaterialTheme.colorScheme
    // Tall on phones; on short landscape screens it leaves room to see what's below.
    val config = currentWindowDp()
    val heightDp = if (config.screenHeightDp < 600) (config.screenHeightDp * 0.8f).dp.coerceAtLeast(300.dp) else 580.dp
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(heightDp),
    ) {
        // Only when there's room for the cover, the words and the tiles side by side.
        val showJump = jump.isNotEmpty() && maxWidth >= 1180.dp
        // Full-bleed art, progressively blurred toward the bottom.
        // On wide screens the art stays sharp further down; the blur only softens the bottom.
        // Goes by the room the banner actually has (half a window when the player is docked).
        val wideHero = config.screenWidthDp > config.screenHeightDp
        val bigHero = wideHero && maxWidth >= 780.dp
        if (bigHero && onSharedBackdrop) {
            // Computers: the window's shared art backdrop shows through; nothing to draw here.
        } else if (bigHero) {
            // Kept inside the banner and faded out at its bottom, so there's no edge below it.
            BlurredImage(
                hero?.art?.atSize(ArtSize.BLUR),
                Modifier.fillMaxSize().clipToBounds().graphicsLayer { scaleX = 1.2f; scaleY = 1.2f },
                radius = 96.dp,
                fadeBottom = 0.35f,
            )
        } else {
            ProgressiveBlurImage(hero?.art, Modifier.fillMaxSize(), start = if (wideHero) 0.62f else 0.4f, end = 0.95f, maxRadius = 64.dp)
        }
        if (!(bigHero && onSharedBackdrop)) Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        0.75f to c.background.copy(alpha = 0.55f),
                        1f to c.background,
                    ),
                ),
        )
        if (hero != null) {
            if (bigHero) {
                // Big screens: a sharp cover card beside the title, instead of stretching the art.
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = 32.dp, end = 32.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    AsyncImage(
                        model = hero.art,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(minOf(heightDp - 200.dp, if (showJump) 300.dp else 340.dp))
                            .shadow(28.dp, RoundedCornerShape(36.dp))
                            .clip(RoundedCornerShape(36.dp)),
                    )
                    HeroText(hero, Modifier.weight(1f, fill = false).widthIn(max = 560.dp).padding(start = 32.dp))
                    if (showJump) {
                        Spacer(Modifier.weight(0.01f).widthIn(min = 32.dp))
                        JumpBackIn(jump, onJump, Modifier.width(420.dp))
                    }
                }
            } else {
                HeroText(hero, Modifier.align(Alignment.BottomStart).widthIn(max = 600.dp).padding(horizontal = 20.dp), modernButtons)
            }
        }
    }
}

/** The hero's words and buttons: label, title, detail, progress, play and shuffle. */
@Composable
private fun HeroText(hero: HeroContent, modifier: Modifier, modernButtons: Boolean = false) {
    val c = MaterialTheme.colorScheme
    Column(modifier) {
        Text(hero.label, style = MaterialTheme.typography.labelSmall, color = c.primary)
        Text(
            hero.title,
            style = MaterialTheme.typography.displayMedium,
            color = c.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        hero.detail?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        hero.progress?.let { p ->
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(c.onBackground.copy(alpha = 0.18f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(p.coerceIn(0f, 1f))
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(c.primary),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        if (modernButtons) {
            // Legacy home: the modern play row (a round shuffle beside a big play pill).
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.height(52.dp).width(196.dp).pressable(CircleShape, c.primary, pressedScale = 0.95f, onClick = hero.onPrimary),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(hero.primary.second, null, tint = c.onPrimary)
                        Text("  ${hero.primary.first}", color = c.onPrimary, style = androidx.compose.ui.text.TextStyle(fontFamily = app.wave.music.platform.prismaDisplayFont, fontSize = 17.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    }
                }
                Box(
                    Modifier.size(52.dp).pressable(CircleShape, c.onBackground.copy(alpha = 0.12f), pressedScale = 0.9f, onClick = hero.onSecondary),
                    contentAlignment = Alignment.Center,
                ) { Icon(hero.secondary.second, hero.secondary.first, tint = c.onBackground) }
            }
        } else
        // Connected button group
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .pressable(RoundedCornerShape(28.dp, 10.dp, 10.dp, 28.dp), c.primary, pressedScale = 0.95f, onClick = hero.onPrimary),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(hero.primary.second, null, tint = c.onPrimary)
                Text("  ${hero.primary.first}", color = c.onPrimary, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Row(
                Modifier
                    .width(150.dp)
                    .fillMaxSize()
                    .pressable(RoundedCornerShape(10.dp, 28.dp, 28.dp, 10.dp), c.onBackground.copy(alpha = 0.12f), pressedScale = 0.93f, onClick = hero.onSecondary),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(hero.secondary.second, null, tint = c.onBackground)
                Text("  ${hero.secondary.first}", color = c.onBackground, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String?, title: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp)) {
        label?.let {
            Text(it.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun SectionRow(
    section: HomePage.Section,
    shapeIndex: Int,
    onPlayAll: (shuffle: Boolean) -> Unit,
    onItem: (YTItem) -> Unit,
    /** Big screens: wrap into rows (two at most) instead of scrolling sideways. */
    grid: Boolean = false,
) {
    val shapes = listOf(
        MaterialShapes.Cookie9Sided, MaterialShapes.Sunny, MaterialShapes.Clover4Leaf,
        MaterialShapes.Cookie12Sided, MaterialShapes.Flower, MaterialShapes.SoftBurst,
    )
    val c = MaterialTheme.colorScheme
    val tones = listOf(
        c.primary to c.onPrimary,
        c.primaryContainer to c.onPrimaryContainer,
        c.tertiaryContainer to c.onTertiaryContainer,
    )
    val (fill, onFill) = tones[shapeIndex % tones.size]
    val songsOnly = section.items.all { it is SongItem }
    val lead: @Composable () -> Unit = {
        LeadTile(
            shape = shapes[shapeIndex % shapes.size].toShape(),
            fill = fill,
            onFill = onFill,
            shuffle = !songsOnly,
            caption = if (songsOnly) "Play all" else "Shuffle all",
            detail = "${section.items.size} ${if (songsOnly) "songs" else "picks"}",
            onClick = { onPlayAll(!songsOnly) },
        )
    }
    if (grid) {
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            maxLines = 2,
        ) {
            lead()
            section.items.forEach { ItemCard(it, onClick = onItem) }
        }
        return
    }
    WheelRow {
        item { lead() }
        items(section.items, key = { it.id }) { ItemCard(it, onClick = onItem) }
    }
}

/** Big screens: quick tiles beside the banner to jump back into your playlists. */
@Composable
private fun JumpBackIn(items: List<YTItem>, onOpen: (YTItem) -> Unit, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("JUMP BACK IN", style = MaterialTheme.typography.labelSmall, color = c.onBackground.copy(alpha = 0.8f))
        items.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { item ->
                    Row(
                        Modifier
                            .weight(1f)
                            .height(64.dp)
                            .pressable(RoundedCornerShape(16.dp), c.onBackground.copy(alpha = 0.08f), pressedScale = 0.96f) { onOpen(item) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AsyncImage(item.thumbnail?.atSize(ArtSize.THUMB), null, Modifier.size(64.dp).background(c.surfaceContainerHigh), contentScale = ContentScale.Crop)
                        Text(item.title, Modifier.padding(end = 10.dp), color = c.onBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** The big expressive-shape tile that plays a whole section. Its shape turns slowly. */
@Composable
private fun LeadTile(
    shape: Shape,
    fill: Color,
    onFill: Color,
    shuffle: Boolean,
    caption: String,
    detail: String,
    onClick: () -> Unit,
) {
    // Move with the music: while something plays, the shape turns slowly and kicks on each beat.
    // Nothing playing (or the setting off), it holds still and costs nothing: no frames at all.
    val moving = WaveSettings.state.collectAsState().value.moveWithMusic
    val playing by remember { PlayerConnection.state.map { it.isPlaying }.distinctUntilChanged() }.collectAsState(false)
    val spinState = remember { mutableFloatStateOf(0f) }
    val spin by spinState
    val active by app.wave.music.ui.components.AppFocus.active.collectAsState()
    LaunchedEffect(moving, playing, active) {
        if (!moving || !playing || !active) return@LaunchedEffect
        var last = withFrameMillis { it }
        while (true) app.wave.music.ui.components.ambientFrame().let { now ->
            spinState.floatValue = (spinState.floatValue + (now - last) * 360f / 30_000f) % 360f
            last = now
        }
    }
    val kick = remember { Animatable(0f) }
    LaunchedEffect(moving) {
        if (!moving) return@LaunchedEffect
        BeatBus.beat.collectLatest { beat ->
            if (beat != 0L) {
                kick.snapTo(1f)
                kick.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 300f))
            }
        }
    }
    Column(
        Modifier
            .width(148.dp)
            .pressable(RoundedCornerShape(24.dp), pressedScale = 0.95f, clipAndRipple = false, onClick = onClick),
    ) {
        Box(Modifier.size(148.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = if (moving) spin else 0f
                        val k = 1f + kick.value * 0.07f
                        scaleX = k
                        scaleY = k
                    }
                    .clip(shape)
                    .background(fill),
            )
            Icon(
                if (shuffle) Icons.Rounded.Shuffle else Icons.Rounded.PlayArrow,
                contentDescription = caption,
                tint = onFill,
                modifier = Modifier.size(56.dp),
            )
        }
        Text(caption, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The big plus at the start of "Your playlists". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NewPlaylistTile(onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier
            .width(148.dp)
            .pressable(RoundedCornerShape(24.dp), pressedScale = 0.95f, clipAndRipple = false, onClick = onClick),
    ) {
        Box(
            Modifier
                .size(148.dp)
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(c.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "New playlist", tint = c.onPrimary, modifier = Modifier.size(72.dp))
        }
        Text("New playlist", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall, color = c.onBackground)
        Text("Make your own mix", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
    }
}


/** For offscreen renders only: start with the player open. */
object RenderStart { var player = false; var openTo: String? = null; var menu: MenuTarget? = null; var page: Destination? = null }


/** What the dock shows for something you just picked, before it starts playing. */
private fun pendingFor(item: YTItem): app.wave.music.playback.NowPlaying = when (item) {
    is com.metrolist.innertube.models.SongItem -> app.wave.music.playback.NowPlaying(item.id, item.title, item.artists.joinToString { it.name }, item.thumbnail)
    is com.metrolist.innertube.models.AlbumItem -> app.wave.music.playback.NowPlaying(item.id, item.title, item.artists?.joinToString { it.name } ?: "Album", item.thumbnail)
    is com.metrolist.innertube.models.PlaylistItem -> app.wave.music.playback.NowPlaying(item.id, item.title, item.author?.name ?: "Playlist", item.thumbnail)
    else -> app.wave.music.playback.NowPlaying(item.id, item.title, "", item.thumbnail)
}


/** Legacy's library: 1.0's big headline, its rounded tiles, and its sideways rows of cards. */
@Composable
private fun LegacyLibrary(
    session: Session,
    playlists: List<com.metrolist.innertube.models.PlaylistItem>,
    recent: List<SongItem>,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onOpen: (Destination) -> Unit,
    openItem: (YTItem) -> Unit,
    playItem: (YTItem, Boolean) -> Unit,
    onNewPlaylist: () -> Unit,
    topBar: @Composable () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding)) {
        item { topBar() }
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
                Text("Your library", color = c.onBackground, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp)
                Text(if (session.guest) "Likes stay on this phone" else session.name, color = c.onSurfaceVariant, fontSize = 15.sp)
            }
        }
        // Liked songs: 1.0's big song-colored card.
        item {
            Row(
                Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 18.dp)
                    .fillMaxWidth()
                    .height(88.dp)
                    .pressable(RoundedCornerShape(28.dp), c.primary, pressedScale = 0.97f) { onOpen(Destination.Playlist("LM")) }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(androidx.compose.material.icons.Icons.Rounded.Favorite, null, tint = c.onPrimary, modifier = Modifier.size(32.dp))
                Column(Modifier.weight(1f)) {
                    Text("Liked songs", color = c.onPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Everything you've liked", color = c.onPrimary.copy(alpha = 0.8f), fontSize = 13.sp)
                }
                Icon(androidx.compose.material.icons.Icons.Rounded.PlayArrow, null, tint = c.onPrimary)
            }
        }
        // The account panel's tiles, as 1.0 had them.
        item {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple(androidx.compose.material.icons.Icons.Rounded.LibraryMusic, "Playlists", Destination.Playlists),
                    Triple(androidx.compose.material.icons.Icons.Rounded.Album, "Albums & artists", Destination.Saved),
                    Triple(androidx.compose.material.icons.Icons.Rounded.History, "History", Destination.History),
                ).forEach { (icon, label, dest) ->
                    Column(
                        Modifier
                            .weight(1f)
                            .height(100.dp)
                            .pressable(RoundedCornerShape(24.dp), c.surfaceContainerHighest, pressedScale = 0.95f) { onOpen(dest) }
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Icon(icon, null, tint = c.primary)
                        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 15.sp, color = c.onSurface, maxLines = 2)
                    }
                }
            }
        }
        if (!session.guest) {
            item { SectionHeader("Your library", "Your playlists") }
            item {
                WheelRow {
                    item { NewPlaylistTile(onNewPlaylist) }
                    items(playlists.filterNot { it.id == "LM" || it.id == "VLLM" }, key = { "lp" + it.id }) { ItemCard(it, onClick = openItem) }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item { SectionHeader("Your recent spins", "Played lately") }
            item {
                WheelRow {
                    items(recent.take(16), key = { "lr" + it.id }) { song -> ItemCard(song) { playItem(it, false) } }
                }
            }
        }
    }
}
