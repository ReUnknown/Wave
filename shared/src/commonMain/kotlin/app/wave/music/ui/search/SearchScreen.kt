package app.wave.music.ui.search

import androidx.compose.ui.graphics.luminance
import app.wave.music.ui.components.WheelRow
import app.wave.music.platform.currentWindowDp
import androidx.compose.foundation.layout.widthIn
import app.wave.music.ui.components.ArtSize
import app.wave.music.ui.components.atSize
import androidx.compose.ui.backhandler.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.North
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wave.music.data.LibraryRepository
import app.wave.music.data.SearchGroup
import androidx.compose.ui.draw.clipToBounds
import app.wave.music.data.SearchRepository
import app.wave.music.data.SearchTab
import app.wave.music.data.Suggestions
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import app.wave.music.ui.components.ItemCard
import app.wave.music.ui.components.ProgressiveBackdropBlur
import app.wave.music.ui.components.ProgressiveBlurImage
import app.wave.music.ui.components.BlurredImage
import app.wave.music.ui.components.blocksTouchesBelow
import app.wave.music.ui.components.WaveMotion
import app.wave.music.ui.components.backGesture
import app.wave.music.ui.components.predictiveBack
import app.wave.music.ui.components.pressable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import app.wave.music.ui.components.backdropSource
import app.wave.music.ui.components.rememberBackdrop
import androidx.compose.ui.graphics.rememberGraphicsLayer
import app.wave.music.ui.components.ItemRow
import app.wave.music.ui.components.rememberArtColorScheme
import app.wave.music.ui.components.subtitle
import coil3.compose.AsyncImage
import androidx.compose.ui.draw.shadow
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface Results {
    data object Idle : Results
    data object Loading : Results
    data object Failed : Results
    data class Ready(val groups: List<SearchGroup>) : Results
}

/**
 * Search (design board 05): type with live suggestions and recent searches, then results.
 * The top result's art fills the backdrop and the page takes its colors.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(
    onClose: () -> Unit,
    onPlay: (YTItem, shuffle: Boolean) -> Unit,
    onPlayNext: (YTItem) -> Unit,
    /** Opens an artist, album or playlist page. */
    onOpen: (YTItem) -> Unit,
    /** Art to glow behind the empty search page (what's playing, or home's top pick). */
    ambientArt: String? = null,
    /** The shared container the search field morphs from (home's search bar). */
    fieldModifier: Modifier = Modifier,
) {
    val back = predictiveBack(onBack = onClose)
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf<String?>(null) }
    var tab by rememberSaveable { mutableStateOf(SearchTab.Top) }
    var suggestions by remember { mutableStateOf(Suggestions()) }
    var results by remember { mutableStateOf<Results>(Results.Idle) }
    val recent by remember { SearchRepository.recent() }.collectAsState(emptyList())
    val myPlaylists by LibraryRepository.playlists.collectAsState()
    val matchColors = app.wave.music.data.WaveSettings.state.collectAsState().value.matchSongColors
    val libraryMatches = remember(query, myPlaylists) { LibraryRepository.search(query) }

    LaunchedEffect(Unit) { if (submitted == null) focus.requestFocus() }
    LaunchedEffect(query, submitted) {
        if (query.isBlank() || query == submitted) {
            suggestions = Suggestions()
            return@LaunchedEffect
        }
        delay(200)
        suggestions = SearchRepository.suggestions(query)
    }
    LaunchedEffect(submitted, tab) {
        val q = submitted ?: return@LaunchedEffect
        results = Results.Loading
        results = SearchRepository.search(q, tab).fold({ Results.Ready(it) }, { Results.Failed })
    }

    fun submit(q: String) {
        val text = q.trim()
        if (text.isEmpty()) return
        query = text
        submitted = text
        keyboard?.hide()
        scope.launch { SearchRepository.remember(text) }
    }

    /** Opens (or plays) something picked straight from the suggestions, remembering the search. */
    val pick: (YTItem) -> Unit = { item ->
        keyboard?.hide()
        val text = query.trim()
        if (text.isNotEmpty()) scope.launch { SearchRepository.remember(text) }
        onOpen(item)
    }
    // Scrolling the list puts the keyboard away so you can see everything.
    val hideKeyboardOnScroll = remember(keyboard) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) keyboard?.hide()
                return Offset.Zero
            }
        }
    }

    val ready = results as? Results.Ready
    // Only a result that really matches what you typed gets the big top spot (typing "e" shouldn't
    // crown whatever YouTube happens to list first).
    val topResult = ready?.groups?.firstOrNull()?.items?.firstOrNull()?.takeIf { isStrongMatch(submitted.orEmpty(), it) }
    val showingResults = submitted != null && query == submitted

    MaterialTheme(colorScheme = rememberArtColorScheme(if (showingResults && matchColors) topResult?.thumbnail?.atSize(600) else null)) {
        val c = MaterialTheme.colorScheme
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val backdrop = rememberBackdrop()
        Box(
            Modifier
                .backGesture(back)
                .fillMaxSize()
                .blocksTouchesBelow()
                .background(c.background),
        ) {
            // Everything under the header records into the backdrop the header blurs.
            Box(Modifier.fillMaxSize().backdropSource(backdrop)) {
            if (!showingResults && ambientArt != null) {
                BlurredImage(
                    ambientArt.atSize(ArtSize.BLUR),
                    Modifier
                        .fillMaxWidth()
                        .height(520.dp)
                        .clipToBounds()
                        .graphicsLayer { scaleX = 1.3f; scaleY = 1.3f },
                    radius = 90.dp,
                    fadeBottom = 0.4f,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(520.dp)
                        .background(Brush.verticalGradient(0f to c.background.copy(alpha = 0.35f), 0.6f to c.background.copy(alpha = 0.75f), 1f to c.background)),
                )
            }
            if (showingResults && topResult != null && tab == SearchTab.Top) {
                // Backdrop: the top result's art, progressively blurred into the page.
                val backdropHeight = 440.dp
                // Sharp at the top so the artist or cover is recognizable, blurring toward the results.
                ProgressiveBlurImage(
                    topResult.thumbnail?.atSize(1080),
                    Modifier
                        .fillMaxWidth()
                        .height(backdropHeight),
                    start = if (currentWindowDp().screenWidthDp > currentWindowDp().screenHeightDp) 0.6f else 0.35f,
                    end = 0.95f,
                    maxRadius = 56.dp,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(backdropHeight)
                        .background(Brush.verticalGradient(0f to (if (c.background.luminance() < 0.5f) Color.Black else Color.White).copy(alpha = 0.25f), 0.35f to Color.Transparent, 0.7f to c.background.copy(alpha = 0.55f), 1f to c.background)),
                )
            }

            LazyColumn(
                Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 900.dp)
                    .fillMaxSize()
                    .nestedScroll(hideKeyboardOnScroll)
                    .imePadding(),
                contentPadding = PaddingValues(top = statusTop + if (showingResults) 128.dp else 84.dp, bottom = 140.dp),
            ) {
                when {
                    !showingResults && query.isBlank() -> {
                        if (recent.isNotEmpty()) item { SmallLabel("Recent") }
                        items(recent) { q ->
                            SuggestionRow(q, Icons.Rounded.History, onClick = { submit(q) }, trailingIcon = Icons.Rounded.Close) {
                                scope.launch { SearchRepository.forget(q) }
                            }
                        }
                    }
                    !showingResults -> {
                        // Searches to finish the query, then the artists, songs and albums it
                        // already matches (tap to open or play), then your own playlists.
                        // Rows glide into place as the suggestions change under your typing.
                        items(suggestions.queries.take(4), key = { "q-$it" }) { s ->
                            Box(Modifier.animateItem()) {
                                SuggestionRow(s, Icons.Rounded.Search, onClick = { submit(s) }, trailingIcon = Icons.Rounded.North) { query = s }
                            }
                        }
                        if (suggestions.items.isNotEmpty()) {
                            item(key = "matches") { Box(Modifier.animateItem()) { SmallLabel("Matches", top = 12.dp) } }
                            items(suggestions.items, key = { "match-" + it.id }) { item ->
                                ItemRow(item, onClick = pick, trailing = { MatchHint(item) }, modifier = Modifier.animateItem())
                            }
                        }
                        if (libraryMatches.isNotEmpty()) {
                            item { SmallLabel("In your library", top = 12.dp) }
                            items(libraryMatches) { ItemRow(it, onClick = pick, trailing = { MatchHint(it) }) }
                        }
                    }
                    else -> when (val r = results) {
                        Results.Loading -> item {
                            Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(72.dp)) }
                        }
                        Results.Failed -> item { Label("Search didn't go through. Check your connection.") }
                        is Results.Ready -> {
                            val mine = if (tab == SearchTab.Top || tab == SearchTab.Playlists) LibraryRepository.search(submitted.orEmpty()) else emptyList()
                            if (r.groups.isEmpty() && mine.isEmpty()) item { Label("No music found for \"$submitted\"") }
                            // Your own matching playlists go right under the top result.
                            val showMine: LazyListScope.() -> Unit = {
                                if (mine.isNotEmpty()) {
                                    item { Label("Your playlists") }
                                    items(mine) { ItemRow(it, onClick = onOpen) }
                                }
                            }
                            if (r.groups.isEmpty() || tab != SearchTab.Top) showMine()
                            r.groups.forEachIndexed { index, group ->
                                if (index == 0 && tab == SearchTab.Top && topResult != null && group.items.firstOrNull() == topResult) {
                                    item { TopResult(group.items.first(), onPlay, onPlayNext, onOpen) }
                                    showMine()
                                    val rest = group.items.drop(1)
                                    if (rest.isNotEmpty()) {
                                        items(rest) { ItemRow(it, onClick = onOpen) { PlayNextButton { onPlayNext(it) } } }
                                    }
                                } else if (tab != SearchTab.Top || group.items.all { it is SongItem }) {
                                    item { Label(group.title) }
                                    items(group.items) { ItemRow(it, onClick = onOpen) { PlayNextButton { onPlayNext(it) } } }
                                } else {
                                    item { Label(group.title) }
                                    item {
                                        WheelRow {
                                            items(group.items) { ItemCard(it, onClick = onOpen) }
                                        }
                                    }
                                }
                            }
                        }
                        Results.Idle -> Unit
                    }
                }
            }
            }

            // Frosted header: real progressive blur behind the search field and filter chips.
            ProgressiveBackdropBlur(
                backdrop = backdrop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusTop + if (showingResults) 132.dp else 80.dp),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(statusTop + if (showingResults) 132.dp else 80.dp)
                    .background(Brush.verticalGradient(0f to c.background.copy(alpha = 0.7f), 0.7f to c.background.copy(alpha = 0.35f), 1f to Color.Transparent)),
            )
            Column(Modifier.align(Alignment.TopCenter).widthIn(max = 900.dp).statusBarsPadding()) {
                Row(
                    Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .then(fieldModifier)
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(CircleShape)
                        .background(c.onBackground.copy(alpha = 0.1f))
                        .border(1.dp, c.onBackground.copy(alpha = 0.14f), CircleShape)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = c.onBackground) }
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Search songs, artists, albums", color = c.onSurfaceVariant, fontSize = 17.sp)
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = c.onBackground, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                            cursorBrush = SolidColor(c.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { submit(query) }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focus)
                                // Space types here instead of pausing the music.
                                .onFocusChanged { app.wave.music.ui.components.Shortcuts.typing = it.isFocused },
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            query = ""
                            submitted = null
                            results = Results.Idle
                            focus.requestFocus()
                        }) { Icon(Icons.Rounded.Close, "Clear search", tint = c.onBackground) }
                    }
                }
                if (showingResults) {
                    WheelRow(contentPadding = PaddingValues(horizontal = 12.dp), spacing = 8.dp) {
                        items(SearchTab.entries) { t ->
                            val selected = t == tab
                            // The chosen filter fills in and rounds out; the rest relax back.
                            val fill by animateColorAsState(if (selected) c.primary else c.onBackground.copy(alpha = 0.1f), tween(160), label = "chip")
                            val ink by animateColorAsState(if (selected) c.onPrimary else c.onBackground, tween(160), label = "chipInk")
                            val corner by animateDpAsState(if (selected) 20.dp else 12.dp, WaveMotion.bouncy(), label = "chipShape")
                            Box(
                                Modifier
                                    .height(40.dp)
                                    .pressable(RoundedCornerShape(corner), fill, pressedScale = 0.92f) { tab = t }
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(t.label, color = ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopResult(item: YTItem, onPlay: (YTItem, Boolean) -> Unit, onPlayNext: (YTItem) -> Unit, onOpen: (YTItem) -> Unit) {
    val c = MaterialTheme.colorScheme
    val isSong = item is SongItem
    // The whole top result (including the art above it) is one big target: songs play,
    // artists, albums and playlists open their page.
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = null, indication = null) { if (isSong) onPlay(item, false) else onOpen(item) }
            .padding(start = 20.dp, end = 20.dp, top = 56.dp, bottom = 16.dp),
    ) {
        // A sharp cover (a circle for artists) beside the name, so you can always tell who or what
        // it is; the blurred art behind it is the backdrop.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val shape = if (item is ArtistItem) CircleShape else RoundedCornerShape(24.dp)
            AsyncImage(
                item.thumbnail?.atSize(ArtSize.CARD),
                item.title,
                Modifier
                    .size(112.dp)
                    .shadow(20.dp, shape)
                    .clip(shape)
                    .background(c.surfaceContainerHigh),
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.weight(1f)) {
                Text("TOP RESULT", style = MaterialTheme.typography.labelSmall, color = c.primary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.title,
                        Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = c.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!isSong) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(32.dp), tint = c.onBackground)
                }
                subtitle(item)?.let { Text(it, color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        Spacer(Modifier.height(16.dp))
        val (primary, secondary) = when (item) {
            is SongItem -> Triple("Play", Icons.Rounded.PlayArrow) { onPlay(item, false) } to
                Triple("Play next", Icons.AutoMirrored.Rounded.PlaylistAdd) { onPlayNext(item) }
            is ArtistItem -> Triple("View artist", Icons.Rounded.Person) { onOpen(item) } to
                Triple("Shuffle", Icons.Rounded.Shuffle) { onPlay(item, true) }
            else -> Triple("Play", Icons.Rounded.PlayArrow) { onPlay(item, false) } to
                Triple("Shuffle", Icons.Rounded.Shuffle) { onPlay(item, true) }
        }
        Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .pressable(RoundedCornerShape(28.dp, 10.dp, 10.dp, 28.dp), c.primary, pressedScale = 0.95f, onClick = primary.third),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(primary.second, null, tint = c.onPrimary)
                Text("  ${primary.first}", color = c.onPrimary, fontWeight = FontWeight.Bold)
            }
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .pressable(RoundedCornerShape(10.dp, 28.dp, 28.dp, 10.dp), c.onBackground.copy(alpha = 0.12f), pressedScale = 0.95f, onClick = secondary.third),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(secondary.second, null, tint = c.onBackground)
                Text("  ${secondary.first}", color = c.onBackground, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** A chevron on things that open a page, so it's clear tapping goes somewhere. */
@Composable
private fun MatchHint(item: YTItem) {
    if (item !is SongItem) {
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.padding(end = 6.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlayNextButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Play next", tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SmallLabel(text: String, top: androidx.compose.ui.unit.Dp = 4.dp) {
    Text(
        text.uppercase(),
        Modifier.padding(start = 20.dp, end = 20.dp, top = top, bottom = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun SuggestionRow(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    trailingIcon: ImageVector,
    onTrailing: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, Modifier.weight(1f).padding(start = 16.dp), color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        IconButton(onClick = onTrailing) { Icon(trailingIcon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
    }
}

/**
 * Whether [item] is what you were looking for: every word you typed starts a word of its name
 * (or, for songs and albums, its artist), and you typed enough to mean something.
 */
private fun isStrongMatch(query: String, item: YTItem): Boolean {
    fun words(text: String) = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
    val typed = words(query)
    if (typed.joinToString("").length < 3) return false
    val name = words(item.title + " " + (subtitle(item) ?: ""))
    return typed.all { t -> name.any { it.startsWith(t) } }
}
