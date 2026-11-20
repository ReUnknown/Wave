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
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wave.music.data.HomeFeed
import app.wave.music.data.Session
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
 * Wave Prisma (Wave 2.0, alpha): a bottom bar instead of the search bar at the top, and a home
 * laid out in big picks, song mixes and rows. Wave's own look, just looser than strict M3E.
 */

/** Height of the bottom bar, not counting the gesture bar under it. */
val PrismaBarHeight: Dp = 72.dp

// ---------------------------------------------------------------- The top

/** Wave's mark and a greeting on the left, your picture on the right. No search bar. */
@Composable
fun PrismaHeader(session: Session, title: String?, onAvatar: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            WaveMark(c.onBackground.copy(alpha = 0.9f), size = 30.dp)
            Spacer(Modifier.height(10.dp))
            if (title != null) {
                Text(title, color = c.onBackground, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp)
            } else {
                Text(greetingLine(), color = c.onBackground.copy(alpha = 0.75f), fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (session.guest) "Welcome to Wave" else session.name.substringBefore(' '),
                    color = c.onBackground,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.8).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(c.onBackground.copy(alpha = 0.1f))
                .border(1.dp, c.onBackground.copy(alpha = 0.16f), CircleShape)
                .clickable(onClick = onAvatar),
            contentAlignment = Alignment.Center,
        ) {
            if (session.photoUrl != null) {
                AsyncImage(session.photoUrl, "Your account", Modifier.size(52.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Rounded.AccountCircle, "Your account", Modifier.size(30.dp), tint = c.onBackground)
            }
        }
    }
}

private fun greetingLine(): String = when (java.time.LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}

// ---------------------------------------------------------------- The bottom bar

/**
 * Three tabs on a floating, rounded bar. The open tab grows into a filled squircle with its name
 * (like WaveMini's tabs); the others stay as icons.
 */
@Composable
fun PrismaTabBar(tab: Int, onTab: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(PrismaBarHeight - 16.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(c.surfaceContainerHigh.copy(alpha = 0.96f))
                .border(1.dp, c.onSurface.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
                .padding(6.dp),
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
                val ink by animateColorAsState(if (selected) c.onPrimary else c.onSurfaceVariant, tween(180), label = "tabInk")
                Row(
                    Modifier
                        .weight(weight)
                        .fillMaxSize()
                        .pressable(RoundedCornerShape(22.dp), fill, pressedScale = 0.92f) { onTab(i) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (selected) on else off, label, tint = ink, modifier = Modifier.size(24.dp))
                    if (selected) {
                        Spacer(Modifier.width(8.dp))
                        Text(label, color = ink, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Home

private fun captionFor(item: YTItem): String = when (item) {
    is AlbumItem -> item.artists?.firstOrNull()?.name?.let { "New from $it" } ?: "Album"
    is PlaylistItem -> "Made for you"
    is ArtistItem -> "Artist"
    is SongItem -> item.artists.firstOrNull()?.name?.let { "From $it" } ?: "Song"
    else -> ""
}

/** Home's rows, added to the list that scrolls under the header. */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.prismaHome(
    feed: HomeFeed,
    recent: List<SongItem>,
    playlists: List<PlaylistItem>,
    guest: Boolean,
    onSignIn: () -> Unit,
    playItem: (YTItem, Boolean) -> Unit,
    openItem: (YTItem) -> Unit,
    playSongs: (String, List<SongItem>, Int) -> Unit,
) {
    if (guest) item(key = "signin") { SignInCard(onSignIn) }

    // Big picks: the day's pick first, then the first row of albums and playlists.
    val pickRow = feed.sections.firstOrNull { s -> s.items.any { it !is SongItem } }
    val picks = (listOfNotNull(feed.hero?.item) + (pickRow?.items.orEmpty().filter { it !is SongItem })).distinctBy { it.id }.take(8)
    if (picks.isNotEmpty()) {
        item(key = "picks-h") { PrismaSectionTitle("Top picks for you") }
        item(key = "picks") {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(picks, key = { it.id }) { pick -> PickCard(pick, onClick = openItem, onPlay = { playItem(pick, false) }) }
            }
        }
    }

    if (recent.isNotEmpty()) {
        item(key = "recent-h") { PrismaSectionTitle("Recently played") }
        item(key = "recent") {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(recent, key = { "r" + it.id }) { song -> SquareCard(song) { playItem(song, false) } }
            }
        }
    }

    if (!guest && playlists.isNotEmpty()) {
        item(key = "mine-h") { PrismaSectionTitle("Your playlists") }
        item(key = "mine") {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(playlists, key = { "p" + it.id }) { list -> SquareCard(list, onClick = openItem) }
            }
        }
    }

    feed.sections.forEachIndexed { index, section ->
        if (section === pickRow) return@forEachIndexed
        val songs = section.items.filterIsInstance<SongItem>()
        item(key = "s-h-$index") { PrismaSectionTitle(section.title, section.label) }
        if (songs.size == section.items.size && songs.size >= 4) {
            // Song mixes: columns of four rows you swipe through.
            item(key = "s-$index") {
                val columns = songs.chunked(4)
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                    items(columns.size) { col ->
                        Column(Modifier.width(320.dp)) {
                            columns[col].forEachIndexed { row, song ->
                                SongLine(song) { playSongs(section.title, songs, col * 4 + row) }
                            }
                        }
                    }
                }
            }
        } else {
            item(key = "s-$index") {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(section.items, key = { "s$index" + it.id }) { entry -> SquareCard(entry, onClick = openItem) }
                }
            }
        }
    }
}

@Composable
private fun PrismaSectionTitle(title: String, label: String? = null) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp)) {
        if (!label.isNullOrBlank()) Text(label.uppercase(), color = c.onBackground.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = c.onBackground, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.onBackground.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun SignInCard(onSignIn: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(Brush.linearGradient(listOf(c.primary, c.tertiary)))
            .padding(24.dp),
    ) {
        Text("Your music, everywhere", color = c.onPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)
        Text("Sign in to see your playlists, likes and mixes.", color = c.onPrimary.copy(alpha = 0.85f), fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
        Box(
            Modifier
                .padding(top = 18.dp)
                .height(52.dp)
                .pressable(CircleShape, Color.White, pressedScale = 0.94f, onClick = onSignIn)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Sign in with Google", color = Color(0xFF1B1B1F), fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    }
}

/** A tall card: the art fills it, the words sit at the bottom over a soft shade. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PickCard(item: YTItem, onClick: (YTItem) -> Unit, onPlay: () -> Unit) {
    val menu = LocalItemMenu.current
    Box(
        Modifier
            .width(268.dp)
            .height(356.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(onClick = { onClick(item) }, onLongClick = { menu.openFor(item) }),
    ) {
        AsyncImage(item.thumbnail?.atSize(1080), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.78f))))
        Row(Modifier.align(Alignment.BottomStart).padding(18.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(captionFor(item), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 25.sp)
                subtitle(item)?.let { Text(it, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Box(
                Modifier
                    .padding(start = 10.dp)
                    .size(48.dp)
                    .pressable(RoundedCornerShape(18.dp), Color.White, pressedScale = 0.9f, onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, "Play", tint = Color(0xFF1B1B1F)) }
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
            .width(150.dp)
            .combinedClickable(onClick = { onClick(item) }, onLongClick = { menu.openFor(item) }),
    ) {
        AsyncImage(
            item.thumbnail?.atSize(400),
            null,
            Modifier.size(150.dp).clip(if (round) CircleShape else RoundedCornerShape(20.dp)).background(c.surfaceContainerHigh),
            contentScale = ContentScale.Crop,
        )
        Text(item.title, color = c.onBackground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        subtitle(item)?.let { Text(it, color = c.onBackground.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
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
            .height(68.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = { menu.openFor(song) })
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AsyncImage(song.thumbnail?.atSize(226), null, Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(c.surfaceContainerHigh), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f)) {
            Text(song.title, color = c.onBackground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artists.joinToString { it.name }, color = c.onBackground.copy(alpha = 0.6f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(40.dp).clip(CircleShape).clickable { menu.openFor(song) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MoreVert, "More", tint = c.onBackground.copy(alpha = 0.6f))
        }
    }
}

// ---------------------------------------------------------------- Library

/** Your library: big tiles for the main parts, then your playlists and what you played lately. */
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
) {
    val c = MaterialTheme.colorScheme
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding)) {
        if (session.guest) {
            item { SignInCard(onSignIn) }
            return@LazyColumn
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LibraryTile(Icons.Rounded.Favorite, "Liked songs", c.primary, c.onPrimary, Modifier.weight(1f)) { onOpen(Destination.Playlist("LM")) }
                    LibraryTile(Icons.Rounded.LibraryMusic, "Playlists", c.secondaryContainer, c.onSecondaryContainer, Modifier.weight(1f)) { onOpen(Destination.Playlists) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LibraryTile(Icons.Rounded.Album, "Albums & artists", c.tertiaryContainer, c.onTertiaryContainer, Modifier.weight(1f)) { onOpen(Destination.Saved) }
                    LibraryTile(Icons.Rounded.History, "History", c.surfaceContainerHighest, c.onSurface, Modifier.weight(1f)) { onOpen(Destination.History) }
                }
            }
        }
        if (playlists.isNotEmpty()) {
            item { PrismaSectionTitle("Your playlists") }
            items(playlists, key = { "lp" + it.id }) { list -> LibraryRow(list) { openItem(list) } }
        }
        if (recent.isNotEmpty()) {
            item { PrismaSectionTitle("Played lately") }
            items(recent.take(10), key = { "lr" + it.id }) { song -> LibraryRow(song) { playItem(song, false) } }
        }
    }
}

@Composable
private fun LibraryTile(icon: ImageVector, label: String, fill: Color, ink: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(104.dp)
            .pressable(RoundedCornerShape(28.dp), fill, pressedScale = 0.95f, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(icon, null, tint = ink, modifier = Modifier.size(28.dp))
        Text(label, color = ink, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(onClick = onClick, onLongClick = { menu.openFor(item) })
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AsyncImage(item.thumbnail?.atSize(226), null, Modifier.size(58.dp).clip(if (item is ArtistItem) CircleShape else RoundedCornerShape(14.dp)).background(c.surfaceContainerHigh), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f)) {
            Text(item.title, color = c.onBackground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle(item)?.let { Text(it, color = c.onBackground.copy(alpha = 0.6f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}
