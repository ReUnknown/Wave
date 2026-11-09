package app.wave.music.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wave.music.data.LyricsRepository
import app.wave.music.data.SearchRepository
import app.wave.music.data.SearchTab
import app.wave.music.data.WaveSettings
import app.wave.music.playback.PlaybackContext
import app.wave.music.playback.PlayerConnection
import app.wave.music.playback.QueueBuilder
import app.wave.music.ui.components.ArtSize
import app.wave.music.ui.components.atSize
import app.wave.music.ui.components.pressable
import coil3.compose.AsyncImage
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import kotlinx.coroutines.launch

/** What WaveMini's panel shows. */
enum class MiniTab(val label: String, val icon: ImageVector) {
    UpNext("Up next", Icons.AutoMirrored.Rounded.QueueMusic),
    Lyrics("Lyrics", Icons.Rounded.Lyrics),
    Search("Search", Icons.Rounded.Search),
    Settings("Settings", Icons.Rounded.Settings),
}

/**
 * WaveMini's tabs: connected buttons under the player. Clicking the open one folds the panel
 * away (just the player); clicking another opens it there.
 */
@Composable
fun WaveMiniTabs(tab: MiniTab?, onTab: (MiniTab?) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        MiniTab.entries.forEachIndexed { i, t ->
            val on = t == tab
            val fill by animateColorAsState(if (on) c.primary else Color.White.copy(alpha = 0.08f), tween(160), label = "miniTab")
            val ink by animateColorAsState(if (on) c.onPrimary else Color.White, tween(160), label = "miniTabInk")
            val first = i == 0
            val last = i == MiniTab.entries.lastIndex
            val shape = RoundedCornerShape(if (first) 12.dp else 4.dp, if (last) 12.dp else 4.dp, if (last) 12.dp else 4.dp, if (first) 12.dp else 4.dp)
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pressable(shape, fill, pressedScale = 0.94f) { onTab(if (on) null else t) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(t.icon, t.label, tint = ink, modifier = Modifier.size(18.dp))
                if (on) Text("  ${t.label}", color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

/**
 * WaveMini's panel: everything you reach for while listening, small. Up next, lyrics, search (with
 * artists, albums and playlists opening right here), and the settings you change often; the
 * rest is a click away in the full Wave ([onOpenWave]).
 */
@Composable
fun WaveMiniPanel(tab: MiniTab, onOpenWave: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        AnimatedContent(tab, Modifier.fillMaxSize(), transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(90)) }, label = "miniPanel") { shown ->
            when (shown) {
                MiniTab.UpNext -> MiniQueue()
                MiniTab.Lyrics -> MiniLyrics()
                MiniTab.Search -> MiniSearch()
                MiniTab.Settings -> MiniSettings(onOpenWave)
            }
        }
    }
}

// ---------------------------------------------------------------- Rows

@Composable
private fun MiniRow(
    art: String?,
    title: String,
    subtitle: String,
    round: Boolean = false,
    current: Boolean = false,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(RoundedCornerShape(10.dp), if (current) c.primary.copy(alpha = 0.18f) else Color.Transparent, pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AsyncImage(
            art?.atSize(ArtSize.THUMB),
            null,
            Modifier.size(38.dp).clip(if (round) CircleShape else RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.08f)),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f)) {
            Text(title, color = if (current) c.primary else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) Text(subtitle, color = Color.White.copy(alpha = 0.65f), fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
private fun SmallIcon(icon: ImageVector, label: String, tint: Color = Color.White.copy(alpha = 0.7f), onClick: () -> Unit) {
    Box(Modifier.size(30.dp).pressable(CircleShape, pressedScale = 0.86f, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun Heading(text: String) {
    Text(text.uppercase(), color = Color.White.copy(alpha = 0.55f), fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 4.dp))
}

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
    }
}

// ---------------------------------------------------------------- Up next

@Composable
private fun MiniQueue() {
    val state by PlayerConnection.state.collectAsState()
    val upcoming = state.upcoming.take(80)
    if (upcoming.isEmpty()) return Centered("Nothing after this song")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(6.dp)) {
        items(upcoming, key = { it }) { index ->
            val song = state.queue.getOrNull(index) ?: return@items
            MiniRow(song.artworkUrl, song.title, song.artist, trailing = {
                SmallIcon(Icons.Rounded.Close, "Remove from queue") { PlayerConnection.removeAt(index) }
            }) { PlayerConnection.skipTo(index) }
        }
    }
}

// ---------------------------------------------------------------- Lyrics

/** The current line big and bright, the rest dimmed; scrolls with the song. Click a line to jump to it. */
@Composable
private fun MiniLyrics() {
    val state by PlayerConnection.state.collectAsState()
    val song = state.current ?: return Centered("Nothing playing")
    var lyrics by remember(song.videoId) { mutableStateOf<app.wave.music.data.Lyrics?>(null) }
    var loading by remember(song.videoId) { mutableStateOf(true) }
    LaunchedEffect(song.videoId, state.durationMs > 0) {
        if (state.durationMs <= 0) return@LaunchedEffect
        lyrics = LyricsRepository.load(song.videoId, song.title, song.artist, state.durationMs, song.album)
        loading = false
    }
    val found = lyrics
    if (found == null) return if (loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(48.dp)) } else Centered("No lyrics for this one")
    val position = rememberPosition(song.videoId, state.isPlaying, everyMs = 200)
    val active = if (found.synced) found.lines.indexOfLast { it.startMs <= position.value + 150 } else -1
    val list = rememberLazyListState()
    LaunchedEffect(active) { if (active >= 0) list.animateScrollToItem((active - 1).coerceAtLeast(0)) }
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(found.lines.size) { i ->
            val line = found.lines[i]
            val now = i == active
            val alpha = when {
                !found.synced -> 0.9f
                now -> 1f
                i < active -> 0.35f
                else -> 0.5f
            }
            Text(
                line.text,
                color = Color.White.copy(alpha = alpha),
                fontSize = if (now) 19.sp else 16.sp,
                lineHeight = if (now) 24.sp else 21.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .then(if (found.synced) Modifier.pressable(RoundedCornerShape(8.dp), pressedScale = 0.98f) { PlayerConnection.seekTo(line.startMs) } else Modifier),
            )
        }
        item { Text("Lyrics from ${found.source}", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp)) }
    }
}

// ---------------------------------------------------------------- Search, artists, albums

private sealed interface Page {
    data class Artist(val item: ArtistItem) : Page
    data class Songs(val item: YTItem) : Page
}

@Composable
private fun MiniSearch() {
    val stack = remember { mutableStateListOf<Page>() }
    val top = stack.lastOrNull()
    AnimatedContent(top, Modifier.fillMaxSize(), transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(90)) }, label = "miniSearchPage") { page ->
        when (page) {
            null -> SearchHome(onOpen = { stack += it })
            is Page.Artist -> ArtistPageMini(page.item, onBack = { stack.removeLastOrNull() }, onOpen = { stack += it })
            is Page.Songs -> SongsPageMini(page.item, onBack = { stack.removeLastOrNull() })
        }
    }
}

/** Remembered across tab switches, so coming back to search shows what you found. */
private object MiniSearchState {
    var query by mutableStateOf("")
    var results by mutableStateOf<List<app.wave.music.data.SearchGroup>>(emptyList())
}

@Composable
private fun SearchHome(onOpen: (Page) -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (MiniSearchState.results.isEmpty()) runCatching { focus.requestFocus() } }
    fun run() {
        val q = MiniSearchState.query.trim()
        if (q.isEmpty()) return
        busy = true
        scope.launch {
            MiniSearchState.results = SearchRepository.search(q, SearchTab.Top).getOrDefault(emptyList())
            busy = false
            SearchRepository.remember(q)
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        Row(
            Modifier.padding(top = 8.dp).fillMaxWidth().height(38.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
            Box(Modifier.weight(1f).padding(start = 8.dp)) {
                if (MiniSearchState.query.isEmpty()) Text("Songs, artists, albums", color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp)
                BasicTextField(
                    MiniSearchState.query,
                    onValueChange = { MiniSearchState.query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { run() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        // Typing a space searches, it doesn't pause the music.
                        .onFocusChanged { app.wave.music.ui.components.Shortcuts.typing = it.isFocused }
                        .onEnter { run() },
                )
            }
            if (MiniSearchState.query.isNotEmpty()) SmallIcon(Icons.Rounded.Close, "Clear") { MiniSearchState.query = ""; MiniSearchState.results = emptyList() }
        }
        when {
            busy -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(48.dp)) }
            MiniSearchState.results.isEmpty() -> Centered("Search for a song, artist or album")
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
                MiniSearchState.results.forEach { group ->
                    item(key = "h-${group.title}") { Heading(group.title) }
                    items(group.items.take(6), key = { "${group.title}-${it.id}" }) { item -> ResultRow(item, onOpen) }
                }
            }
        }
    }
}

/** Enter in the field searches (computers have no on-screen search key). */
private fun Modifier.onEnter(action: () -> Unit): Modifier = onPreviewKeyEvent { e ->
    if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) {
        action()
        true
    } else false
}

@Composable
private fun ResultRow(item: YTItem, onOpen: (Page) -> Unit) {
    val scope = rememberCoroutineScope()
    val subtitle = when (item) {
        is SongItem -> "Song • " + item.artists.joinToString { it.name }
        is AlbumItem -> "Album • " + item.artists.orEmpty().joinToString { it.name }
        is ArtistItem -> "Artist"
        is PlaylistItem -> "Playlist" + (item.author?.name?.let { " • $it" } ?: "")
        else -> ""
    }
    MiniRow(item.thumbnail, item.title, subtitle, round = item is ArtistItem) {
        when (item) {
            is SongItem -> scope.launch { QueueBuilder.forItem(item).onSuccess { PlayerConnection.play(it) } }
            is ArtistItem -> onOpen(Page.Artist(item))
            else -> onOpen(Page.Songs(item))
        }
    }
}

@Composable
private fun PageHeader(art: String?, title: String, subtitle: String?, round: Boolean, onBack: () -> Unit, buttons: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SmallIcon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White, onClick = onBack)
        AsyncImage(art?.atSize(ArtSize.THUMB), null, Modifier.size(48.dp).clip(if (round) CircleShape else RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, color = Color.White.copy(alpha = 0.65f), fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        buttons()
    }
}

@Composable
private fun PillButton(icon: ImageVector, label: String, primary: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.height(32.dp).pressable(RoundedCornerShape(10.dp), if (primary) c.primary else Color.White.copy(alpha = 0.1f), pressedScale = 0.94f, onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (primary) c.onPrimary else Color.White, modifier = Modifier.size(16.dp))
        Text(" $label", color = if (primary) c.onPrimary else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ArtistPageMini(artist: ArtistItem, onBack: () -> Unit, onOpen: (Page) -> Unit) {
    val scope = rememberCoroutineScope()
    var page by remember(artist.id) { mutableStateOf<com.metrolist.innertube.pages.ArtistPage?>(null) }
    LaunchedEffect(artist.id) { page = YouTube.artist(artist.id).getOrNull() }
    Column(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
        PageHeader(artist.thumbnail, artist.title, page?.subscriberCountText, round = true, onBack = onBack) {
            PillButton(Icons.Rounded.Shuffle, "Shuffle", true) { scope.launch { QueueBuilder.forItem(page?.artist ?: artist, shuffle = true).onSuccess { PlayerConnection.play(it) } } }
            PillButton(Icons.Rounded.Radio, "Radio", false) { scope.launch { QueueBuilder.forItem(page?.artist ?: artist).onSuccess { PlayerConnection.play(it) } } }
        }
        val loaded = page
        if (loaded == null) return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(48.dp)) }
        LazyColumn(Modifier.fillMaxSize()) {
            loaded.sections.filter { it.items.isNotEmpty() }.forEach { section ->
                item(key = "h-${section.title}") { Heading(section.title) }
                items(section.items.take(if (section.items.all { it is SongItem }) 5 else 6), key = { "${section.title}-${it.id}" }) { item -> ResultRow(item, onOpen) }
            }
        }
    }
}

/** An album or playlist: play, shuffle, and its songs. */
@Composable
private fun SongsPageMini(item: YTItem, onBack: () -> Unit) {
    var songs by remember(item.id) { mutableStateOf<List<SongItem>?>(null) }
    LaunchedEffect(item.id) {
        songs = when (item) {
            is AlbumItem -> YouTube.album(item.browseId).getOrNull()?.songs
            is PlaylistItem -> YouTube.playlist(item.id.removePrefix("VL")).getOrNull()?.songs
            else -> emptyList()
        }
    }
    val context = PlaybackContext(item.id, item.title, item.thumbnail, (item as? AlbumItem)?.playlistId ?: (item as? PlaylistItem)?.id?.removePrefix("VL"), isCollection = true)
    fun play(start: Int, shuffle: Boolean) {
        val list = songs ?: return
        if (list.isNotEmpty()) PlayerConnection.play(QueueBuilder.forList(list, start, context, shuffle))
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
        PageHeader(item.thumbnail, item.title, songs?.let { "${it.size} songs" }, round = false, onBack = onBack) {
            PillButton(Icons.Rounded.PlayArrow, "Play", true) { play(0, false) }
            PillButton(Icons.Rounded.Shuffle, "Shuffle", false) { play(0, true) }
        }
        val loaded = songs
        if (loaded == null) return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(48.dp)) }
        LazyColumn(Modifier.fillMaxSize()) {
            items(loaded.size) { i ->
                val s = loaded[i]
                MiniRow(s.thumbnail ?: item.thumbnail, s.title, s.artists.joinToString { it.name }) { play(i, false) }
            }
        }
    }
}

// ---------------------------------------------------------------- Settings

/** The settings you change while listening; everything else is in the full Wave. */
@Composable
private fun MiniSettings(onOpenWave: () -> Unit) {
    val settings by WaveSettings.state.collectAsState()
    val state by PlayerConnection.state.collectAsState()
    val scope = rememberCoroutineScope()
    fun change(f: (app.wave.music.data.Settings) -> app.wave.music.data.Settings) = scope.launch { WaveSettings.update(f) }
    val c = MaterialTheme.colorScheme
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item { Heading("Sound") }
        item {
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Volume", color = Color.White, fontSize = 13.sp, modifier = Modifier.width(80.dp))
                SlimVolume(Color.White)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Speed", color = Color.White, fontSize = 13.sp, modifier = Modifier.width(80.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf(0.75f, 1f, 1.25f, 1.5f).forEach { s ->
                        val on = kotlin.math.abs(state.speed - s) < 0.01f
                        Box(
                            Modifier.height(28.dp).pressable(RoundedCornerShape(8.dp), if (on) c.primary else Color.White.copy(alpha = 0.1f), pressedScale = 0.92f) { PlayerConnection.setSpeed(s) }.padding(horizontal = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (s == 1f) "1×" else "${s}×", color = if (on) c.onPrimary else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        item { MiniToggle("Crossfade", settings.crossfadeSeconds > 0) { on -> change { it.copy(crossfadeSeconds = if (on) 6 else 0) } } }
        item { MiniToggle("Even volume", settings.evenVolume) { on -> change { it.copy(evenVolume = on) } } }
        item { MiniToggle("Autoplay when the queue ends", settings.autoplay) { on -> change { it.copy(autoplay = on) } } }
        item { Heading("Look") }
        item { MiniToggle("Match song colors", settings.matchSongColors) { on -> change { it.copy(matchSongColors = on) } } }
        item { MiniToggle("Move with the music", settings.moveWithMusic) { on -> change { it.copy(moveWithMusic = on) } } }
        item {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().height(40.dp).pressable(RoundedCornerShape(12.dp), Color.White.copy(alpha = 0.1f), pressedScale = 0.97f, onClick = onOpenWave),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.OpenInNew, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text("  All settings in Wave", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MiniToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            modifier = Modifier.height(24.dp),
            colors = SwitchDefaults.colors(checkedTrackColor = c.primary, checkedThumbColor = c.onPrimary),
        )
    }
}
