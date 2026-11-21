package app.wave.music.ui.radio

import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wave.music.data.RadioRepository
import app.wave.music.data.Station
import app.wave.music.playback.PlayerConnection
import app.wave.music.playback.QueueBuilder
import app.wave.music.platform.topBarPadding
import app.wave.music.ui.components.LocalMiniPlayerSpace
import app.wave.music.ui.components.blocksTouchesBelow
import app.wave.music.ui.components.pressable
import app.wave.music.ui.detail.PageActions
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

/** Plays a station live. */
fun playStation(station: Station) = PlayerConnection.play(QueueBuilder.forStation(station))

/**
 * Live radio: search stations by name or genre, or browse what's popular near you and around
 * the world. Tapping a station plays it live.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RadioScreen(initialQuery: String, actions: PageActions) {
    val c = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var near by remember { mutableStateOf<List<Station>?>(null) }
    var world by remember { mutableStateOf<List<Station>?>(null) }
    var found by remember { mutableStateOf<List<Station>?>(null) }
    LaunchedEffect(Unit) {
        val country = java.util.Locale.getDefault().country
        near = if (country.isNotBlank()) RadioRepository.popular(country, 20) else emptyList()
        world = RadioRepository.popular(limit = 30)
    }
    LaunchedEffect(query) {
        if (query.isBlank()) { found = null; return@LaunchedEffect }
        delay(350)
        found = null
        found = RadioRepository.find(query)
    }

    Box(Modifier.fillMaxSize().blocksTouchesBelow().background(c.background), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 840.dp).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp + LocalMiniPlayerSpace.current),
        ) {
            item {
                Column(Modifier.topBarPadding().padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier.size(48.dp).pressable(CircleShape, c.onBackground.copy(alpha = 0.08f), pressedScale = 0.88f, onClick = actions.back),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = c.onBackground) }
                    }
                    Row(Modifier.padding(top = 14.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Radio", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, color = c.onBackground)
                        LivePill()
                    }
                    Text("Real stations, live from around the world.", Modifier.padding(start = 4.dp, top = 2.dp), color = c.onSurfaceVariant, fontSize = 15.sp)
                    // Search stations by name or genre.
                    Row(
                        Modifier
                            .padding(top = 16.dp, bottom = 8.dp)
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(CircleShape)
                            .background(c.onBackground.copy(alpha = 0.08f))
                            .padding(start = 16.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Rounded.Search, null, tint = c.onSurfaceVariant)
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) Text("Stations, genres, cities", color = c.onSurfaceVariant, fontSize = 16.sp)
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = TextStyle(color = c.onBackground, fontSize = 16.sp),
                                cursorBrush = SolidColor(c.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {}),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (query.isNotEmpty()) Box(Modifier.size(42.dp).clip(CircleShape).clickable { query = "" }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Close, "Clear", tint = c.onSurfaceVariant)
                        }
                    }
                }
            }
            if (query.isNotBlank()) {
                val list = found
                when {
                    list == null -> item { Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(64.dp)) } }
                    list.isEmpty() -> item { Text("No stations found for \"$query\".", Modifier.padding(20.dp), color = c.onSurfaceVariant, fontSize = 15.sp) }
                    else -> items(list, key = { "f" + it.uuid }) { StationRow(it) { playStation(it) } }
                }
            } else {
                val nearList = near
                val worldList = world
                if (nearList == null && worldList == null) item { Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(64.dp)) } }
                if (!nearList.isNullOrEmpty()) {
                    item { Heading("Near you", "Popular where you are") }
                    items(nearList, key = { "n" + it.uuid }) { StationRow(it) { playStation(it) } }
                }
                if (!worldList.isNullOrEmpty()) {
                    item { Heading("Around the world", "Most listened to") }
                    items(worldList.filter { s -> nearList?.none { it.uuid == s.uuid } != false }, key = { "w" + it.uuid }) { StationRow(it) { playStation(it) } }
                }
                if (nearList?.isEmpty() == true && worldList?.isEmpty() == true) item {
                    Text("Couldn't reach the radio directory. Check your connection and try again.", Modifier.padding(20.dp), color = c.onSurfaceVariant, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun Heading(label: String, title: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
    }
}

/** A small red LIVE tag. */
@Composable
fun LivePill(modifier: Modifier = Modifier) {
    Row(
        modifier.clip(CircleShape).background(MaterialTheme.colorScheme.error).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onError))
        Text("LIVE", color = MaterialTheme.colorScheme.onError, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

/** A station's logo, or a radio icon on the song color when it has none. */
@Composable
fun StationArt(station: Station, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Box(modifier.background(Brush.linearGradient(listOf(c.primaryContainer, c.tertiaryContainer))), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Radio, null, tint = c.onPrimaryContainer, modifier = Modifier.size(28.dp))
        if (station.favicon != null) AsyncImage(station.favicon, station.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun StationRow(station: Station, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StationArt(station, Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)))
        Column(Modifier.weight(1f)) {
            Text(station.name, color = c.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (station.about.isNotBlank()) Text(station.about, color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (station.bitrate > 0) Text("${station.bitrate} kbps", color = c.onSurfaceVariant, fontSize = 12.sp)
    }
}

/** A station as a card, for the Radio row on home. */
@Composable
fun StationCard(station: Station, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.width(132.dp).clickable(onClick = onClick)) {
        StationArt(station, Modifier.size(132.dp).clip(RoundedCornerShape(24.dp)))
        Text(station.name, Modifier.padding(top = 8.dp), color = c.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(station.country.ifBlank { "Live radio" }, color = c.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}


/**
 * Live radio's one button: stop, or start again. Starting again reconnects, so you hear the
 * station as it is now (live radio can't be paused and resumed).
 */
fun toggleLive(state: app.wave.music.playback.PlayerUiState) {
    val now = state.current ?: return
    if (state.isPlaying) PlayerConnection.togglePlay()
    else PlayerConnection.play(
        app.wave.music.playback.PlayRequest(
            listOf(now), 0,
            app.wave.music.playback.PlaybackContext("radio-${now.videoId}", now.title, now.artworkUrl, null, isCollection = false),
            false,
        ),
    )
}

/** Stop and play, for live radio. */
@Composable
fun StopPlayIcon(playing: Boolean, tint: androidx.compose.ui.graphics.Color, size: androidx.compose.ui.unit.Dp = 30.dp) {
    androidx.compose.animation.Crossfade(playing, label = "stopPlay") { on ->
        Icon(if (on) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, if (on) "Stop" else "Listen live", tint = tint, modifier = Modifier.size(size))
    }
}


/**
 * Live radio's round stop / play button. It answers the moment you tap: stopping is instant, and
 * starting shows a spinner straight away until the station is back on.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LiveButton(state: app.wave.music.playback.PlayerUiState, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    var starting by remember { mutableStateOf(false) }
    LaunchedEffect(state.isPlaying, state.current?.videoId) { if (state.isPlaying) starting = false }
    LaunchedEffect(starting) { if (starting) { delay(12_000); starting = false } }
    val busy = starting || (state.isBuffering && !state.isPlaying)
    Box(
        modifier.size(size).pressable(CircleShape, c.primary, pressedScale = 0.88f) {
            if (!state.isPlaying) starting = true
            toggleLive(state)
        },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.animation.Crossfade(busy, label = "liveBusy") { b ->
            if (b) LoadingIndicator(Modifier.size(size * 0.62f), color = c.onPrimary)
            else StopPlayIcon(state.isPlaying, c.onPrimary, size * 0.5f)
        }
    }
}
