package app.wave.music.ui.detail

import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import app.wave.music.ui.components.ArtSize
import app.wave.music.ui.components.BlurredImage
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.wave.music.data.LibraryRepository
import app.wave.music.data.WaveSettings
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ThumbUp
import app.wave.music.ui.menu.LocalItemMenu
import app.wave.music.ui.menu.MenuTarget
import app.wave.music.ui.menu.RemoveFrom
import app.wave.music.ui.menu.openFor
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wave.music.playback.PlayRequest
import app.wave.music.playback.PlaybackContext
import app.wave.music.playback.QueueBuilder
import app.wave.music.ui.components.ItemCard
import app.wave.music.ui.components.blocksTouchesBelow
import app.wave.music.ui.components.backGesture
import app.wave.music.ui.components.predictiveBack
import app.wave.music.ui.components.pressable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import app.wave.music.ui.components.ItemRow
import app.wave.music.ui.components.ProgressiveBackdropBlur
import app.wave.music.ui.components.ProgressiveBlurImage
import app.wave.music.ui.components.atSize
import app.wave.music.ui.components.backdropSource
import app.wave.music.ui.components.rememberBackdrop
import app.wave.music.ui.components.rememberArtColorScheme
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem

/** A page you can open on top of home: an artist, album or playlist. */
sealed interface Destination {
    val id: String

    data class Artist(override val id: String) : Destination
    data class Album(override val id: String) : Destination
    data class Playlist(override val id: String) : Destination
    data object History : Destination { override val id = "history" }
    data object Playlists : Destination { override val id = "playlists" }
    data object Saved : Destination { override val id = "saved" }
    data object Settings : Destination { override val id = "settings" }

    companion object {
        fun of(item: YTItem): Destination? = when (item) {
            is ArtistItem -> Artist(item.id)
            is AlbumItem -> Album(item.browseId)
            // Mixes and radios (RD…) are endless; they play instead of opening a page.
            is PlaylistItem -> item.id.removePrefix("VL").takeUnless { it.startsWith("RD") }?.let(::Playlist)
            else -> null
        }
    }
}

/** Callbacks every page needs. */
class PageActions(
    val play: (PlayRequest) -> Unit,
    val playItem: (YTItem, shuffle: Boolean) -> Unit,
    val playNext: (YTItem) -> Unit,
    val open: (YTItem) -> Unit,
    val back: () -> Unit,
    val createPlaylist: () -> Unit = {},
)

private fun isMusic(item: YTItem): Boolean = when (item) {
    is PodcastItem, is EpisodeItem -> false
    is PlaylistItem -> !item.isPodcast
    is SongItem -> !item.isEpisode && !item.isVideoSong
    is ArtistItem -> !item.isProfile
    else -> true
}

private sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data object Failed : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
}

/**
 * Pages you opened recently, so going back to one is instant instead of reloading it. Memory
 * only (nothing is written to storage), at most [MAX] pages, each trusted for [FRESH_MS].
 */
/** Sign-out: forget pages the last account opened. */
fun clearPageCache() = PageCache.clear()

private object PageCache {
    private const val MAX = 12
    private const val FRESH_MS = 10 * 60_000L
    private val entries = android.util.LruCache<Any, Pair<Long, Any?>>(MAX)

    fun clear() = entries.evictAll()

    fun get(key: Any): Any? = entries.get(key)?.takeIf { System.currentTimeMillis() - it.first < FRESH_MS }?.second

    fun put(key: Any, value: Any?) {
        entries.put(key, System.currentTimeMillis() to value)
    }
}

@Composable
private fun <T> rememberLoad(key: Any, cache: Boolean = true, block: suspend () -> Result<T>): Pair<Load<T>, () -> Unit> {
    var attempt by remember { mutableIntStateOf(0) }
    @Suppress("UNCHECKED_CAST")
    var state by remember(key) { mutableStateOf<Load<T>>((if (cache) PageCache.get(key) else null)?.let { Load.Ready(it as T) } ?: Load.Loading) }
    LaunchedEffect(key, attempt) {
        // Already here from a recent visit: show it as it was, no reload.
        if (attempt == 0 && state is Load.Ready) return@LaunchedEffect
        state = Load.Loading
        state = block().fold({ if (cache) PageCache.put(key, it); Load.Ready(it) }, { Load.Failed })
    }
    return state to { attempt++ }
}

@Composable
fun DetailScreen(
    destination: Destination,
    actions: PageActions,
    /** False while this page is animating away, so a quick second back doesn't pop twice. */
    active: Boolean = true,
) {
    // Swiping back shrinks the page toward the edge as you go, then it slides away.
    val back = predictiveBack(enabled = active, onBack = actions.back)
    Box(Modifier.fillMaxSize().backGesture(back)) {
    when (destination) {
        is Destination.Artist -> ArtistScreen(destination.id, actions)
        is Destination.Album -> AlbumScreen(destination.id, actions)
        is Destination.Playlist -> PlaylistScreen(destination.id, actions)
        Destination.History -> HistoryScreen(actions)
        Destination.Playlists -> PlaylistsScreen(actions)
        Destination.Saved -> SavedScreen(actions)
        Destination.Settings -> app.wave.music.ui.settings.SettingsScreen(onBack = actions.back)
    }
    }
}

// ---------------------------------------------------------------------------------------------
// Shared page frame: full-bleed art that blurs into the page, frosted back button, colors from art.

private class HeaderInfo(
    /** The page's own item, for its ⋮ menu. */
    val item: YTItem?,
    val art: String?,
    val eyebrow: String,
    val title: String,
    val subtitle: String?,
    val primary: Pair<String, ImageVector>,
    val onPrimary: () -> Unit,
    val secondary: Pair<String, ImageVector>,
    val onSecondary: () -> Unit,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> PageFrame(
    load: Load<T>,
    retry: () -> Unit,
    art: String?,
    actions: PageActions,
    header: (T) -> HeaderInfo,
    content: LazyListScope.(T) -> Unit,
) {
    val matchColors = WaveSettings.state.collectAsState().value.matchSongColors
    MaterialTheme(colorScheme = rememberArtColorScheme(if (matchColors) art?.atSize(600) else null)) {
        val c = MaterialTheme.colorScheme
        val backdrop = rememberBackdrop()
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Box(
            Modifier
                .fillMaxSize()
                .blocksTouchesBelow()
                .background(c.background),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .backdropSource(backdrop),
            ) {
                AnimatedContent(
                    targetState = load,
                    contentKey = { it::class },
                    transitionSpec = { fadeIn(tween(240, delayMillis = 30)) togetherWith fadeOut(tween(110)) },
                    label = "page",
                ) { load ->
                Box(Modifier.fillMaxSize()) {
                when (load) {
                    Load.Loading -> LoadingIndicator(Modifier.size(80.dp).align(Alignment.Center))
                    Load.Failed -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Couldn't load this page", color = c.onBackground, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = retry) { Text("Try again") }
                    }
                    is Load.Ready -> {
                        val info = header(load.value)
                        val config = LocalConfiguration.current
                        if (config.screenWidthDp >= 600 && config.screenWidthDp > config.screenHeightDp) {
                            // Side by side on wide screens, over the art blurred across the window.
                            BlurredImage(info.art?.atSize(ArtSize.BLUR), Modifier.fillMaxSize().graphicsLayer { scaleX = 1.2f; scaleY = 1.2f }, radius = 96.dp)
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to c.background.copy(alpha = 0.55f), 1f to c.background.copy(alpha = 0.88f))))
                            Row(Modifier.fillMaxSize()) {
                                SideHero(info, Modifier.weight(0.42f).fillMaxHeight())
                                LazyColumn(
                                    Modifier.weight(0.58f).fillMaxHeight(),
                                    contentPadding = PaddingValues(top = statusTop + 76.dp, bottom = 140.dp, end = 16.dp),
                                ) { content(load.value) }
                            }
                        } else {
                            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 140.dp)) {
                                item { Hero(info) }
                                content(load.value)
                            }
                        }
                    }
                }
                }
                }
            }
            ProgressiveBackdropBlur(backdrop, Modifier.fillMaxWidth().height(statusTop + 96.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(statusTop + 96.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent))),
            )
            Box(
                Modifier
                    .statusBarsPadding()
                    .padding(12.dp)
                    .size(48.dp)
                    .pressable(CircleShape, Color.White.copy(alpha = 0.14f), pressedScale = 0.86f, onClick = actions.back),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
            val pageItem = (load as? Load.Ready)?.let { header(it.value).item }
            if (pageItem != null) {
                val menu = LocalItemMenu.current
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(12.dp)
                        .size(48.dp)
                        .pressable(CircleShape, Color.White.copy(alpha = 0.14f), pressedScale = 0.86f) { menu.open(MenuTarget.Collection(pageItem, onItsPage = true)) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.MoreVert, "More options", tint = Color.White) }
            }
        }
    }
}

/** Play and shuffle (or the page's own two actions) as a connected pair. */
@Composable
private fun HeroButtons(info: HeaderInfo) {
    val c = MaterialTheme.colorScheme
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
                .pressable(RoundedCornerShape(28.dp, 10.dp, 10.dp, 28.dp), c.primary, pressedScale = 0.95f, onClick = info.onPrimary),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(info.primary.second, null, tint = c.onPrimary)
            Text("  ${info.primary.first}", color = c.onPrimary, fontWeight = FontWeight.Bold)
        }
        Row(
            Modifier
                .weight(1f)
                .fillMaxSize()
                .pressable(RoundedCornerShape(10.dp, 28.dp, 28.dp, 10.dp), c.onBackground.copy(alpha = 0.12f), pressedScale = 0.95f, onClick = info.onSecondary),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(info.secondary.second, null, tint = c.onBackground)
            Text("  ${info.secondary.first}", color = c.onBackground, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Wide screens: the page's cover, title and actions in a column beside its songs. */
@Composable
private fun SideHero(info: HeaderInfo, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier
            .statusBarsPadding()
            .padding(start = 32.dp, end = 16.dp, top = 72.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        BoxWithConstraints(Modifier.weight(1f, fill = false)) {
            val side = minOf(maxWidth, maxHeight, 380.dp)
            AsyncImage(
                model = info.art?.atSize(ArtSize.CARD),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(side)
                    .shadow(24.dp, RoundedCornerShape(36.dp))
                    .clip(RoundedCornerShape(36.dp)),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(info.eyebrow, style = MaterialTheme.typography.labelSmall, color = c.primary)
        Text(info.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = c.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
        info.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.widthIn(max = 420.dp)) { HeroButtons(info) }
    }
}

@Composable
private fun Hero(info: HeaderInfo) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxWidth()
            .height(520.dp),
    ) {
        ProgressiveBlurImage(info.art?.atSize(1080), Modifier.fillMaxSize(), start = 0.5f, end = 1f, maxRadius = 64.dp)
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.4f to Color.Transparent, 0.8f to c.background.copy(alpha = 0.6f), 1f to c.background)),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 20.dp),
        ) {
            Text(info.eyebrow, style = MaterialTheme.typography.labelSmall, color = c.primary)
            Text(info.title, style = MaterialTheme.typography.displayMedium, color = c.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
            info.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.height(16.dp))
            HeroButtons(info)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun NumberedSong(number: Int, song: SongItem, onClick: () -> Unit, onPlayNext: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val menu = LocalItemMenu.current
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    menu.openFor(song)
                },
            )
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(number.toString(), Modifier.width(32.dp), color = c.onSurfaceVariant, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Column(Modifier.weight(1f)) {
            Text(song.title, color = c.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artists.joinToString { it.name }, color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        song.duration?.let { Text("%d:%02d".format(it / 60, it % 60), color = c.onSurfaceVariant, fontSize = 13.sp) }
        IconButton(onClick = onPlayNext) { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Play ${song.title} next", tint = c.onSurfaceVariant) }
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
private fun ArtistScreen(browseId: String, actions: PageActions) {
    val (load, retry) = rememberLoad(browseId) { YouTube.artist(browseId) }
    val page = (load as? Load.Ready)?.value
    PageFrame(
        load = load,
        retry = retry,
        art = page?.artist?.thumbnail,
        actions = actions,
        header = { p ->
            HeaderInfo(
                item = p.artist,
                art = p.artist.thumbnail,
                eyebrow = "ARTIST",
                title = p.artist.title,
                subtitle = p.monthlyListenerCount ?: p.subscriberCountText,
                primary = "Shuffle" to Icons.Rounded.Shuffle,
                onPrimary = { actions.playItem(p.artist, true) },
                secondary = "Radio" to Icons.Rounded.Sensors,
                onSecondary = { actions.playItem(p.artist, false) },
            )
        },
    ) { p ->
        p.sections
            .map { it.copy(items = it.items.filter(::isMusic)) }
            .filter { it.items.isNotEmpty() && !it.title.contains("video", ignoreCase = true) && !it.title.contains("episode", ignoreCase = true) }
            .forEach { section ->
                item { SectionTitle(section.title) }
                val songs = section.items.filterIsInstance<SongItem>()
                if (songs.size == section.items.size) {
                    // Top songs: a list; tapping one plays the artist's top songs from there.
                    itemsIndexed(songs.take(10)) { index, song ->
                        NumberedSong(
                            number = index + 1,
                            song = song,
                            onClick = {
                                actions.play(
                                    QueueBuilder.forList(
                                        songs,
                                        index,
                                        PlaybackContext("artist-${p.artist.id}", "${p.artist.title}: ${section.title}", p.artist.thumbnail, null, isCollection = false),
                                        shuffle = false,
                                    ),
                                )
                            },
                            onPlayNext = { actions.playNext(song) },
                        )
                    }
                } else {
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(section.items) { ItemCard(it, onClick = actions.open) }
                        }
                    }
                }
            }
        p.description?.takeIf { it.isNotBlank() }?.let { about ->
            item { SectionTitle("About") }
            item {
                Text(about, Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, lineHeight = 22.sp, maxLines = 8, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AlbumScreen(browseId: String, actions: PageActions) {
    val (load, retry) = rememberLoad(browseId) { YouTube.album(browseId) }
    val page = (load as? Load.Ready)?.value
    PageFrame(
        load = load,
        retry = retry,
        art = page?.album?.thumbnail,
        actions = actions,
        header = { p ->
            val ctx = PlaybackContext(p.album.browseId, p.album.title, p.album.thumbnail, p.album.playlistId, isCollection = true)
            HeaderInfo(
                item = p.album,
                art = p.album.thumbnail,
                eyebrow = "ALBUM",
                title = p.album.title,
                subtitle = listOfNotNull(
                    p.album.artists?.joinToString { it.name },
                    p.album.year?.toString(),
                    "${p.songs.size} songs",
                ).joinToString(" · "),
                primary = "Play" to Icons.Rounded.PlayArrow,
                onPrimary = { actions.play(QueueBuilder.forList(p.songs, 0, ctx, shuffle = false)) },
                secondary = "Shuffle" to Icons.Rounded.Shuffle,
                onSecondary = { actions.play(QueueBuilder.forList(p.songs, 0, ctx, shuffle = true)) },
            )
        },
    ) { p ->
        val ctx = PlaybackContext(p.album.browseId, p.album.title, p.album.thumbnail, p.album.playlistId, isCollection = true)
        item { Spacer(Modifier.height(8.dp)) }
        itemsIndexed(p.songs) { index, song ->
            NumberedSong(index + 1, song, onClick = { actions.play(QueueBuilder.forList(p.songs, index, ctx, shuffle = false)) }, onPlayNext = { actions.playNext(song) })
        }
        val others = p.otherVersions.filter(::isMusic)
        if (others.isNotEmpty()) {
            item { SectionTitle("Other versions") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(others) { ItemCard(it, onClick = actions.open) }
                }
            }
        }
    }
}

private class PlaylistData(val playlist: PlaylistItem, val songs: List<SongItem>)

@Composable
private fun PlaylistScreen(playlistId: String, actions: PageActions) {
    val version by LibraryRepository.version.collectAsState()
    val (load, retry) = rememberLoad("$playlistId-$version") {
        runCatching {
            val first = YouTube.playlist(playlistId).getOrThrow()
            val songs = first.songs.toMutableList()
            // Follow every page so long playlists load in full (capped at ~5,000 songs).
            var next = first.songsContinuation ?: first.continuation
            var pages = 0
            while (next != null && pages++ < 50) {
                val more = YouTube.playlistContinuation(next).getOrNull() ?: break
                songs += more.songs
                next = more.continuation
            }
            PlaylistData(first.playlist, songs.filter(::isMusic).distinctBy { it.id })
        }
    }
    val page = (load as? Load.Ready)?.value
    PageFrame(
        load = load,
        retry = retry,
        art = page?.playlist?.thumbnail,
        actions = actions,
        header = { p ->
            val ctx = PlaybackContext(p.playlist.id, p.playlist.title, p.playlist.thumbnail, playlistId, isCollection = true)
            HeaderInfo(
                item = p.playlist,
                art = p.playlist.thumbnail,
                eyebrow = "PLAYLIST",
                title = p.playlist.title,
                subtitle = listOfNotNull(p.playlist.author?.name, "${p.songs.size} songs").joinToString(" · "),
                primary = "Play" to Icons.Rounded.PlayArrow,
                onPrimary = { actions.play(QueueBuilder.forList(p.songs, 0, ctx, shuffle = false)) },
                secondary = "Remix" to Icons.Rounded.Shuffle,
                onSecondary = { actions.play(QueueBuilder.forList(p.songs, 0, ctx, shuffle = true)) },
            )
        },
    ) { p ->
        val ctx = PlaybackContext(p.playlist.id, p.playlist.title, p.playlist.thumbnail, playlistId, isCollection = true)
        item { Spacer(Modifier.height(8.dp)) }
        itemsIndexed(p.songs) { index, song ->
            val removable = if (p.playlist.isEditable) song.setVideoId?.let { RemoveFrom(playlistId, it) } else null
            ItemRow(song, onClick = { actions.play(QueueBuilder.forList(p.songs, index, ctx, shuffle = false)) }, removeFrom = removable) {
                IconButton(onClick = { actions.playNext(song) }) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Play ${song.title} next", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------

@Composable
private fun HistoryScreen(actions: PageActions) {
    val (load, retry) = rememberLoad("history", cache = false) {
        YouTube.musicHistory().map { page -> page.sections.orEmpty().map { it.title to it.songs.filter(::isMusic) }.filter { it.second.isNotEmpty() } }
    }
    val sections = (load as? Load.Ready)?.value
    val all = sections?.flatMap { it.second }?.distinctBy { it.id }.orEmpty()
    val ctx = PlaybackContext("history", "History", all.firstOrNull()?.thumbnail, null, isCollection = false)
    PageFrame(
        load = load,
        retry = retry,
        art = all.firstOrNull()?.thumbnail,
        actions = actions,
        header = {
            HeaderInfo(
                item = null,
                art = all.firstOrNull()?.thumbnail,
                eyebrow = "YOUR LIBRARY",
                title = "History",
                subtitle = "${all.size} songs you've played recently",
                primary = "Play" to Icons.Rounded.PlayArrow,
                onPrimary = { if (all.isNotEmpty()) actions.play(QueueBuilder.forList(all, 0, ctx, shuffle = false)) },
                secondary = "Shuffle" to Icons.Rounded.Shuffle,
                onSecondary = { if (all.isNotEmpty()) actions.play(QueueBuilder.forList(all, 0, ctx, shuffle = true)) },
            )
        },
    ) { list ->
        list.forEach { (title, songs) ->
            item { SectionTitle(title) }
            items(songs) { song ->
                ItemRow(song, onClick = { actions.playItem(it, false) }) {
                    IconButton(onClick = { actions.playNext(song) }) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Play ${song.title} next", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistsScreen(actions: PageActions) {
    val playlists by LibraryRepository.playlists.collectAsState()
    val (load, retry) = rememberLoad("playlists") { LibraryRepository.refresh() }
    val shown = if (playlists.isNotEmpty()) Load.Ready(playlists) else load
    PageFrame(
        load = shown,
        retry = retry,
        art = playlists.firstOrNull { it.thumbnail != null }?.thumbnail,
        actions = actions,
        header = { list ->
            HeaderInfo(
                item = null,
                art = list.firstOrNull { it.thumbnail != null }?.thumbnail,
                eyebrow = "YOUR LIBRARY",
                title = "Your playlists",
                subtitle = "${list.size} playlists",
                primary = "New playlist" to Icons.Rounded.Add,
                onPrimary = actions.createPlaylist,
                secondary = "Liked" to Icons.Rounded.ThumbUp,
                onSecondary = { actions.open(PlaylistItem("LM", "Liked music", null, null, null, null, null, null)) },
            )
        },
    ) { list ->
        item { Spacer(Modifier.height(8.dp)) }
        items(list) { ItemRow(it, onClick = actions.open) }
    }
}

private class SavedData(val albums: List<AlbumItem>, val artists: List<ArtistItem>)

/** Albums and artists you've saved on YouTube Music. */
@Composable
private fun SavedScreen(actions: PageActions) {
    val (load, retry) = rememberLoad("saved") {
        runCatching {
            coroutineScope {
                val albums = async { LibraryRepository.savedAlbums().getOrDefault(emptyList()) }
                val artists = async { LibraryRepository.savedArtists().getOrDefault(emptyList()) }
                SavedData(albums.await(), artists.await())
            }
        }
    }
    val data = (load as? Load.Ready)?.value
    val art = data?.albums?.firstOrNull()?.thumbnail ?: data?.artists?.firstOrNull()?.thumbnail
    PageFrame(
        load = load,
        retry = retry,
        art = art,
        actions = actions,
        header = { d ->
            HeaderInfo(
                item = null,
                art = art,
                eyebrow = "YOUR LIBRARY",
                title = "Albums & artists",
                subtitle = "${d.albums.size} albums · ${d.artists.size} artists",
                primary = "Shuffle albums" to Icons.Rounded.Shuffle,
                onPrimary = { d.albums.randomOrNull()?.let { actions.playItem(it, true) } },
                secondary = "Liked" to Icons.Rounded.ThumbUp,
                onSecondary = { actions.open(PlaylistItem("LM", "Liked music", null, null, null, null, null, null)) },
            )
        },
    ) { d ->
        if (d.artists.isNotEmpty()) {
            item { SectionTitle("Artists") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(d.artists) { ItemCard(it, onClick = actions.open) }
                }
            }
        }
        if (d.albums.isNotEmpty()) {
            item { SectionTitle("Albums") }
            items(d.albums) { ItemRow(it, onClick = actions.open) }
        }
        if (d.albums.isEmpty() && d.artists.isEmpty()) {
            item {
                Text(
                    "Nothing saved yet. Save albums or follow artists in YouTube Music and they'll show up here.",
                    Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
