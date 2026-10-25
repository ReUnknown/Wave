package app.wave.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.compose.AsyncImage
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.launch

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Wave", state = rememberWindowState(width = 1100.dp, height = 760.dp)) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Prototype()
        }
    }
}

/** First desktop milestone: search YouTube Music and play a song through the desktop audio engine. */
@Composable
private fun Prototype() {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SongItem>>(emptyList()) }
    var current by remember { mutableStateOf<SongItem?>(null) }
    var status by remember { mutableStateOf("Search for a song") }
    val engine by AudioEngine.state.collectAsState()
    val c = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().background(c.background).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Wave", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = c.onBackground)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search songs") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                scope.launch {
                    status = "Searching…"
                    YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
                        .onSuccess { r -> results = r.items.filterIsInstance<SongItem>(); status = "${results.size} songs" }
                        .onFailure { status = "Search failed: ${it.message}" }
                }
            }),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
        )
        Text(engine.error?.let { "Playback failed: $it" } ?: status, color = c.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(results, key = { it.id }) { song ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            current = song
                            status = "Loading ${song.title}…"
                            scope.launch {
                                runCatching { Streams.resolve(song.id) }
                                    .onSuccess { AudioEngine.play(it); status = "Playing" }
                                    .onFailure { status = "Couldn't get a stream: ${it.message}" }
                            }
                        }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AsyncImage(song.thumbnail, null, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                    Column {
                        Text(song.title, color = c.onBackground, fontWeight = FontWeight.SemiBold)
                        Text(song.artists.joinToString { it.name }, color = c.onSurfaceVariant)
                    }
                }
            }
        }
        current?.let { song ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.surfaceContainerHigh).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AsyncImage(song.thumbnail, null, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f)) {
                    Text(song.title, color = c.onSurface, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(
                        progress = { if (engine.durationMs > 0) engine.positionMs.toFloat() / engine.durationMs else 0f },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp),
                    )
                }
                Box(Modifier.size(52.dp).clip(CircleShape).background(c.primary), contentAlignment = Alignment.Center) {
                    IconButton(onClick = AudioEngine::togglePause) {
                        Icon(if (engine.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = c.onPrimary)
                    }
                }
            }
        }
    }
}
