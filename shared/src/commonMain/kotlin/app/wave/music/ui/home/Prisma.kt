package app.wave.music.ui.home

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
fun PrismaTopBar(session: Session, onAvatar: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().height(PrismaTopHeight).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { WaveMark(c.onBackground, size = 40.dp) }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
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
) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(32.dp)
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .then(
                    if (haze != null) {
                        Modifier.hazeEffect(state = haze) {
                            blurRadius = 30.dp
                            backgroundColor = c.background
                            tints = listOf(dev.chrisbanes.haze.HazeTint(c.surfaceContainer.copy(alpha = 0.6f)))
                            noiseFactor = 0f
                        }
                    } else Modifier.background(c.surfaceContainer.copy(alpha = 0.97f)),
                )
                .border(1.dp, c.onSurface.copy(alpha = 0.1f), shape),
        ) {
            if (now != null) {
                DockSong(now, playing, onOpenPlayer)
                Box(Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(1.dp).background(c.onSurface.copy(alpha = 0.08f)))
            }
            Row(
                Modifier.fillMaxWidth().height(PrismaBarHeight - 16.dp).padding(7.dp),
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
                            .pressable(RoundedCornerShape(26.dp), fill, pressedScale = 0.92f) { onTab(i) },
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
    }
}

/** The mini player inside the dock: cover, song, and a play button in the song's color. */
@Composable
private fun DockSong(now: app.wave.music.playback.NowPlaying, playing: Boolean, onOpen: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val song = app.wave.music.ui.components.rememberArtColorScheme(now.artworkUrl)
    Row(
        Modifier
            .fillMaxWidth()
            .height(PrismaDockSongHeight)
            .clickable(onClick = onOpen)
            .padding(start = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AsyncImage(now.artworkUrl?.atSize(226), null, Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(c.surfaceContainerHigh), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f)) {
            Text(now.title, color = c.onSurface, style = body(15.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(now.artist, color = c.onSurface.copy(alpha = 0.65f), style = body(13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable { app.wave.music.playback.PlayerConnection.previous() },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.SkipPrevious, "Previous song", tint = c.onSurface) }
        Box(
            Modifier.size(46.dp).pressable(RoundedCornerShape(16.dp), song.primary, pressedScale = 0.9f) { app.wave.music.playback.PlayerConnection.togglePlay() },
            contentAlignment = Alignment.Center,
        ) { app.wave.music.ui.components.PlayPauseIcon(playing, song.onPrimary) }
        Box(
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
) {
    item(key = "top") { topBar() }
    val picks = picksFrom(feed, recent)
    if (picks.isNotEmpty()) {
        item(key = "picks-h") { PrismaSectionTitle("Top picks for you", first = true) }
        item(key = "picks") {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(picks, key = { "pick" + it.id }) { pick ->
                    PickCard(pick, onClick = { if (it is SongItem) playItem(it, false) else openItem(it) }, onPlay = { playItem(pick, false) })
                }
            }
        }
    }

    if (recent.isNotEmpty()) {
        item(key = "recent-h") { PrismaSectionTitle("Recently played") }
        item(key = "recent") {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(recent, key = { "r" + it.id }) { song -> SquareCard(song) { playItem(song, false) } }
            }
        }
    }

    if (!guest && playlists.isNotEmpty()) {
        item(key = "mine-h") { PrismaSectionTitle("Your playlists") }
        item(key = "mine") {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(playlists, key = { "p" + it.id }) { list -> SquareCard(list, onClick = openItem) }
            }
        }
    }

    feed.sections.forEachIndexed { index, section ->
        val songs = section.items.filterIsInstance<SongItem>()
        item(key = "s-h-$index") { PrismaSectionTitle(section.title, section.label) }
        if (songs.size == section.items.size && songs.size >= 4) {
            // Song mixes: columns of four rows you swipe through.
            item(key = "s-$index") {
                val columns = songs.chunked(4)
                LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                    items(columns.size) { col ->
                        Column(Modifier.width(312.dp)) {
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
}

@Composable
private fun PrismaSectionTitle(title: String, label: String? = null, first: Boolean = false, onClick: (() -> Unit)? = null) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 16.dp, top = if (first) 4.dp else 22.dp, bottom = 10.dp),
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
        Modifier
            .width(224.dp)
            .height(296.dp)
            .clip(RoundedCornerShape(26.dp))
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
                Text(captionFor(item), color = Color.White.copy(alpha = 0.82f), style = body(12.sp, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.title, color = Color.White, style = display(19.sp, FontWeight.Bold, (-0.2).sp).copy(lineHeight = 22.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                subtitle(item)?.let { Text(it, color = Color.White.copy(alpha = 0.75f), style = body(12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            val tone = app.wave.music.ui.components.rememberArtColorScheme(item.thumbnail?.atSize(226), app.wave.music.ui.theme.darkBaseScheme())
            Box(
                Modifier
                    .padding(start = 8.dp)
                    .size(42.dp)
                    .pressable(RoundedCornerShape(16.dp), tone.primary, pressedScale = 0.9f, onClick = onPlay),
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
            .width(128.dp)
            .combinedClickable(onClick = { onClick(item) }, onLongClick = { menu.openFor(item) }),
    ) {
        AsyncImage(
            item.thumbnail?.atSize(400),
            null,
            Modifier.size(128.dp).clip(if (round) CircleShape else RoundedCornerShape(18.dp)).background(c.surfaceContainerHigh),
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
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = { menu.openFor(song) })
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(song.thumbnail?.atSize(226), null, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(c.surfaceContainerHigh), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f)) {
            Text(song.title, color = c.onBackground, style = body(15.sp, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artists.joinToString { it.name }, color = c.onBackground.copy(alpha = 0.6f), style = body(13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(36.dp).clip(CircleShape).clickable { menu.openFor(song) }, contentAlignment = Alignment.Center) {
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
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LibraryTile(Icons.Rounded.Favorite, "Liked songs", c.primary, c.onPrimary, Modifier.weight(1f)) { onOpen(Destination.Playlist("LM")) }
                    LibraryTile(Icons.Rounded.LibraryMusic, "Playlists", c.secondaryContainer, c.onSecondaryContainer, Modifier.weight(1f)) { onOpen(Destination.Playlists) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LibraryTile(Icons.Rounded.Album, "Albums & artists", c.tertiaryContainer, c.onTertiaryContainer, Modifier.weight(1f)) { onOpen(Destination.Saved) }
                    LibraryTile(Icons.Rounded.History, "History", c.surfaceContainerHighest, c.onSurface, Modifier.weight(1f)) { onOpen(Destination.History) }
                }
            }
        }
        item { PrismaSectionTitle("Your playlists", onClick = { onOpen(Destination.Playlists) }) }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item { NewPlaylistCard(onNewPlaylist) }
                items(playlists, key = { "lp" + it.id }) { list -> SquareCard(list, onClick = openItem) }
            }
        }
        if (recent.isNotEmpty()) {
            item { PrismaSectionTitle("Played lately", onClick = { onOpen(Destination.History) }) }
            items(recent.take(8), key = { "lr" + it.id }) { song -> LibraryRow(song) { playItem(song, false) } }
        }
    }
}

/** The first card in your playlists: makes a new one. */
@Composable
private fun NewPlaylistCard(onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.width(128.dp).clickable(onClick = onClick)) {
        Box(
            Modifier.size(128.dp).clip(RoundedCornerShape(18.dp)).background(c.surfaceContainerHigh).border(1.dp, c.onSurface.copy(alpha = 0.1f), RoundedCornerShape(18.dp)),
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
            .pressable(RoundedCornerShape(22.dp), fill, pressedScale = 0.95f, onClick = onClick)
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
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = { menu.openFor(item) })
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(item.thumbnail?.atSize(226), null, Modifier.size(50.dp).clip(if (item is ArtistItem) CircleShape else RoundedCornerShape(12.dp)).background(c.surfaceContainerHigh), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f)) {
            Text(item.title, color = c.onBackground, style = body(15.sp, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle(item)?.let { Text(it, color = c.onBackground.copy(alpha = 0.6f), style = body(13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}
