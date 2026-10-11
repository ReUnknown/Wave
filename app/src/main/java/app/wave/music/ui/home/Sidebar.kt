package app.wave.music.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wave.music.ui.components.WaveMark
import app.wave.music.ui.components.atSize
import app.wave.music.ui.components.onRightClick
import app.wave.music.ui.components.pressable
import app.wave.music.ui.detail.Destination
import app.wave.music.ui.menu.LocalItemMenu
import app.wave.music.ui.menu.openFor
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.PlaylistItem

/**
 * Computers and tablets in landscape: your library, always there on the left (design board 1).
 * Home, albums & artists, history, then your playlists. In the song's colors like everything else.
 */
@Composable
fun Sidebar(
    current: Destination?,
    playlists: List<PlaylistItem>,
    onHome: () -> Unit,
    onOpen: (Destination) -> Unit,
    onNewPlaylist: () -> Unit,
    onPlaylist: (PlaylistItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    // No panel of its own: it sits on the same background as home (the song's art fading into
    // the solid color), so the window reads as one surface.
    Box(
        modifier
            .width(264.dp)
            .fillMaxHeight(),
    ) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.padding(start = 10.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(c.primary), contentAlignment = Alignment.Center) {
                WaveMark(c.onPrimary, size = 22.dp)
            }
            Text("Wave", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
        NavRow(Icons.Rounded.Home, "Home", selected = current == null, onClick = onHome)
        NavRow(Icons.Rounded.Album, "Albums & artists", selected = current == Destination.Saved) { onOpen(Destination.Saved) }
        NavRow(Icons.Rounded.History, "History", selected = current == Destination.History) { onOpen(Destination.History) }

        Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("PLAYLISTS", Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Color.White.copy(alpha = 0.75f))
            Box(
                Modifier
                    .size(36.dp)
                    .pressable(CircleShape, pressedScale = 0.88f, onClick = onNewPlaylist),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, "New playlist", tint = Color.White.copy(alpha = 0.85f)) }
        }
        val menu = LocalItemMenu.current
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(playlists, key = { it.id }) { p ->
                val open = (current as? Destination.Playlist)?.id == p.id.removePrefix("VL")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .onRightClick { menu.openFor(p) }
                        .pressable(RoundedCornerShape(14.dp), if (open) c.secondaryContainer else Color.Transparent, pressedScale = 0.97f) { onPlaylist(p) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AsyncImage(
                        p.thumbnail?.atSize(120),
                        null,
                        Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(c.surfaceContainerHigh),
                        contentScale = ContentScale.Crop,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(p.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        p.songCountText?.let { Text(it, fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1) }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
    }
}

@Composable
private fun NavRow(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (selected) c.secondaryContainer else Color.Transparent, tween(160), label = "nav")
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .pressable(RoundedCornerShape(23.dp), fill, pressedScale = 0.97f, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, null, tint = if (selected) c.onSecondaryContainer else Color.White.copy(alpha = 0.8f))
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) c.onSecondaryContainer else Color.White,
        )
    }
}
