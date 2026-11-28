package app.wave.music.ui.detail

import app.wave.music.ui.components.glass
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import app.wave.music.ui.menu.menuAnchor
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Star
import app.wave.music.platform.topBarPadding
import app.wave.music.platform.topInset
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Check
import app.wave.music.ui.components.WheelRow
import androidx.compose.ui.draw.clipToBounds
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import app.wave.music.ui.components.ArtSize
import app.wave.music.ui.components.BlurredImage
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import app.wave.music.platform.currentWindowDp
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import app.wave.music.ui.i18n.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import app.wave.music.ui.i18n.Text
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
    /** Live radio: popular stations, or ones matching [query]. */
    data class Radio(val query: String = "") : Destination { override val id = "radio" }
    /** Music videos picked for you. */
    data object Videos : Destination { override val id = "videos" }
    data object Settings : Destination { override val id = "settings" }
    data class SettingsPage(override val id: String) : Destination

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
    /** Opens another page (Prisma's settings pages). */
    val navigate: (Destination) -> Unit = {},
    val signOut: () -> Unit = {},
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
    private val entries = app.wave.music.data.Lru<Any, Pair<Long, Any?>>(MAX)

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
        is Destination.Radio -> app.wave.music.ui.radio.RadioScreen(destination.query, actions)
        Destination.Videos -> app.wave.music.ui.video.VideosScreen(actions)
        Destination.Settings -> app.wave.music.ui.settings.PrismaSettingsHome(
            onBack = actions.back,
            onSection = { actions.navigate(Destination.SettingsPage(it.id)) },
            onSignOut = actions.signOut,
        )
        is Destination.SettingsPage -> app.wave.music.ui.settings.PrismaSettingsPage(
            app.wave.music.ui.settings.SettingsSection.of(destination.id),
            onBack = actions.back,
            open = { actions.navigate(Destination.SettingsPage(it.id)) },
        )
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
    /** A third button joined to the end of the pair (Follow, on artist pages). */
    val extra: (@Composable () -> Unit)? = null,
    /** Phones: an artist page gets the big photo and name; albums and playlists the centered cover. */
    val artist: Boolean = false,
    /** The small line under the subtitle ("Album · 2024 · 12 songs"). */
    val meta: String? = null,
    /** An artist's newest album or single, shown as a card under the buttons. */
    /** The artist's follow button, round, for the phone layout. */
    val followCompact: (@Composable () -> Unit)? = null,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> PageFrame(
    load: Load<T>,
    retry: () -> Unit,
    art: String?,
    actions: PageActions,
    header: (T) -> HeaderInfo,
    /** A list of songs (album, playlist): big screens show a # · Title · Time heading over it. */
    table: Boolean = false,
    content: LazyListScope.(T) -> Unit,
) {
    val matchColors = WaveSettings.state.collectAsState().value.matchSongColors
    MaterialTheme(colorScheme = rememberArtColorScheme(if (matchColors) art?.atSize(600) else null)) {
        val c = MaterialTheme.colorScheme
        app.wave.music.ui.components.ReportPageLook(c, art)
        val backdrop = rememberBackdrop()
        // Phones: a frosted strip across the top once you've scrolled past the photo.
        val pageGlass = dev.chrisbanes.haze.rememberHazeState()
        val phoneList = androidx.compose.foundation.lazy.rememberLazyListState()
        val scrolledPast by remember { androidx.compose.runtime.derivedStateOf { phoneList.firstVisibleItemIndex > 0 || phoneList.firstVisibleItemScrollOffset > 900 } }
        val statusTop = topInset()
        Box(
            Modifier
                .fillMaxSize()
                .blocksTouchesBelow()
                .background(c.background),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .backdropSource(backdrop)
                    .hazeSource(pageGlass),
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
                        val config = currentWindowDp()
                        // Tablets get the side-by-side layout below (the cover and buttons beside the
                        // songs), which suits a touch screen better than the computer's table.
                        if (config.screenWidthDp >= 600 && (config.screenWidthDp > config.screenHeightDp || app.wave.music.ui.components.LocalShell.current)) {
                            // Side by side on wide screens, over the art blurred across the window.
                            BlurredImage(info.art?.atSize(ArtSize.BLUR), Modifier.fillMaxSize().graphicsLayer { scaleX = 1.2f; scaleY = 1.2f }, radius = 96.dp)
                            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to c.background.copy(alpha = 0.55f), 1f to c.background.copy(alpha = 0.88f))))
                            // A generous info column, and the songs right beside it at a readable width,
                            // so big windows feel full without stretching rows edge to edge.
                            BoxWithConstraints(Modifier.fillMaxSize()) {
                                val side = (maxWidth * 0.34f).coerceIn(320.dp, 500.dp)
                                Row(Modifier.fillMaxSize()) {
                                    val sideMenu = LocalItemMenu.current
                                    SideHero(info, Modifier.width(side).fillMaxHeight()) { info.item?.let { sideMenu.open(MenuTarget.Collection(it, onItsPage = true)) } }
                                    Box(Modifier.weight(1f).fillMaxHeight()) {
                                        LazyColumn(
                                            Modifier.widthIn(max = 1100.dp).fillMaxSize(),
                                            contentPadding = PaddingValues(top = statusTop + 76.dp, bottom = 140.dp, start = 8.dp, end = 24.dp),
                                        ) { content(load.value) }
                                    }
                                }
                            }
                        } else {
                            val menu = LocalItemMenu.current
                            LazyColumn(Modifier.fillMaxSize(), state = phoneList, contentPadding = PaddingValues(bottom = 140.dp + app.wave.music.ui.components.LocalMiniPlayerSpace.current)) {
                                item {
                                    androidx.compose.runtime.CompositionLocalProvider(
                                        LocalHeroScroll provides { if (phoneList.firstVisibleItemIndex == 0) phoneList.firstVisibleItemScrollOffset.toFloat() else 100_000f },
                                    ) {
                                        if (info.artist) ArtistHero(info, actions.open)
                                        else CollectionHero(info) { info.item?.let { menu.open(MenuTarget.Collection(it, onItsPage = true)) } }
                                    }
                                }
                                content(load.value)
                            }
                        }
                    }
                }
                }
                }
            }
            if (app.wave.music.ui.components.LocalShell.current) ProgressiveBackdropBlur(backdrop, Modifier.fillMaxWidth().height(statusTop + 96.dp))
            else {
                // A whisper of blur right under the status bar over the full art, fading out fast.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(statusTop + 18.dp)
                        .glass(pageGlass, 10.dp, Color.Transparent, emptyList(), Color.Transparent, progressive = true),
                )
                val glassAlpha by androidx.compose.animation.core.animateFloatAsState(if (scrolledPast) 1f else 0f, tween(240), label = "pageGlass")
                if (glassAlpha > 0f) Box(
                    Modifier
                        .fillMaxWidth()
                        .height(statusTop + 76.dp)
                        .graphicsLayer { alpha = glassAlpha }
                        .glass(pageGlass, 24.dp, c.background, listOf(dev.chrisbanes.haze.HazeTint(c.background.copy(alpha = 0.5f))), c.background.copy(alpha = 0.92f), progressive = true),
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(statusTop + 96.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent))),
            )
            Box(
                Modifier
                    .topBarPadding()
                    .padding(12.dp)
                    .size(48.dp)
                    .pressable(CircleShape, Color.Black.copy(alpha = 0.32f), pressedScale = 0.86f, onClick = actions.back),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
            val pageItem = (load as? Load.Ready)?.let { header(it.value).item }
            if (pageItem != null) {
                val menu = LocalItemMenu.current
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .topBarPadding()
                        .padding(12.dp)
                        .size(48.dp)
                        .menuAnchor(Color.Black.copy(alpha = 0.32f), Color.White)
                        .pressable(CircleShape, Color.Black.copy(alpha = 0.32f), pressedScale = 0.86f) { menu.open(MenuTarget.Collection(pageItem, onItsPage = true)) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.MoreVert, "More options", tint = Color.White) }
            }
        }
    }
}




/** Wide screens: the page's cover, title and actions in a column beside its songs. */
@Composable
private fun SideHero(info: HeaderInfo, modifier: Modifier, onAdd: () -> Unit = {}) {
    // Tablets: Prisma's header beside the songs. The cover (round for an artist), the title in
    // Prisma's type, and the same play row as on phones.
    val c = MaterialTheme.colorScheme
    val corner = app.wave.music.platform.PrismaLook.corner(32.dp)
    Column(
        modifier
            .topBarPadding()
            .padding(start = 32.dp, end = 24.dp, top = 76.dp, bottom = 24.dp + app.wave.music.ui.components.LocalMiniPlayerSpace.current),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoxWithConstraints(Modifier.weight(1f, fill = false), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight, 420.dp)
            val shape = if (info.artist) CircleShape else RoundedCornerShape(corner)
            AsyncImage(
                model = info.art?.atSize(ArtSize.FULL),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(side).shadow(28.dp, shape).clip(shape),
            )
        }
        Text(
            if (info.artist && !app.wave.music.platform.PrismaLook.legacy) info.title.uppercase() else info.title,
            Modifier.padding(top = 20.dp),
            color = c.onBackground,
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = app.wave.music.platform.prismaDisplayFont,
                fontSize = if (info.artist) 40.sp else 30.sp,
                fontWeight = if (info.artist) FontWeight.Black else FontWeight.Bold,
                letterSpacing = (if (info.artist) -1.2 else -0.4).sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = if (info.artist) 42.sp else 34.sp,
            ),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        info.subtitle?.let { Text(it, Modifier.padding(top = 4.dp), color = if (info.artist) c.onBackground.copy(alpha = 0.7f) else c.primary, style = androidx.compose.ui.text.TextStyle(fontFamily = app.wave.music.platform.prismaTextFont, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center), maxLines = 2, overflow = TextOverflow.Ellipsis) }
        info.meta?.let { Text(it, Modifier.padding(top = 2.dp), color = c.onBackground.copy(alpha = 0.6f), style = androidx.compose.ui.text.TextStyle(fontFamily = app.wave.music.platform.prismaTextFont, fontSize = 13.sp)) }
        if (info.artist) {
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                RoundButton(info.secondary.second, info.secondary.first, onClick = info.onSecondary)
                Box(
                    Modifier.size(76.dp).pressable(CircleShape, c.primary, pressedScale = 0.92f, onClick = info.onPrimary),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.PlayArrow, info.primary.first, tint = c.onPrimary, modifier = Modifier.size(40.dp)) }
                info.followCompact?.invoke() ?: Spacer(Modifier.size(52.dp))
            }
        } else CollectionButtons(info, onAdd)
    }
}



/**
 * Prisma (phones): an album or playlist. Its art fills the top of the page with the name and
 * buttons over its bottom edge; double-tap the art for the square cover, centered, instead.
 */
@Composable
private fun CollectionHero(info: HeaderInfo, onAdd: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val statusTop = topInset()
    // Legacy opens on 1.0's square cover; double-tap swaps either way.
    var square by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(app.wave.music.platform.PrismaLook.legacy) }
    val flip = Modifier.pointerInput(Unit) { detectTapGestures(onDoubleTap = { square = !square }) }
    androidx.compose.animation.AnimatedContent(square, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "cover") { isSquare ->
        if (!isSquare) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                PinnedHero(info.art, Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.25f), 0.18f to Color.Transparent, 0.55f to Color.Transparent, 0.85f to c.background.copy(alpha = 0.8f), 1f to c.background), Modifier.fillMaxWidth().height(500.dp).then(flip)) { scrolled, heroPx ->
                    CollectionText(info, Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp).graphicsLayer { alpha = 1f - (scrolled() / heroPx * 1.4f).coerceIn(0f, 1f) }, details = false)
                }
                CollectionButtons(info, onAdd)
            }
        } else {
            Box(Modifier.fillMaxWidth()) {
                BlurredImage(info.art?.atSize(ArtSize.BLUR), Modifier.fillMaxWidth().height(460.dp).clipToBounds().graphicsLayer { scaleX = 1.4f; scaleY = 1.4f }, radius = 90.dp)
                Box(Modifier.fillMaxWidth().height(460.dp).background(Brush.verticalGradient(0f to c.background.copy(alpha = 0.35f), 1f to c.background)))
                Column(Modifier.fillMaxWidth().padding(top = statusTop + 68.dp, start = 24.dp, end = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = info.art?.atSize(ArtSize.FULL),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(244.dp).shadow(24.dp, RoundedCornerShape(app.wave.music.platform.PrismaLook.corner(24.dp))).clip(RoundedCornerShape(app.wave.music.platform.PrismaLook.corner(24.dp))).then(flip),
                    )
                    CollectionText(info, Modifier.padding(top = 18.dp))
                    CollectionButtons(info, onAdd)
                }
            }
        }
    }
}

@Composable
private fun CollectionText(info: HeaderInfo, modifier: Modifier, details: Boolean = true) {
    val c = MaterialTheme.colorScheme
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            info.title,
            color = c.onBackground,
            style = androidx.compose.ui.text.TextStyle(fontFamily = app.wave.music.platform.prismaDisplayFont, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        info.subtitle?.let {
            Text(it, color = c.primary, style = androidx.compose.ui.text.TextStyle(fontFamily = app.wave.music.platform.prismaTextFont, fontSize = 17.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (details) info.meta?.let {
            Text(it, Modifier.padding(top = 2.dp), color = c.onBackground.copy(alpha = 0.7f), style = androidx.compose.ui.text.TextStyle(fontFamily = app.wave.music.platform.prismaTextFont, fontSize = 13.sp))
        }
    }
}

@Composable
private fun CollectionButtons(info: HeaderInfo, onAdd: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        RoundButton(info.secondary.second, info.secondary.first, onClick = info.onSecondary)
        Row(
            Modifier.width(196.dp).height(52.dp).pressable(CircleShape, c.primary, pressedScale = 0.95f, onClick = info.onPrimary),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(info.primary.second, null, tint = c.onPrimary)
            Text("  ${info.primary.first}", color = c.onPrimary, style = androidx.compose.ui.text.TextStyle(fontFamily = app.wave.music.platform.prismaDisplayFont, fontSize = 17.sp, fontWeight = FontWeight.Bold))
        }
        RoundButton(Icons.Rounded.Add, "Save, add or share", onClick = onAdd)
    }
}

/** How far the page has scrolled, for [PinnedHero]. */
private val LocalHeroScroll = androidx.compose.runtime.compositionLocalOf<(() -> Float)?> { null }

/**
 * A photo header that stays put behind the page as you scroll: the fade and everything under it
 * slide up over the photo, which dims and eases back a little, so it feels like it's sitting
 * behind the page rather than scrolling with it.
 */
@Composable
private fun PinnedHero(
    art: String?,
    fade: Brush,
    modifier: Modifier,
    overlay: @Composable androidx.compose.foundation.layout.BoxWithConstraintsScope.(scrolled: () -> Float, heroPx: Float) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    // Read straight from the list every frame, so it works however many times you scroll.
    val listScroll = LocalHeroScroll.current
    BoxWithConstraints(modifier) {
        val heroPx = constraints.maxHeight.toFloat()
        val scrolled = { listScroll?.invoke()?.coerceAtLeast(0f) ?: 0f }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = scrolled()
                    val p = (s / heroPx).coerceIn(0f, 1f)
                    translationY = s
                    val k = 1f + p * 0.08f
                    scaleX = k
                    scaleY = k
                    alpha = 1f - p * 0.55f
                },
        ) {
            AsyncImage(art?.atSize(ArtSize.FULL), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
        }
        // The fade moves with the page, and the page's color carries on below it, covering the photo.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(fade)
                    drawRect(c.background, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 1f), size = androidx.compose.ui.geometry.Size(size.width, 4000f))
                },
        )
        overlay(scrolled, heroPx)
    }
}

/**
 * Prisma (phones): an artist's photo across the top with their name set big over its bottom
 * edge, then the buttons with Shuffle big in the middle, and their newest release.
 */
@Composable
private fun ArtistHero(info: HeaderInfo, open: (YTItem) -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        PinnedHero(info.art, Brush.verticalGradient(0.35f to Color.Transparent, 0.82f to c.background.copy(alpha = 0.75f), 1f to c.background), Modifier.fillMaxWidth().height(500.dp)) { scrolled, heroPx ->
            // The name tag: as big as fits on one line, heavy and tightly set. Long names that won't
            // fit even small go onto two lines instead of getting cut off.
            val name = if (app.wave.music.platform.PrismaLook.legacy) info.title else info.title.uppercase()
            val measurer = androidx.compose.ui.text.rememberTextMeasurer()
            val density = androidx.compose.ui.platform.LocalDensity.current
            val avail = constraints.maxWidth - with(density) { 32.dp.roundToPx() }
            fun styleAt(size: Float) = androidx.compose.ui.text.TextStyle(
                fontFamily = app.wave.music.platform.prismaDisplayFont,
                fontSize = size.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.04f * size).sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = (size * 0.95f).sp,
                shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.45f), blurRadius = 24f),
            )
            val (size, lines) = remember(name, avail) {
                fun wide(t: String, size: Float) = measurer.measure(t, styleAt(size), softWrap = false, maxLines = 1).size.width
                var sz = 84f
                while (sz > 36f && wide(name, sz) > avail) sz -= 2f
                if (wide(name, sz) <= avail) sz to 1
                else {
                    val longest = name.split(" ").maxByOrNull { it.length } ?: name
                    var two = 56f
                    while (two > 20f && wide(longest, two) > avail) two -= 2f
                    two to 2
                }
            }
            Text(
                name,
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .graphicsLayer {
                        val s = scrolled()
                        alpha = 1f - (s / heroPx * 1.4f).coerceIn(0f, 1f)
                    },
                color = Color.White,
                style = styleAt(size),
                maxLines = lines,
                softWrap = lines > 1,
            )
        }
        info.subtitle?.let { Text(it, color = c.onBackground.copy(alpha = 0.7f), style = androidx.compose.ui.text.TextStyle(fontFamily = app.wave.music.platform.prismaTextFont, fontSize = 13.sp)) }
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            RoundButton(info.secondary.second, info.secondary.first, onClick = info.onSecondary)
            Box(
                Modifier.size(76.dp).pressable(CircleShape, c.primary, pressedScale = 0.92f, onClick = info.onPrimary),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, info.primary.first, tint = c.onPrimary, modifier = Modifier.size(40.dp)) }
            info.followCompact?.invoke() ?: Spacer(Modifier.size(52.dp))
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).pressable(CircleShape, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f), pressedScale = 0.9f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = MaterialTheme.colorScheme.onBackground) }
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
                primary = "Play" to Icons.Rounded.PlayArrow,
                onPrimary = {
                    val top = p.sections.firstOrNull { sec -> sec.items.isNotEmpty() && sec.items.all { it is SongItem } }?.items?.filterIsInstance<SongItem>()
                    if (top.isNullOrEmpty()) actions.playItem(p.artist, true)
                    else actions.play(QueueBuilder.forList(top, 0, PlaybackContext("artist-${p.artist.id}", "${p.artist.title}: Top songs", p.artist.thumbnail, null, isCollection = false), shuffle = false))
                },
                secondary = "Shuffle" to Icons.Rounded.Shuffle,
                onSecondary = { actions.playItem(p.artist, true) },
                extra = { FollowButton(p.artist.channelId ?: browseId, p.isSubscribed, p.artist.title) },
                artist = true,
                followCompact = { FollowButton(p.artist.channelId ?: browseId, p.isSubscribed, p.artist.title, compact = true) },
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
                        WheelRow {
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
    val shellPage = app.wave.music.ui.components.LocalShell.current
    val (load, retry) = rememberLoad(browseId) { YouTube.album(browseId) }
    val page = (load as? Load.Ready)?.value
    PageFrame(
        load = load,
        retry = retry,
        art = page?.album?.thumbnail,
        actions = actions,
        table = true,
        header = { p ->
            val ctx = PlaybackContext(p.album.browseId, p.album.title, p.album.thumbnail, p.album.playlistId, isCollection = true)
            HeaderInfo(
                item = p.album,
                art = p.album.thumbnail,
                eyebrow = "ALBUM",
                title = p.album.title,
                subtitle = if (shellPage) listOfNotNull(
                    p.album.artists?.joinToString { it.name },
                    p.album.year?.toString(),
                    "${p.songs.size} songs",
                ).joinToString(" · ") else p.album.artists?.joinToString { it.name },
                meta = listOfNotNull("Album", p.album.year?.toString(), "${p.songs.size} songs", totalTime(p.songs)).joinToString(" · "),
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
                WheelRow {
                    items(others) { ItemCard(it, onClick = actions.open) }
                }
            }
        }
    }
}

/** "1h 33m" or "42m" for a list of songs (when their lengths are known). */
private fun totalTime(songs: List<SongItem>): String? {
    val seconds = songs.sumOf { it.duration ?: 0 }
    if (seconds <= 0) return null
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private class PlaylistData(val playlist: PlaylistItem, val songs: List<SongItem>)

@Composable
private fun PlaylistScreen(playlistId: String, actions: PageActions) {
    val shellPage = app.wave.music.ui.components.LocalShell.current
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
        table = true,
        header = { p ->
            val ctx = PlaybackContext(p.playlist.id, p.playlist.title, p.playlist.thumbnail, playlistId, isCollection = true)
            HeaderInfo(
                item = p.playlist,
                art = p.playlist.thumbnail,
                eyebrow = "PLAYLIST",
                title = p.playlist.title,
                subtitle = if (shellPage) listOfNotNull(p.playlist.author?.name, "${p.songs.size} songs").joinToString(" · ") else p.playlist.author?.name,
                meta = listOfNotNull("Playlist", "${p.songs.size} songs", totalTime(p.songs)).joinToString(" · "),
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
            item { SectionTitle("Following") }
            item {
                WheelRow {
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

/** Follows changed this session, so a cached artist page doesn't show the old state. */
private val followed = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

/** Follow (subscribe to) an artist on YouTube Music; they show up under Following in your library. */
@Composable
private fun FollowButton(channelId: String, initiallyFollowing: Boolean, name: String, compact: Boolean = false) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var following by remember(channelId) { mutableStateOf(followed[channelId] ?: initiallyFollowing) }
    val fill by animateColorAsState(if (following) c.primary else c.onBackground.copy(alpha = 0.12f), tween(180), label = "followFill")
    val ink by animateColorAsState(if (following) c.onPrimary else c.onBackground, tween(180), label = "followInk")
    Row(
        Modifier
            .then(if (compact) Modifier.size(52.dp) else Modifier.fillMaxHeight())
            .pressable(if (compact) CircleShape else RoundedCornerShape(10.dp, 28.dp, 28.dp, 10.dp), fill, pressedScale = 0.93f) {
                if (app.wave.music.data.SessionRepository.isGuest) {
                    app.wave.music.ui.components.Notices.show("Sign in with Google to follow artists")
                    return@pressable
                }
                val next = !following
                following = next
                scope.launch {
                    YouTube.subscribeChannel(channelId, next)
                        .onSuccess {
                            followed[channelId] = next
                            app.wave.music.ui.components.Notices.show(if (next) "Following $name" else "Unfollowed $name")
                        }
                        .onFailure {
                            following = !next
                            app.wave.music.ui.components.Notices.show("Couldn't update that")
                        }
                }
            }
            .padding(horizontal = if (compact) 0.dp else 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (compact) {
            Icon(
                if (following) Icons.Rounded.Subscriptions else androidx.compose.material.icons.Icons.Outlined.Subscriptions,
                if (following) "Subscribed to $name" else "Subscribe to $name",
                tint = ink,
            )
            return@Row
        }
        androidx.compose.animation.AnimatedContent(following, label = "follow") { on ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (on) Icons.Rounded.Check else Icons.Rounded.PersonAdd, null, tint = ink)
                Text(if (on) "  Following" else "  Follow", color = ink, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}
