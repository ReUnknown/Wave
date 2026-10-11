package app.wave.music.ui.home

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import app.wave.music.ui.components.ArtSize
import app.wave.music.ui.components.BlurredImage
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.Close
import app.wave.music.data.Updater
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.graphics.rememberGraphicsLayer
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
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.mutableStateListOf
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ui by remember(session) { mutableStateOf<HomeUi>(HomeUi.Loading) }
    var attempt by remember { mutableIntStateOf(0) }
    var askSignOut by remember { mutableStateOf(false) }
    val desktop = app.wave.music.ui.components.LocalDesktop.current
    var accountMenu by remember { mutableStateOf(false) }
    var settingsDialog by remember { mutableStateOf(false) }
    val settings by WaveSettings.state.collectAsState()
    LaunchedEffect(session) { PlayerConnection.loadLikes() }
    LaunchedEffect(Unit) {
        WaveSettings.load(context.applicationContext)
        if (WaveSettings.state.value.checkUpdates) Updater.check(context.applicationContext)
    }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    // Ctrl+F or "/" on a keyboard opens search.
    LaunchedEffect(Unit) { app.wave.music.ui.components.Shortcuts.openSearch.collect { showSearch = true } }
    val player by PlayerConnection.state.collectAsState()
    val resume by remember { ResumeStore.observe(context.applicationContext) }.collectAsState(null)

    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(session, attempt) {
        if (ui !is HomeUi.Ready) ui = HomeUi.Loading
        // First page shows as soon as it lands; more sections fill in underneath.
        runCatching { HomeRepository.feed(refresh = attempt > 0).collect { ui = HomeUi.Ready(it) } }
            .onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                if (ui !is HomeUi.Ready) ui = HomeUi.Failed
            }
    }

    fun start(build: suspend () -> Result<PlayRequest>, startPositionMs: Long = 0) {
        scope.launch {
            preparing = true
            build()
                .onSuccess { PlayerConnection.play(it, startPositionMs) }
                .onFailure { Toast.makeText(context, "Couldn't play that right now", Toast.LENGTH_SHORT).show() }
            preparing = false
        }
    }
    val playItem: (YTItem, Boolean) -> Unit = { item, shuffle -> start({ QueueBuilder.forItem(item, shuffle) }) }
    val stack = remember { mutableStateListOf<Destination>() }
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
    var menuTarget by remember { mutableStateOf<MenuTarget?>(null) }
    var createPlaylist by remember { mutableStateOf(false) }
    val myPlaylists by LibraryRepository.playlists.collectAsState()
    LaunchedEffect(session) { LibraryRepository.refresh() }
    val addToQueue: (YTItem) -> Unit = { item ->
        scope.launch {
            val request = if (item is SongItem) Result.success(QueueBuilder.single(item)) else QueueBuilder.forItem(item)
            request.onSuccess {
                PlayerConnection.addToQueue(it)
                Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val playNext: (YTItem) -> Unit = { item ->
        scope.launch {
            val request = if (item is SongItem) Result.success(QueueBuilder.single(item)) else QueueBuilder.forItem(item)
            request.onSuccess {
                PlayerConnection.playNext(it)
                Toast.makeText(context, "Playing next", Toast.LENGTH_SHORT).show()
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
            onPrimary = { showPlayer = true },
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
    // Computers with a big window: the player opens docked in the right half, leaving the app usable
    // on the left; a button expands it to the full window (with lyrics and up next beside it).
    val dockable = desktop && LocalConfiguration.current.screenWidthDp >= 1200
    var playerFull by rememberSaveable { mutableStateOf(false) }
    val docked = dockable && !playerFull && showPlayer && player.current != null
    val pageCovers = rememberCovered(stack.isNotEmpty())
    val playerCovers = rememberCovered(showPlayer && player.current != null && !docked)
    val searchCovers = rememberCovered(showSearch)

    // Remember where each click lands, so a computer's dropdown menus open right under the pointer.
    SharedTransitionLayout(
        Modifier.pointerInput(Unit) {
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
        app.wave.music.ui.components.LocalMiniPlayerSpace provides if (player.current != null && !showPlayer) 116.dp else 0.dp,
    ) {
    // Colors follow the song that's playing; otherwise the art at the top of home.
    MaterialTheme(colorScheme = rememberArtColorScheme(if (settings.matchSongColors) player.current?.artworkUrl ?: hero?.art else null)) {
        val c = MaterialTheme.colorScheme
        val backdrop = rememberBackdrop()
        val listState = rememberLazyListState()
        val greetingAlpha by remember {
            derivedStateOf {
                if (listState.firstVisibleItemIndex > 0) 0f else (1f - listState.firstVisibleItemScrollOffset / 260f).coerceIn(0f, 1f)
            }
        }
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

        // With the player docked on the right (big computer windows), the app reflows into the left half.
        val contentWidth by animateFloatAsState(if (docked) 0.5f else 1f, tween(260, easing = WaveMotion.Emphasized), label = "dock")
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(contentWidth)
                .background(c.background),
        ) {
            // Home (feed + frosted header) stops drawing while a page, search or the player fully
            // covers it, and is drawn again the moment a back swipe could reveal it.
            val homeHidden = (pageCovers || playerCovers || searchCovers) && !peeking.value
            Box(Modifier.fillMaxSize().drawWithContent { if (!homeHidden) drawContent() }) {
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
                    contentPadding = PaddingValues(bottom = 140.dp),
                ) {
                    item { Hero(hero) }
                    item { SectionHeader("Your library", "Your playlists") }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            item { NewPlaylistTile { createPlaylist = true } }
                            items(myPlaylists, key = { it.id }) { ItemCard(it, onClick = openItem) }
                        }
                    }
                    if (state.feed.recent.isNotEmpty()) {
                        item { SectionHeader("Your recent spins", "Recently played") }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(state.feed.recent, key = { it.id }) { song -> ItemCard(song) { playItem(it, false) } }
                            }
                        }
                    }
                    state.feed.sections.forEachIndexed { index, section ->
                        item(key = "header-$index") { SectionHeader(section.label, section.title) }
                        item(key = "row-$index") {
                            SectionRow(
                                section = section,
                                shapeIndex = index,
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

            // Sticky header: a frosted band that's blurriest at the very top and fades out downward,
            // so the search bar and greeting always read cleanly over the art.
            // Real backdrop blur behind the sticky header: fully frosted behind the search bar and
            // greeting, then easing off in stages until the content is sharp again.
            // A shorter frosted band on short (landscape) screens, so it doesn't cover the page.
            val headerBand = if (LocalConfiguration.current.screenHeightDp < 600) 150.dp else 230.dp
            ProgressiveBackdropBlur(
                backdrop = backdrop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusTop + headerBand),
            )
            // Tint on top of the blur so white text always has contrast.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(statusTop + headerBand)
                    .background(
                        Brush.verticalGradient(
                            0f to c.background.copy(alpha = 0.72f),
                            0.6f to c.background.copy(alpha = 0.45f),
                            1f to Color.Transparent,
                        ),
                    ),
            )
            Column(
                Modifier
                    .statusBarsPadding()
                    .widthIn(max = 760.dp)
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                // The search bar opens into the search page: it morphs into the search field.
                Box(Modifier.fillMaxWidth().height(56.dp)) {
                MorphVisibility(!showSearch) {
                Row(
                    Modifier
                        .sharedContainer("search", this, CircleShape)
                        .fillMaxWidth()
                        .height(56.dp)
                        .pressable(CircleShape, Color.White.copy(alpha = 0.12f), pressedScale = 0.97f) { showSearch = true }
                        .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                        .padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WaveMark(Color.White)
                    Text(
                        "Search songs, artists, albums",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    // Phones and tablets: the account panel slides up. Computers: a dropdown right here.
                    val avatar = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { if (desktop) accountMenu = true else askSignOut = true }
                    Box {
                        if (session.photoUrl != null) {
                            AsyncImage(session.photoUrl, "Your Google account", avatar, contentScale = ContentScale.Crop)
                        } else {
                            Icon(Icons.Rounded.AccountCircle, "Your Google account", avatar, tint = Color.White)
                        }
                        AccountMenu(
                            session = session,
                            expanded = accountMenu,
                            onDismiss = { accountMenu = false },
                            onOpen = { accountMenu = false; push(it) },
                            onSettings = { accountMenu = false; settingsDialog = true },
                            onSignOut = { accountMenu = false; onSignOut() },
                        )
                    }
                }
                }
                }
                Column(
                    Modifier
                        .padding(top = 18.dp, start = 4.dp)
                        .alpha(greetingAlpha),
                ) {
                    Text(greeting(), color = Color.White.copy(alpha = 0.88f), fontSize = 22.sp, fontWeight = FontWeight.Medium)
                    Text(session.name.substringBefore(' '), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            }

            AnimatedVisibility(
                visible = showSearch,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(150, delayMillis = 70)),
                // Same for search when a page opened from it covers it.
                modifier = Modifier.drawWithContent { if (!(pageCovers && !peeking.value)) drawContent() },
            ) {
                SearchScreen(
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
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(tween(300, easing = WaveMotion.EmphasizedDecelerate)) { dir * it / 4 } + fadeIn(tween(180, delayMillis = 30))) togetherWith
                        (slideOutHorizontally(tween(200, easing = WaveMotion.EmphasizedAccelerate)) { -dir * it / 8 } + fadeOut(tween(120))) using
                        SizeTransform(clip = false)
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
                        ),
                    )
                }
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (preparing) LoadingIndicator(Modifier.size(48.dp).padding(bottom = 8.dp))
                UpdatePill()
                // The player grows out of the mini player and tucks back into it.
                AnimatedVisibility(
                    visible = !showPlayer && player.current != null && !WindowInsets.isImeVisible,
                    enter = EnterTransition.None,
                    exit = ExitTransition.None,
                ) {
                    MiniPlayer(
                        player,
                        onOpen = { showPlayer = true },
                        // A comfortable size on big screens instead of spanning the whole window.
                        modifier = Modifier.widthIn(max = 640.dp).sharedContainer("player", this, RoundedCornerShape(32.dp)),
                    )
                }
            }
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
    AnimatedVisibility(
        visible = showPlayer && player.current != null,
        enter = EnterTransition.None,
        exit = ExitTransition.None,
    ) {
        NowPlayingScreen(
            player,
            onClose = { showPlayer = false },
            modifier = Modifier
                .fillMaxWidth(if (docked) 0.5f else 1f)
                .sharedContainer("player", this, RoundedCornerShape(32.dp), fromTop = true),
            docked = docked,
            onToggleSize = if (dockable) ({ playerFull = !playerFull }) else null,
        )
    }
    }

    }
    }

    ItemMenuHost(
        target = menuTarget,
        onDismiss = { menuTarget = null },
        actions = MenuActions(
            playItem = playItem,
            playNext = playNext,
            addToQueue = addToQueue,
            navigate = { dest ->
                showPlayer = false
                push(dest)
            },
        ),
        createRequested = createPlaylist,
        onCreateHandled = { createPlaylist = false },
    )

    if (settingsDialog) app.wave.music.ui.settings.SettingsDialog(onDismiss = { settingsDialog = false })

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

/** A new Wave release: a small pill above the mini player to download and install it. */
@Composable
private fun UpdatePill() {
    val context = LocalContext.current
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
                        is Updater.State.Available -> scope.launch { Updater.download(context, u.release) }
                        is Updater.State.Ready -> Updater.install(context, u.apk)
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
private fun Hero(hero: HeroContent?) {
    val c = MaterialTheme.colorScheme
    // Tall on phones; on short landscape screens it leaves room to see what's below.
    val config = LocalConfiguration.current
    val heightDp = if (config.screenHeightDp < 600) (config.screenHeightDp * 0.8f).dp.coerceAtLeast(300.dp) else 580.dp
    val heightPx = with(LocalDensity.current) { heightDp.toPx() }
    Box(
        Modifier
            .fillMaxWidth()
            .height(heightDp),
    ) {
        // Full-bleed art, progressively blurred toward the bottom.
        // On wide screens the art stays sharp further down; the blur only softens the bottom.
        val wideHero = config.screenWidthDp > config.screenHeightDp
        val bigHero = wideHero && config.screenWidthDp >= 840
        if (bigHero) {
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
        Box(
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
                        .padding(start = 32.dp, end = 32.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    AsyncImage(
                        model = hero.art,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(minOf(heightDp - 200.dp, 340.dp))
                            .shadow(28.dp, RoundedCornerShape(36.dp))
                            .clip(RoundedCornerShape(36.dp)),
                    )
                    HeroText(hero, Modifier.widthIn(max = 560.dp).padding(start = 32.dp))
                }
            } else {
                HeroText(hero, Modifier.align(Alignment.BottomStart).widthIn(max = 600.dp).padding(horizontal = 20.dp))
            }
        }
    }
}

/** The hero's words and buttons: label, title, detail, progress, play and shuffle. */
@Composable
private fun HeroText(hero: HeroContent, modifier: Modifier) {
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SectionRow(
    section: HomePage.Section,
    shapeIndex: Int,
    onPlayAll: (shuffle: Boolean) -> Unit,
    onItem: (YTItem) -> Unit,
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
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
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
        items(section.items, key = { it.id }) { ItemCard(it, onClick = onItem) }
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
        while (true) withFrameMillis { now ->
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
