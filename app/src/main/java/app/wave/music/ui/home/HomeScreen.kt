package app.wave.music.ui.home

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

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(session: Session, onSignOut: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ui by remember(session) { mutableStateOf<HomeUi>(HomeUi.Loading) }
    var attempt by remember { mutableIntStateOf(0) }
    var askSignOut by remember { mutableStateOf(false) }
    val settings by WaveSettings.state.collectAsState()
    LaunchedEffect(session) { PlayerConnection.loadLikes() }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    val player by PlayerConnection.state.collectAsState()
    val resume by remember { ResumeStore.observe(context.applicationContext) }.collectAsState(null)

    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(session, attempt) {
        ui = HomeUi.Loading
        ui = HomeRepository.load().fold({ HomeUi.Ready(it) }, { HomeUi.Failed })
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
    // Songs play; artists, albums and playlists open their page.
    val openItem: (YTItem) -> Unit = { item -> Destination.of(item)?.let { stack.add(it) } ?: playItem(item, false) }
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
    CompositionLocalProvider(LocalItemMenu provides MenuOpener { menuTarget = it }) {
    // Colors follow the song that's playing; otherwise the art at the top of home.
    MaterialTheme(colorScheme = rememberArtColorScheme(if (settings.matchSongColors) player.current?.artworkUrl ?: hero?.art else null)) {
        val c = MaterialTheme.colorScheme
        val backdrop = rememberGraphicsLayer()
        val listState = rememberLazyListState()
        val greetingAlpha by remember {
            derivedStateOf {
                if (listState.firstVisibleItemIndex > 0) 0f else (1f - listState.firstVisibleItemScrollOffset / 260f).coerceIn(0f, 1f)
            }
        }
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

        Box(
            Modifier
                .fillMaxSize()
                .background(c.background),
        ) {
            when (val state = ui) {
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
                            items(myPlaylists) { ItemCard(it, onClick = openItem) }
                        }
                    }
                    if (state.feed.recent.isNotEmpty()) {
                        item { SectionHeader("Your recent spins", "Recently played") }
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(state.feed.recent) { song -> ItemCard(song) { playItem(it, false) } }
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

            // Sticky header: a frosted band that's blurriest at the very top and fades out downward,
            // so the search bar and greeting always read cleanly over the art.
            // Real backdrop blur behind the sticky header: fully frosted behind the search bar and
            // greeting, then easing off in stages until the content is sharp again.
            ProgressiveBackdropBlur(
                layer = backdrop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusTop + 230.dp),
            )
            // Tint on top of the blur so white text always has contrast.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(statusTop + 230.dp)
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
                    .padding(horizontal = 16.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                        .clickable { showSearch = true }
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
                    val avatar = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { askSignOut = true }
                    if (session.photoUrl != null) {
                        AsyncImage(session.photoUrl, "Your Google account", avatar, contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Rounded.AccountCircle, "Your Google account", avatar, tint = Color.White)
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

            AnimatedVisibility(visible = showSearch, enter = fadeIn(), exit = fadeOut()) {
                SearchScreen(onClose = { showSearch = false }, onPlay = playItem, onPlayNext = playNext, onOpen = openItem, ambientArt = player.current?.artworkUrl ?: hero?.art)
            }

            AnimatedContent(
                targetState = stack.lastOrNull(),
                transitionSpec = {
                    (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 6 } + fadeOut())
                },
                label = "pages",
            ) { dest ->
                if (dest != null) {
                    DetailScreen(
                        dest,
                        PageActions(
                            play = { PlayerConnection.play(it) },
                            playItem = playItem,
                            playNext = playNext,
                            open = openItem,
                            back = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) },
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
                if (!WindowInsets.isImeVisible) MiniPlayer(player, onOpen = { showPlayer = true })
            }
        }
    }

    AnimatedVisibility(
        visible = showPlayer && player.current != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        NowPlayingScreen(player, onClose = { showPlayer = false })
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
                stack.add(dest)
            },
        ),
        createRequested = createPlaylist,
        onCreateHandled = { createPlaylist = false },
    )

    if (askSignOut) {
        AccountSheet(
            session = session,
            onDismiss = { askSignOut = false },
            onOpen = { dest ->
                askSignOut = false
                stack.add(dest)
            },
            onSignOut = {
                askSignOut = false
                onSignOut()
            },
        )
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
    val heightDp = 580.dp
    val heightPx = with(LocalDensity.current) { heightDp.toPx() }
    Box(
        Modifier
            .fillMaxWidth()
            .height(heightDp),
    ) {
        // Full-bleed art, progressively blurred toward the bottom.
        ProgressiveBlurImage(hero?.art, Modifier.fillMaxSize(), start = 0.4f, end = 0.92f, maxRadius = 64.dp)
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
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 20.dp),
            ) {
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
                            .clip(RoundedCornerShape(28.dp, 10.dp, 10.dp, 28.dp))
                            .background(c.primary)
                            .clickable(onClick = hero.onPrimary),
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
                            .clip(RoundedCornerShape(10.dp, 28.dp, 28.dp, 10.dp))
                            .background(c.onBackground.copy(alpha = 0.12f))
                            .clickable(onClick = hero.onSecondary),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(hero.secondary.second, null, tint = c.onBackground)
                        Text("  ${hero.secondary.first}", color = c.onBackground, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
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
        items(section.items) { ItemCard(it, onClick = onItem) }
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
    val spin by rememberInfiniteTransition(label = "lead").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(30_000, easing = LinearEasing), RepeatMode.Restart),
        label = "spin",
    )
    // Move with the music: the shape turns and kicks on each beat.
    val moving = WaveSettings.state.collectAsState().value.moveWithMusic
    val beat by BeatBus.beat.collectAsState()
    val kick = remember { Animatable(0f) }
    LaunchedEffect(beat) {
        if (moving && beat != 0L) {
            kick.snapTo(1f)
            kick.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 300f))
        }
    }
    Column(
        Modifier
            .width(148.dp)
            .clickable(onClick = onClick),
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
            .clickable(onClick = onClick),
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
