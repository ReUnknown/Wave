package app.wave.music.ui.home

import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.luminance
import app.wave.music.ui.components.glass
import androidx.compose.ui.layout.layout
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.wave.music.data.LibraryRepository
import app.wave.music.data.SearchGroup
import app.wave.music.data.SearchRepository
import app.wave.music.data.SearchTab
import app.wave.music.data.Session
import app.wave.music.data.Stores
import app.wave.music.data.Suggestions
import app.wave.music.platform.prismaDisplayFont
import app.wave.music.platform.prismaTextFont
import app.wave.music.platform.topInset
import app.wave.music.ui.components.atSize
import app.wave.music.ui.components.backGesture
import app.wave.music.ui.components.blocksTouchesBelow
import app.wave.music.ui.components.predictiveBack
import app.wave.music.ui.components.subtitle
import app.wave.music.ui.menu.LocalItemMenu
import app.wave.music.ui.menu.openFor
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- Recent searches

/** Something you searched for: a phrase, or an artist, album, playlist or song you opened. */
sealed interface Recent {
    data class Query(val text: String) : Recent
    data class Picked(val item: YTItem) : Recent
}

/** The last 12 searches, kept as plain lines of text. */
object PrismaRecents {
    private val key = stringPreferencesKey("prisma_recent")
    private const val ROW = "\u001e"
    private const val FIELD = "\u001f"

    private fun encode(r: Recent): String? = when (r) {
        is Recent.Query -> listOf("q", r.text)
        is Recent.Picked -> when (val i = r.item) {
            is SongItem -> listOf("s", i.id, i.title, i.artists.joinToString { it.name }, i.thumbnail)
            is AlbumItem -> listOf("a", i.browseId, i.title, i.artists?.joinToString { it.name }.orEmpty(), i.thumbnail, i.playlistId)
            is ArtistItem -> listOf("r", i.id, i.title, "", i.thumbnail.orEmpty())
            is PlaylistItem -> listOf("p", i.id, i.title, i.author?.name.orEmpty(), i.thumbnail.orEmpty())
            else -> null
        }
    }?.joinToString(FIELD)

    private fun decode(line: String): Recent? {
        val f = line.split(FIELD)
        fun at(i: Int) = f.getOrNull(i).orEmpty()
        val by = at(3).takeIf { it.isNotBlank() }?.let { listOf(Artist(it, null)) } ?: emptyList()
        return when (f.firstOrNull()) {
            "q" -> Recent.Query(at(1))
            "s" -> Recent.Picked(SongItem(id = at(1), title = at(2), artists = by, thumbnail = at(4)))
            "a" -> Recent.Picked(AlbumItem(browseId = at(1), playlistId = at(5), title = at(2), artists = by, thumbnail = at(4)))
            "r" -> Recent.Picked(ArtistItem(id = at(1), title = at(2), thumbnail = at(4).ifBlank { null }, shuffleEndpoint = null, radioEndpoint = null))
            "p" -> Recent.Picked(PlaylistItem(id = at(1), title = at(2), author = by.firstOrNull(), songCountText = null, thumbnail = at(4).ifBlank { null }, playEndpoint = null, shuffleEndpoint = null, radioEndpoint = null))
            else -> null
        }
    }

    private fun idOf(r: Recent) = when (r) {
        is Recent.Query -> "q:" + r.text.lowercase()
        is Recent.Picked -> "i:" + r.item.id
    }

    fun all(): Flow<List<Recent>> = Stores.prefs("search").data.map { p ->
        p[key]?.split(ROW)?.filter { it.isNotBlank() }?.mapNotNull(::decode).orEmpty()
    }

    suspend fun add(r: Recent) {
        val line = encode(r) ?: return
        Stores.prefs("search").edit { p ->
            val old = p[key]?.split(ROW)?.filter { it.isNotBlank() }.orEmpty()
            val kept = old.filter { o -> decode(o)?.let(::idOf) != idOf(r) }
            p[key] = (listOf(line) + kept).take(12).joinToString(ROW)
        }
    }

    suspend fun remove(r: Recent) {
        Stores.prefs("search").edit { p ->
            p[key] = p[key]?.split(ROW).orEmpty().filter { o -> o.isNotBlank() && decode(o)?.let(::idOf) != idOf(r) }.joinToString(ROW)
        }
    }

    suspend fun clear() {
        Stores.prefs("search").edit { it.remove(key) }
    }
}

// ---------------------------------------------------------------- The page

private sealed interface Found {
    data object Idle : Found
    data object Loading : Found
    data object Failed : Found
    data class Ready(val groups: List<SearchGroup>) : Found
}

/**
 * Prisma's search: the keyboard comes up as it opens (put it away any time), your recent searches
 * under the field, then three ways to finish what you're typing and the songs, albums and
 * playlists it already matches.
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PrismaSearch(
    session: Session,
    onClose: () -> Unit,
    onAvatar: () -> Unit,
    onPlay: (YTItem, Boolean) -> Unit,
    onOpen: (YTItem) -> Unit,
    onRadio: (String) -> Unit = {},
    bottomPadding: Dp,
) {
    val back = predictiveBack(onBack = onClose)
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val c = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf(RenderStart.searchQuery.orEmpty()) }
    var submitted by rememberSaveable { mutableStateOf(RenderStart.searchQuery) }
    var suggestions by remember { mutableStateOf(Suggestions()) }
    var found by remember { mutableStateOf<Found>(Found.Idle) }
    val recents by remember { PrismaRecents.all() }.collectAsState(emptyList())
    val myPlaylists by LibraryRepository.playlists.collectAsState()
    val mine = remember(query, myPlaylists) { if (query.isBlank()) emptyList() else LibraryRepository.search(query) }

    LaunchedEffect(Unit) { if (submitted == null) focus.requestFocus() }
    LaunchedEffect(query, submitted) {
        if (query.isBlank() || query == submitted) {
            suggestions = Suggestions()
            return@LaunchedEffect
        }
        delay(180)
        suggestions = SearchRepository.suggestions(query)
    }
    // Filters, like Wave 1.x: Top, Songs, Artists, Albums, Playlists.
    var filter by rememberSaveable { mutableStateOf(SearchTab.Top) }
    // New results always start from the top (the list would otherwise hold on to the row it showed).
    val resultsList = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(submitted, filter) {
        val q = submitted ?: return@LaunchedEffect
        found = Found.Loading
        found = SearchRepository.search(q, filter).fold({ Found.Ready(it) }, { Found.Failed })
        resultsList.scrollToItem(0)
    }

    fun submit(q: String) {
        val text = q.trim()
        if (text.isEmpty()) return
        query = text
        submitted = text
        keyboard?.hide()
        scope.launch { PrismaRecents.add(Recent.Query(text)) }
    }

    // Songs play; everything else opens. Either way it goes into your recent searches.
    val pick: (YTItem) -> Unit = { item ->
        keyboard?.hide()
        scope.launch { PrismaRecents.add(Recent.Picked(item)) }
        if (item is SongItem) onPlay(item, false) else onOpen(item)
    }
    val hideKeyboardOnScroll = remember(keyboard) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) keyboard?.hide()
                return Offset.Zero
            }
        }
    }
    val showingResults = submitted != null && query == submitted
    val statusTop = topInset()

    val glass = dev.chrisbanes.haze.rememberHazeState()
    val screenWidth = app.wave.music.platform.currentWindowDp().screenWidthDp
    val resultColumns = when { screenWidth >= 1100 -> 3; screenWidth >= 700 -> 2; else -> 1 }
    Box(Modifier.backGesture(back).fillMaxSize().blocksTouchesBelow().background(c.background)) {
        val look by app.wave.music.data.WaveSettings.state.collectAsState()
        val playingNow by app.wave.music.playback.PlayerConnection.state.collectAsState()
        // The cover background is part of what the field's glass blurs, so the glass matches the
        // page under it exactly (no line where they meet).
        Box(Modifier.fillMaxSize().hazeSource(glass)) {
        if (!look.legacyUi && look.matchSongColors) AppBackdrop(playingNow.current?.artworkUrl)
        LazyColumn(
            state = resultsList,
            modifier = Modifier.align(Alignment.TopCenter).widthIn(max = if (resultColumns > 1) 1280.dp else 760.dp).fillMaxSize().nestedScroll(hideKeyboardOnScroll).imePadding(),
            contentPadding = PaddingValues(top = statusTop + 76.dp, bottom = bottomPadding),
        ) {
            when {
                !showingResults && query.isBlank() -> {
                    if (recents.isNotEmpty()) {
                        item {
                            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Recent searches", Modifier.weight(1f), color = c.onBackground, style = TextStyle(fontFamily = prismaDisplayFont, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp))
                                Text(
                                    "Clear",
                                    Modifier.clip(CircleShape).clickable { scope.launch { PrismaRecents.clear() } }.padding(horizontal = 10.dp, vertical = 8.dp),
                                    color = c.primary,
                                    style = TextStyle(fontFamily = prismaTextFont, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                                )
                            }
                        }
                        items(recents, key = { r -> if (r is Recent.Picked) "i" + r.item.id else "q" + (r as Recent.Query).text }) { r ->
                            when (r) {
                                is Recent.Picked -> FoundRow(r.item, onClick = { pick(r.item) }, trailing = {
                                    RowIcon(Icons.Rounded.Close, "Remove") { scope.launch { PrismaRecents.remove(r) } }
                                })
                                is Recent.Query -> TextRow(r.text, Icons.Rounded.History, onClick = { submit(r.text) }) {
                                    RowIcon(Icons.Rounded.Close, "Remove") { scope.launch { PrismaRecents.remove(r) } }
                                }
                            }
                        }
                    } else {
                        item {
                            Text(
                                "Search for songs, artists, albums and playlists.",
                                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                color = c.onBackground.copy(alpha = 0.6f),
                                style = TextStyle(fontFamily = prismaTextFont, fontSize = 15.sp),
                            )
                        }
                    }
                }
                !showingResults -> {
                    // Three ways to finish the phrase, then what it already matches.
                    items(suggestions.queries.take(3), key = { "q-$it" }) { s ->
                        TextRow(s, Icons.Rounded.Search, onClick = { submit(s) }) {
                            RowIcon(Icons.Rounded.NorthWest, "Fill in \"$s\"") {
                                query = "$s "
                                focus.requestFocus()
                            }
                        }
                    }
                    if (suggestions.queries.isNotEmpty() && (suggestions.items.isNotEmpty() || mine.isNotEmpty())) {
                        item { Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().height(1.dp).background(c.onBackground.copy(alpha = 0.08f))) }
                    }
                    items(mine, key = { "mine-" + it.id }) { FoundRow(it, onClick = { pick(it) }) }
                    items(suggestions.items, key = { "m-" + it.id }) { FoundRow(it, onClick = { pick(it) }) }
                }
                else -> {
                val filterRow: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = { item(key = "filters") {
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(SearchTab.entries.size) { i ->
                            val tab = SearchTab.entries[i]
                            FilterChipPill(tab.label, tab == filter) { filter = tab }
                        }
                    }
                } }
                // Searching for radio: a big way in to live radio, on top. It *is* the top result, so
                // no big song card comes in over it.
                val radioQuery = submitted.orEmpty().let { q -> Regex("\\b(radio|fm|am|station|stations|live)\\b", RegexOption.IGNORE_CASE).containsMatchIn(q) }
                val bigTop = (found as? Found.Ready)?.groups?.firstOrNull()?.items?.firstOrNull()?.takeIf { !radioQuery && filter == SearchTab.Top && confidentMatch(submitted.orEmpty(), it) }
                if (radioQuery) item(key = "radio-go") { RadioShortcut(submitted.orEmpty(), big = true) { onRadio(radioSearchText(submitted.orEmpty())) } }
                // Filters on top, or right under the big top result when there is one.
                if (bigTop == null) filterRow()
                when (val f = found) {
                    Found.Loading -> item { Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(64.dp)) } }
                    Found.Failed -> item { Hint("Search didn't go through. Check your connection.") }
                    is Found.Ready -> {
                        if (f.groups.isEmpty()) item { Hint("Nothing found for \"$submitted\"") }
                        // The big top result only when it's clearly what you searched for.
                        val top = bigTop
                        if (top != null) item(key = "top") { TopResult(top, onOpen = { pick(top) }, onPlay = {
                            scope.launch { PrismaRecents.add(Recent.Picked(top)) }
                            onPlay(top, false)
                        }) }
                        if (top != null) filterRow()
                        f.groups.forEachIndexed { gi, group ->
                            val rest = if (gi == 0 && top != null) group.items.drop(1) else group.items
                            if (rest.isEmpty()) return@forEachIndexed
                            item { GroupTitle(group.title) }
                            // Tablets: results in two or three columns, so a wide screen shows more at once.
                            if (resultColumns > 1) {
                                items(rest.chunked(resultColumns), key = { row -> "r-" + group.title + row.first().id }) { row ->
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                                        row.forEach { item -> Box(Modifier.weight(1f)) { FoundRow(item, onClick = { pick(item) }) } }
                                        repeat(resultColumns - row.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            } else items(rest, key = { "r-" + group.title + it.id }) { FoundRow(it, onClick = { pick(it) }) }
                        }
                    }
                    Found.Idle -> Unit
                }
                // Not about radio: a small way to find stations for it, at the end.
                if (!radioQuery && found is Found.Ready) item(key = "radio-go") { RadioShortcut(submitted.orEmpty(), big = false) { onRadio(submitted.orEmpty()) } }
                }
            }
        }

        }

        // The field and your picture, over a short fade into the page.
        Box(
            Modifier
                .fillMaxWidth()
                .height(statusTop + 84.dp)
                // Frosted glass behind the field, fading out at its lower edge.
                .glass(glass, 24.dp, c.background, listOf(dev.chrisbanes.haze.HazeTint(c.background.copy(alpha = 0.55f))), c.background.copy(alpha = 0.92f), progressive = true),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = statusTop + 8.dp, start = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(CircleShape)
                    .background(c.onBackground.copy(alpha = 0.09f))
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.Search, null, tint = c.onBackground.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Songs, artists, albums", color = c.onBackground.copy(alpha = 0.5f), style = TextStyle(fontFamily = prismaTextFont, fontSize = 17.sp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = prismaTextFont, color = c.onBackground, fontSize = 17.sp, fontWeight = FontWeight.Medium),
                        cursorBrush = SolidColor(c.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit(query) }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus)
                            .onFocusChanged { app.wave.music.ui.components.Shortcuts.typing = it.isFocused },
                    )
                }
                if (query.isNotEmpty()) {
                    RowIcon(Icons.Rounded.Close, "Clear search") {
                        query = ""
                        submitted = null
                        found = Found.Idle
                        focus.requestFocus()
                    }
                }
            }
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(c.onBackground.copy(alpha = 0.09f))
                    .border(1.dp, c.onBackground.copy(alpha = 0.14f), CircleShape)
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
}

@Composable
private fun RowIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
    }
}

private fun radioSearchText(q: String) = q.replace(Regex("\\b(radio|stations?|live)\\b", RegexOption.IGNORE_CASE), "").trim().replace(Regex("\\s+"), " ")

/** Takes you to Radio: big when you searched for radio, a small row otherwise. */
@Composable
private fun RadioShortcut(query: String, big: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val rest = radioSearchText(query)
    if (big) {
        Row(
            Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(app.wave.music.platform.PrismaLook.corner(26.dp)))
                .background(c.primaryContainer)
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(c.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Radio, null, tint = c.onPrimary, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(if (rest.isBlank()) "Live radio" else "Radio for \"$rest\"", color = c.onPrimaryContainer, style = TextStyle(fontFamily = prismaDisplayFont, fontSize = 20.sp, fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Real stations, playing right now", color = c.onPrimaryContainer.copy(alpha = 0.75f), style = TextStyle(fontFamily = prismaTextFont, fontSize = 14.sp))
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = c.onPrimaryContainer)
        }
    } else {
        TextRow("Find radio stations for \"$query\"", Icons.Rounded.Radio, onClick = onClick) {
            Icon(Icons.Rounded.ChevronRight, null, tint = c.onBackground.copy(alpha = 0.5f), modifier = Modifier.padding(end = 10.dp))
        }
    }
}

@Composable
private fun TextRow(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 6.dp).height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = c.onBackground.copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
        Text(text, Modifier.weight(1f), color = c.onBackground, style = TextStyle(fontFamily = prismaTextFont, fontSize = 16.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}

/** A song, album, playlist or artist: cover (round for artists), name, and what it is. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FoundRow(item: YTItem, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    val c = MaterialTheme.colorScheme
    val menu = LocalItemMenu.current
    val line = when (item) {
        is SongItem -> listOf("Song", subtitle(item)).filterNot { it.isNullOrBlank() }.joinToString(" · ")
        else -> subtitle(item).orEmpty()
    }
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { menu.openFor(item) })
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AsyncImage(
            item.thumbnail?.atSize(226),
            null,
            Modifier.size(52.dp).clip(if (item is ArtistItem) CircleShape else RoundedCornerShape(app.wave.music.platform.PrismaLook.corner(12.dp))).background(c.surfaceContainerHigh),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f)) {
            Text(item.title, color = c.onBackground, style = TextStyle(fontFamily = prismaTextFont, fontSize = 16.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (line.isNotBlank()) Text(line, color = c.onBackground.copy(alpha = 0.6f), style = TextStyle(fontFamily = prismaTextFont, fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.invoke()
    }
}

/** The best match, in the middle: its cover (round for artists), name, what it is, and a play button in its colors. */
@Composable
private fun TopResult(item: YTItem, onOpen: () -> Unit, onPlay: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val tone = app.wave.music.ui.components.rememberArtColorScheme(item.thumbnail?.atSize(226))
    val round = item is ArtistItem
    // The top result's art spreads across the whole top of search (under the field), lightly
    // blurred, and fades out to whatever's behind search, so there's no edge at the bottom.
    val wide = true
    val reach = with(androidx.compose.ui.platform.LocalDensity.current) { (app.wave.music.platform.topInset() + 76.dp).roundToPx() }
    val darkPage = c.background.luminance() < 0.5f
    Box(
        Modifier
            .fillMaxWidth()
            // Reach up under the search field to the very top of the screen.
            .layout { m, k ->
                val p = m.measure(k.copy(maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                layout(p.width, (p.height - reach).coerceAtLeast(0)) { p.place(0, -reach) }
            },
    ) {
    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // Fade the whole art layer out toward the bottom.
                drawRect(Brush.verticalGradient(0f to Color.Black, 0.55f to Color.Black, 1f to Color.Transparent), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
            },
    ) {
        app.wave.music.ui.components.BlurredImage(
            item.thumbnail?.atSize(app.wave.music.ui.components.ArtSize.CARD),
            Modifier.matchParentSize().graphicsLayer { scaleX = 1.15f; scaleY = 1.15f },
            radius = 14.dp,
        )
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.45f), 0.35f to Color.Black.copy(alpha = 0.12f), 1f to c.background.copy(alpha = 0.7f))))
    }
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(top = if (wide) with(androidx.compose.ui.platform.LocalDensity.current) { reach.toDp() } + 16.dp else 20.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            item.thumbnail?.atSize(544),
            null,
            Modifier.size(168.dp).shadow(20.dp, if (round) CircleShape else RoundedCornerShape(app.wave.music.platform.PrismaLook.corner(24.dp))).clip(if (round) CircleShape else RoundedCornerShape(app.wave.music.platform.PrismaLook.corner(24.dp))).background(c.surfaceContainerHigh),
            contentScale = ContentScale.Crop,
        )
        Text(item.title, Modifier.padding(top = 14.dp, start = 24.dp, end = 24.dp), color = if (darkPage) Color.White else c.onBackground, style = TextStyle(fontFamily = prismaDisplayFont, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center), maxLines = 2, overflow = TextOverflow.Ellipsis)
        val kind = when (item) { is SongItem -> "Song"; is AlbumItem -> "Album"; is ArtistItem -> "Artist"; is PlaylistItem -> "Playlist"; else -> null }
        val line = listOfNotNull(kind, (if (item is SongItem) subtitle(item) else null)).joinToString(" · ")
        if (line.isNotBlank()) Text(line, color = (if (darkPage) Color.White else c.onBackground).copy(alpha = 0.75f), style = TextStyle(fontFamily = prismaTextFont, fontSize = 14.sp))
        Row(
            Modifier
                .padding(top = 14.dp)
                .height(48.dp)
                .clip(CircleShape)
                .background(tone.primary)
                .clickable(onClick = onPlay)
                .padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(androidx.compose.material.icons.Icons.Rounded.PlayArrow, null, tint = tone.onPrimary)
            Text(if (item is ArtistItem) "Shuffle" else "Play", color = tone.onPrimary, style = TextStyle(fontFamily = prismaDisplayFont, fontSize = 16.sp, fontWeight = FontWeight.Bold))
        }
    }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text,
        Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 4.dp),
        color = MaterialTheme.colorScheme.onBackground,
        style = TextStyle(fontFamily = prismaDisplayFont, fontSize = 20.sp, fontWeight = FontWeight.Bold),
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), style = TextStyle(fontFamily = prismaTextFont, fontSize = 15.sp))
}


/** A search filter: filled with a check when it's on. */
@Composable
private fun FilterChipPill(label: String, on: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val fill by androidx.compose.animation.animateColorAsState(if (on) c.primary else c.onBackground.copy(alpha = 0.08f), androidx.compose.animation.core.tween(180), label = "chipFill")
    val ink by androidx.compose.animation.animateColorAsState(if (on) c.onPrimary else c.onBackground, androidx.compose.animation.core.tween(180), label = "chipInk")
    Row(
        Modifier.height(38.dp).clip(CircleShape).background(fill).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.animation.AnimatedVisibility(on) {
            Icon(androidx.compose.material.icons.Icons.Rounded.Check, null, Modifier.padding(end = 6.dp).size(18.dp), tint = ink)
        }
        Text(label, color = ink, style = TextStyle(fontFamily = prismaTextFont, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
    }
}

/**
 * Whether [item] is clearly what [query] was looking for: its name (or name and artist) matches the
 * search, or nearly all the searched words are in it. Otherwise no big top result.
 */
private fun confidentMatch(query: String, item: YTItem): Boolean {
    fun norm(t: String) = t.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()
    val q = norm(query)
    if (q.length < 2) return false
    val title = norm(item.title.replace(Regex("\\s*[(\\[].*?[)\\]]"), ""))
    val by = norm(when (item) {
        is SongItem -> item.artists.joinToString(" ") { it.name }
        is AlbumItem -> item.artists?.joinToString(" ") { it.name }.orEmpty()
        else -> ""
    })
    if (title == q || (title.length >= 3 && (q.startsWith(title) || title.startsWith(q)))) return true
    val words = q.split(" ").filter { it.length > 1 }
    if (words.isEmpty()) return false
    val hay = "$title $by"
    val hits = words.count { w -> hay.split(" ").any { it == w || (w.length >= 4 && it.startsWith(w)) } }
    return hits * 10 >= words.size * 8 && title.split(" ").any { it in words }
}
