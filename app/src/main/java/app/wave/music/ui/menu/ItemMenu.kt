package app.wave.music.ui.menu

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wave.music.data.LibraryRepository
import app.wave.music.ui.components.atSize
import app.wave.music.ui.components.subtitle
import app.wave.music.ui.detail.Destination
import coil3.compose.AsyncImage
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.YTItem
import kotlinx.coroutines.launch

/** Where a song sits in one of your playlists, so it can be removed from it. */
data class RemoveFrom(val playlistId: String, val setVideoId: String)

/** What the options menu is about. */
sealed interface MenuTarget {
    data class Song(
        val videoId: String,
        val title: String,
        val artist: String,
        val artworkUrl: String?,
        val item: SongItem? = null,
        val removeFrom: RemoveFrom? = null,
    ) : MenuTarget

    data class Collection(val item: YTItem) : MenuTarget
}

fun SongItem.menuTarget(removeFrom: RemoveFrom? = null) =
    MenuTarget.Song(id, title, artists.joinToString { it.name }, thumbnail, this, removeFrom)

/** Long-press anywhere calls this to open the options menu. */
fun interface MenuOpener {
    fun open(target: MenuTarget)
}

val LocalItemMenu = staticCompositionLocalOf { MenuOpener { } }

/** Opens the menu for any item: songs get song options, the rest collection options. */
fun MenuOpener.openFor(item: YTItem, removeFrom: RemoveFrom? = null) =
    open(if (item is SongItem) item.menuTarget(removeFrom) else MenuTarget.Collection(item))

class MenuActions(
    val playItem: (YTItem, shuffle: Boolean) -> Unit,
    val playNext: (YTItem) -> Unit,
    val addToQueue: (YTItem) -> Unit,
    val navigate: (Destination) -> Unit,
)

private enum class Dialog { None, AddToPlaylist, Create, Rename, Delete }

/**
 * Hosts every options sheet and dialog: the song/collection menu, "Add to playlist",
 * and creating, renaming or deleting your playlists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemMenuHost(
    target: MenuTarget?,
    onDismiss: () -> Unit,
    actions: MenuActions,
    createRequested: Boolean,
    onCreateHandled: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf(Dialog.None) }
    var pendingVideo by remember { mutableStateOf<String?>(null) }
    var dialogTarget by remember { mutableStateOf<PlaylistItem?>(null) }
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }

    LaunchedEffect(createRequested) {
        if (createRequested) {
            pendingVideo = null
            dialog = Dialog.Create
            onCreateHandled()
        }
    }

    // ---- The options sheet ----
    if (target != null) {
        // Songs opened from Now Playing only know their id; look up artist and album links.
        var resolved by remember(target) { mutableStateOf((target as? MenuTarget.Song)?.item) }
        LaunchedEffect(target) {
            if (target is MenuTarget.Song && (resolved == null || resolved?.album == null)) {
                YouTube.next(WatchEndpoint(videoId = target.videoId)).getOrNull()
                    ?.items?.firstOrNull { it.id == target.videoId }?.let { resolved = it }
            }
        }
        ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                when (target) {
                    is MenuTarget.Song -> {
                        val song = resolved ?: SongItem(
                            id = target.videoId,
                            title = target.title,
                            artists = listOf(Artist(target.artist, null)),
                            thumbnail = target.artworkUrl.orEmpty(),
                        )
                        SheetHeader(target.artworkUrl, target.title, target.artist, round = false)
                        Option(Icons.Rounded.SkipNext, "Play next") { actions.playNext(song); onDismiss() }
                        Option(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue") { actions.addToQueue(song); onDismiss() }
                        Option(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist") {
                            pendingVideo = target.videoId
                            dialog = Dialog.AddToPlaylist
                            onDismiss()
                        }
                        Option(Icons.Rounded.Sensors, "Start radio") { actions.playItem(song, false); onDismiss() }
                        song.artists.firstOrNull { it.id != null }?.let { artist ->
                            Option(Icons.Rounded.Person, "Go to ${artist.name}") { actions.navigate(Destination.Artist(artist.id!!)); onDismiss() }
                        }
                        song.album?.let { album ->
                            Option(Icons.Rounded.Album, "Go to album") { actions.navigate(Destination.Album(album.id)); onDismiss() }
                        }
                        Option(Icons.Outlined.ThumbUp, "Like song") {
                            scope.launch { YouTube.likeVideo(target.videoId, true).onSuccess { toast("Added to Liked music") } }
                            onDismiss()
                        }
                        target.removeFrom?.let { from ->
                            Option(Icons.Rounded.PlaylistRemove, "Remove from this playlist") {
                                scope.launch {
                                    LibraryRepository.remove(from.playlistId, target.videoId, from.setVideoId)
                                        .onSuccess { toast("Removed from playlist") }
                                        .onFailure { toast("Couldn't remove it") }
                                }
                                onDismiss()
                            }
                        }
                    }
                    is MenuTarget.Collection -> {
                        val item = target.item
                        SheetHeader(item.thumbnail, item.title, subtitle(item).orEmpty(), round = item is ArtistItem)
                        if (item is ArtistItem) {
                            Option(Icons.Rounded.Shuffle, "Shuffle") { actions.playItem(item, true); onDismiss() }
                            Option(Icons.Rounded.Sensors, "Start radio") { actions.playItem(item, false); onDismiss() }
                        } else {
                            Option(Icons.Rounded.PlayArrow, "Play") { actions.playItem(item, false); onDismiss() }
                            Option(Icons.Rounded.Shuffle, "Shuffle") { actions.playItem(item, true); onDismiss() }
                            Option(Icons.Rounded.SkipNext, "Play next") { actions.playNext(item); onDismiss() }
                            Option(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue") { actions.addToQueue(item); onDismiss() }
                        }
                        if (item is PlaylistItem && item.isEditable && LibraryRepository.idOf(item) != "LM") {
                            Option(Icons.Rounded.Edit, "Rename playlist") {
                                dialogTarget = item
                                dialog = Dialog.Rename
                                onDismiss()
                            }
                            Option(Icons.Rounded.Delete, "Delete playlist") {
                                dialogTarget = item
                                dialog = Dialog.Delete
                                onDismiss()
                            }
                        }
                        if (item is AlbumItem) {
                            item.artists?.firstOrNull { it.id != null }?.let { artist ->
                                Option(Icons.Rounded.Person, "Go to ${artist.name}") { actions.navigate(Destination.Artist(artist.id!!)); onDismiss() }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- Add to playlist ----
    if (dialog == Dialog.AddToPlaylist) {
        val playlists by LibraryRepository.playlists.collectAsState()
        LaunchedEffect(Unit) { if (playlists.isEmpty()) LibraryRepository.refresh() }
        ModalBottomSheet(onDismissRequest = { dialog = Dialog.None }, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Text(
                "Add to playlist",
                Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            LazyColumn(Modifier.navigationBarsPadding()) {
                item {
                    PlaylistChoice(null, "New playlist", isNew = true) { dialog = Dialog.Create }
                }
                items(playlists.filter { it.isEditable }) { playlist ->
                    PlaylistChoice(playlist.thumbnail, playlist.title, isNew = false) {
                        val video = pendingVideo ?: return@PlaylistChoice
                        dialog = Dialog.None
                        scope.launch {
                            LibraryRepository.add(LibraryRepository.idOf(playlist), video)
                                .onSuccess { toast("Added to ${playlist.title}") }
                                .onFailure { toast("Couldn't add it") }
                        }
                    }
                }
            }
        }
    }

    // ---- New / rename / delete ----
    if (dialog == Dialog.Create || dialog == Dialog.Rename) {
        val renaming = dialog == Dialog.Rename
        var name by remember(dialog) { mutableStateOf(if (renaming) dialogTarget?.title.orEmpty() else "") }
        AlertDialog(
            onDismissRequest = { dialog = Dialog.None },
            title = { Text(if (renaming) "Rename playlist" else "New playlist") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    dialog = Dialog.None
                    scope.launch {
                        if (renaming) {
                            val p = dialogTarget ?: return@launch
                            LibraryRepository.rename(LibraryRepository.idOf(p), name).onSuccess { toast("Renamed") }.onFailure { toast("Couldn't rename it") }
                        } else {
                            LibraryRepository.create(name)
                                .onSuccess { id ->
                                    val video = pendingVideo
                                    if (video != null) {
                                        LibraryRepository.add(id, video)
                                        toast("Added to $name")
                                    } else {
                                        toast("Created $name")
                                        actions.navigate(Destination.Playlist(id))
                                    }
                                }
                                .onFailure { toast("Couldn't create the playlist") }
                        }
                    }
                }) { Text(if (renaming) "Save" else "Create") }
            },
            dismissButton = { TextButton(onClick = { dialog = Dialog.None }) { Text("Cancel") } },
        )
    }
    if (dialog == Dialog.Delete) {
        val p = dialogTarget
        AlertDialog(
            onDismissRequest = { dialog = Dialog.None },
            title = { Text("Delete playlist?") },
            text = { Text("\"${p?.title}\" will be removed from your YouTube Music library.") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = Dialog.None
                    scope.launch {
                        p?.let { LibraryRepository.delete(LibraryRepository.idOf(it)).onSuccess { toast("Deleted") }.onFailure { toast("Couldn't delete it") } }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { dialog = Dialog.None }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SheetHeader(art: String?, title: String, subtitle: String, round: Boolean) {
    Row(
        Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AsyncImage(
            art?.atSize(200),
            null,
            Modifier.size(56.dp).clip(if (round) CircleShape else RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Option(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PlaylistChoice(art: String?, title: String, isNew: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (isNew) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onPrimary) }
        } else {
            AsyncImage(art?.atSize(200), null, Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
        }
        Text(title, fontSize = 16.sp, fontWeight = if (isNew) FontWeight.Bold else FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
