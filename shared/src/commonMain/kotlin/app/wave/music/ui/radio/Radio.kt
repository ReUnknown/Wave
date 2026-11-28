package app.wave.music.ui.radio

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wave.music.data.RadioRepository
import app.wave.music.data.Station
import app.wave.music.platform.PrismaLook
import app.wave.music.platform.prismaDisplayFont
import app.wave.music.platform.prismaTextFont
import app.wave.music.platform.topBarPadding
import app.wave.music.playback.PlayRequest
import app.wave.music.playback.PlaybackContext
import app.wave.music.playback.PlayerConnection
import app.wave.music.playback.PlayerUiState
import app.wave.music.playback.QueueBuilder
import app.wave.music.ui.components.LocalMiniPlayerSpace
import app.wave.music.ui.components.blocksTouchesBelow
import app.wave.music.ui.components.pressable
import app.wave.music.ui.detail.PageActions
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

private fun display(size: TextUnit, weight: FontWeight = FontWeight.Bold) = TextStyle(fontFamily = prismaDisplayFont, fontSize = size, fontWeight = weight)
private fun body(size: TextUnit, weight: FontWeight = FontWeight.Normal) = TextStyle(fontFamily = prismaTextFont, fontSize = size, fontWeight = weight)

/** Plays a station live. */
fun playStation(station: Station) = PlayerConnection.play(QueueBuilder.forStation(station))


/**
 * Live radio's one button: stop, or start again. Starting again reconnects, so you hear the
 * station as it is now (live radio can't be paused and picked up later).
 */
fun toggleLive(state: PlayerUiState) {
    val now = state.current ?: return
    if (state.isPlaying) PlayerConnection.togglePlay()
    else PlayerConnection.play(PlayRequest(listOf(now), 0, PlaybackContext("radio-${now.videoId}", now.title, now.artworkUrl, null, isCollection = false), false))
}

/** A small red LIVE tag. */
@Composable
fun LivePill(modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier.clip(CircleShape).background(c.error).padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(c.onError))
        Text("LIVE", color = c.onError, style = body(10.sp, FontWeight.Bold).copy(letterSpacing = 1.sp))
    }
}

/** Stop or play, for live radio. */
@Composable
fun StopPlayIcon(playing: Boolean, tint: Color, size: Dp = 30.dp) {
    Crossfade(playing, label = "stopPlay") { on ->
        Icon(if (on) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, if (on) "Stop" else "Listen live", tint = tint, modifier = Modifier.size(size))
    }
}

/**
 * Live radio's round stop / play button. It answers the moment you tap: stopping is instant, and
 * starting shows a spinner straight away until the station is back on.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LiveButton(state: PlayerUiState, size: Dp, fill: Color = MaterialTheme.colorScheme.primary, ink: Color = MaterialTheme.colorScheme.onPrimary, modifier: Modifier = Modifier) {
    var starting by remember { mutableStateOf(false) }
    LaunchedEffect(state.isPlaying, state.current?.videoId) { if (state.isPlaying) starting = false }
    LaunchedEffect(starting) { if (starting) { delay(12_000); starting = false } }
    val busy = starting || (state.isBuffering && !state.isPlaying)
    Box(
        modifier.size(size).pressable(CircleShape, fill, pressedScale = 0.88f) {
            if (!state.isPlaying) starting = true
            toggleLive(state)
        },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(busy, label = "liveBusy") { b ->
            if (b) LoadingIndicator(Modifier.size(size * 0.62f), color = ink)
            else StopPlayIcon(state.isPlaying, ink, size * 0.5f)
        }
    }
}

/** A station's logo, or a radio icon on a soft gradient when it has none. */
@Composable
fun StationArt(station: Station, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Box(modifier.background(Brush.linearGradient(listOf(c.primaryContainer, c.tertiaryContainer))), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Radio, null, tint = c.onPrimaryContainer, modifier = Modifier.size(30.dp))
        if (station.favicon != null) AsyncImage(station.favicon, station.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/** A station as a card, for Radio for you on home. */
@Composable
fun StationCard(station: Station, size: Dp = 128.dp, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.width(size).clickable(onClick = onClick)) {
        Box {
            StationArt(station, Modifier.size(size).clip(RoundedCornerShape(PrismaLook.corner(18.dp))))
            LivePill(Modifier.align(Alignment.TopStart).padding(8.dp))
        }
        Text(station.name, Modifier.padding(top = 6.dp), color = c.onBackground, style = body(14.sp, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(station.country.ifBlank { "Live radio" }, color = c.onBackground.copy(alpha = 0.6f), style = body(12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The last card in Radio for you: every station, and searching them. */
@Composable
fun RadioMoreCard(size: Dp = 128.dp, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.width(size).clickable(onClick = onClick)) {
        Box(Modifier.size(size).clip(RoundedCornerShape(PrismaLook.corner(18.dp))).background(c.onBackground.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Radio, null, tint = c.primary, modifier = Modifier.size(34.dp))
                Text("All stations", Modifier.padding(top = 6.dp), color = c.onBackground, style = body(13.sp, FontWeight.SemiBold))
            }
        }
        Text("Radio", Modifier.padding(top = 6.dp), color = c.onBackground, style = body(14.sp, FontWeight.Medium), maxLines = 1)
        Text("Search by name or genre", color = c.onBackground.copy(alpha = 0.6f), style = body(12.sp), maxLines = 1)
    }
}

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
        LazyColumn(Modifier.widthIn(max = 840.dp).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp + LocalMiniPlayerSpace.current)) {
            item {
                Column(Modifier.topBarPadding().padding(horizontal = 16.dp)) {
                    Box(
                        Modifier.size(48.dp).pressable(CircleShape, c.onBackground.copy(alpha = 0.08f), pressedScale = 0.88f, onClick = actions.back),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = c.onBackground) }
                    Row(Modifier.padding(top = 14.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Radio", color = c.onBackground, style = display(34.sp).copy(letterSpacing = (-0.6).sp))
                        LivePill()
                    }
                    Text("Real stations, live from around the world.", Modifier.padding(start = 4.dp, top = 2.dp), color = c.onBackground.copy(alpha = 0.65f), style = body(15.sp))
                    Row(
                        Modifier.padding(top = 16.dp, bottom = 8.dp).fillMaxWidth().height(52.dp).clip(CircleShape).background(c.onBackground.copy(alpha = 0.09f)).padding(start = 16.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Rounded.Search, null, tint = c.onBackground.copy(alpha = 0.7f))
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) Text("Stations, genres, cities", color = c.onBackground.copy(alpha = 0.55f), style = body(16.sp))
                            BasicTextField(
                                value = query, onValueChange = { query = it }, singleLine = true,
                                textStyle = body(16.sp).copy(color = c.onBackground), cursorBrush = SolidColor(c.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (query.isNotEmpty()) Box(Modifier.size(40.dp).clip(CircleShape).clickable { query = "" }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Close, "Clear", tint = c.onBackground.copy(alpha = 0.7f))
                        }
                    }
                }
            }
            if (query.isNotBlank()) {
                val list = found
                when {
                    list == null -> item { Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(64.dp)) } }
                    list.isEmpty() -> item { Text("No stations found for “$query”.", Modifier.padding(20.dp), color = c.onBackground.copy(alpha = 0.65f), style = body(15.sp)) }
                    else -> items(list, key = { "f" + it.uuid }) { StationRow(it) { playStation(it) } }
                }
            } else {
                val nearList = near
                val worldList = world
                if (nearList == null && worldList == null) item { Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(64.dp)) } }
                if (!nearList.isNullOrEmpty()) {
                    item { Heading("Near you") }
                    items(nearList, key = { "n" + it.uuid }) { StationRow(it) { playStation(it) } }
                }
                if (!worldList.isNullOrEmpty()) {
                    item { Heading("Around the world") }
                    items(worldList.filter { s -> nearList?.none { it.uuid == s.uuid } != false }, key = { "w" + it.uuid }) { StationRow(it) { playStation(it) } }
                }
                if (nearList?.isEmpty() == true && worldList?.isEmpty() == true) item {
                    Text("Couldn't reach the radio directory. Check your connection and try again.", Modifier.padding(20.dp), color = c.onBackground.copy(alpha = 0.65f), style = body(15.sp))
                }
            }
        }
    }
}

@Composable
private fun Heading(title: String) {
    Text(title, Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.onBackground, style = display(22.sp))
}

@Composable
private fun StationRow(station: Station, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(PrismaLook.corner(18.dp))).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StationArt(station, Modifier.size(56.dp).clip(RoundedCornerShape(PrismaLook.corner(14.dp))))
        Column(Modifier.weight(1f)) {
            Text(station.name, color = c.onBackground, style = body(16.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (station.about.isNotBlank()) Text(station.about, color = c.onBackground.copy(alpha = 0.6f), style = body(13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (station.bitrate > 0) Text("${station.bitrate} kbps", color = c.onBackground.copy(alpha = 0.5f), style = body(12.sp))
    }
}
