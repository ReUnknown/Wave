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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import app.wave.music.ui.components.pressable
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

/**
 * Your account (design board 09): who's signed in, shortcuts into your library, settings and
 * sign out. Settings themselves live on their own page so this stays short.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(
    session: Session,
    onDismiss: () -> Unit,
    onOpen: (Destination) -> Unit,
    onSignOut: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    // Opens fully in one motion: no half-open stage where the first tap is taken as a drag.
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.surfaceContainer) {
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

            Row(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .height(64.dp)
                    .pressable(RoundedCornerShape(24.dp), c.surfaceContainerHighest, pressedScale = 0.97f) { onOpen(Destination.Settings) }
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(Icons.Rounded.Settings, null, tint = c.primary)
                Column(Modifier.weight(1f)) {
                    Text("Settings", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = c.onSurface)
                    Text("Look & feel, playback, battery, updates", fontSize = 13.sp, color = c.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.onSurfaceVariant)
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
            .pressable(RoundedCornerShape(24.dp), c.surfaceContainerHighest, pressedScale = 0.95f, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(icon, null, tint = c.primary)
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = c.onSurface)
    }
}



