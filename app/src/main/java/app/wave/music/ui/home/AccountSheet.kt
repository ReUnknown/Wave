package app.wave.music.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wave.music.data.Session
import app.wave.music.data.WaveSettings
import app.wave.music.playback.AudioQuality
import app.wave.music.ui.detail.Destination
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/** Account & settings (design board 09): your library shortcuts, look & feel, playback. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(
    session: Session,
    onDismiss: () -> Unit,
    onOpen: (Destination) -> Unit,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by WaveSettings.state.collectAsState()
    val c = MaterialTheme.colorScheme
    fun change(block: (app.wave.music.data.Settings) -> app.wave.music.data.Settings) {
        scope.launch { WaveSettings.update(context, block) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surfaceContainer) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (session.photoUrl != null) {
                    AsyncImage(session.photoUrl, "Your Google account", Modifier.size(72.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Rounded.AccountCircle, null, Modifier.size(72.dp), tint = c.onSurfaceVariant)
                }
                Column {
                    Text(session.name, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = c.onSurface)
                    Text(session.email ?: "Signed in with Google", fontSize = 14.sp, color = c.onSurfaceVariant)
                }
            }

            Eyebrow("Your library")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(Icons.Rounded.LibraryMusic, "Playlists", Modifier.weight(1f)) { onOpen(Destination.Playlists) }
                Tile(Icons.Rounded.Album, "Albums & artists", Modifier.weight(1f)) { onOpen(Destination.Saved) }
                Tile(Icons.Rounded.History, "History", Modifier.weight(1f)) { onOpen(Destination.History) }
            }

            Eyebrow("Look & feel")
            Group {
                Toggle("Match song colors", "The whole app takes on the colors of what's playing", settings.matchSongColors) { on ->
                    change { it.copy(matchSongColors = on) }
                }
                Divider()
                Toggle("Move with the music", "Shapes pulse and waves flow with the beat", settings.moveWithMusic) { on ->
                    change { it.copy(moveWithMusic = on) }
                }
            }

            Eyebrow("Playback")
            Group {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 14.dp)) {
                    Text("Audio quality", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface)
                    Text(
                        when (settings.audioQuality) {
                            AudioQuality.HIGH -> "Best available, about 160 kbps"
                            AudioQuality.AUTO -> "High on Wi-Fi, lower on mobile data"
                            AudioQuality.LOW -> "Saves data, about 50 kbps"
                        },
                        fontSize = 13.sp,
                        color = c.onSurfaceVariant,
                    )
                    // Connected segmented choice
                    Row(Modifier.padding(top = 10.dp).fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        listOf(AudioQuality.LOW to "Low", AudioQuality.AUTO to "Auto", AudioQuality.HIGH to "High").forEachIndexed { i, (q, label) ->
                            val selected = settings.audioQuality == q
                            val shape = when (i) {
                                0 -> RoundedCornerShape(22.dp, 8.dp, 8.dp, 22.dp)
                                2 -> RoundedCornerShape(8.dp, 22.dp, 22.dp, 8.dp)
                                else -> RoundedCornerShape(8.dp)
                            }
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(shape)
                                    .background(if (selected) c.primary else c.surfaceContainerHighest)
                                    .clickable { change { it.copy(audioQuality = q) } },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(label, color = if (selected) c.onPrimary else c.onSurface, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Divider()
                Toggle("Skip silence", "Trim quiet gaps between songs", settings.skipSilence) { on ->
                    change { it.copy(skipSilence = on) }
                }
                Divider()
                Toggle("Autoplay", "When a playlist ends, keep going with similar songs", settings.autoplay) { on ->
                    change { it.copy(autoplay = on) }
                }
            }

            Box(
                Modifier
                    .padding(top = 24.dp)
                    .align(Alignment.CenterHorizontally)
                    .height(48.dp)
                    .clip(CircleShape)
                    .border(1.dp, c.outlineVariant, CircleShape)
                    .clickable(onClick = onSignOut)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Sign out", color = c.primary, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun Eyebrow(text: String) {
    Text(
        text.uppercase(),
        Modifier.padding(start = 8.dp, top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Tile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(
        modifier
            .height(92.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(c.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(icon, null, tint = c.primary)
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = c.onSurface)
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 12.dp),
    ) { content() }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
}

@Composable
private fun Toggle(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
