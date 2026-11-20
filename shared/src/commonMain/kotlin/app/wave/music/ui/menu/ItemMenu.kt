package app.wave.music.ui.menu

import app.wave.music.ui.components.glass
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import app.wave.music.ui.components.LocalDesktop
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
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
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
        /** The album's name, when known up front (songs from the player and queue). */
        val album: String? = null,
        /** Opened on whatever's playing: the menu moves on to the next song with the music. */
        val nowPlaying: Boolean = false,
    ) : MenuTarget

    /** [onItsPage]: opened from that item's own page, so "Open …" would go nowhere. */
    data class Collection(val item: YTItem, val onItsPage: Boolean = false) : MenuTarget
}

fun SongItem.menuTarget(removeFrom: RemoveFrom? = null): MenuTarget.Song {
    app.wave.music.data.SongLinks.remember(this)
    return MenuTarget.Song(id, title, artists.joinToString { it.name }, thumbnail, this, removeFrom, album = album?.name)
}

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
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf(Dialog.None) }
    var pendingVideo by remember { mutableStateOf<String?>(null) }
    var dialogTarget by remember { mutableStateOf<PlaylistItem?>(null) }
    val toast: (String) -> Unit = { app.wave.music.ui.components.Notices.show(it) }

    LaunchedEffect(createRequested) {
        if (createRequested) {
            pendingVideo = null
            dialog = Dialog.Create
            onCreateHandled()
        }
    }

    // ---- The options sheet ----
    // In the colors of what it's about (the album, playlist or song you opened it on), not
    // whatever happens to be playing.
    val menuArt = when (target) {
        is MenuTarget.Song -> target.artworkUrl
        is MenuTarget.Collection -> target.item.thumbnail
        null -> null
    }
    val matchColors = app.wave.music.data.WaveSettings.state.collectAsState().value.matchSongColors
    MaterialTheme(colorScheme = app.wave.music.ui.components.rememberArtColorScheme(if (matchColors) menuArt else null)) {
    if (target != null) {
        // Fully open right away, so every option is on screen and nothing shifts under your finger.
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        // Prisma: the menu opens right where your finger is (phones too), not as a panel.
        // Modern: the menu grows out of the ⋮. Legacy: 1.0's sheet from the bottom.
        val desktop = !app.wave.music.platform.PrismaLook.legacy || LocalDesktop.current
        /** Runs [action], then slides the sheet away (or closes the dropdown). */
        fun choose(action: () -> Unit) {
            action()
            if (desktop) onDismiss() else scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() }
        }
        // Songs from the player and queue only know their id and names; their artist and album
        // links are usually already looked up (see SongLinks), and otherwise arrive in the
        // background. The names show right away either way.
        val songTarget = target as? MenuTarget.Song
        var resolved by remember(target) {
            mutableStateOf(songTarget?.item?.takeIf { it.album != null } ?: songTarget?.let { app.wave.music.data.SongLinks.cached(it.videoId) } ?: songTarget?.item)
        }
        var lookedUp by remember(target) { mutableStateOf(target !is MenuTarget.Song || resolved?.album != null) }
        LaunchedEffect(target) {
            if (songTarget != null && !lookedUp) {
                app.wave.music.data.SongLinks.lookup(songTarget.videoId).await()
                    ?.let { found -> resolved = found.copy(album = found.album ?: resolved?.album) }
                lookedUp = true
            }
        }
        /** Goes to a link as soon as it's known (instantly, almost always). */
        fun whenResolved(go: (SongItem) -> Unit) {
            val now = resolved
            if (now != null && lookedUp) { go(now); return }
            scope.launch {
                val found = songTarget?.let { app.wave.music.data.SongLinks.lookup(it.videoId).await() } ?: now
                found?.let(go)
            }
        }
        androidx.compose.runtime.CompositionLocalProvider(LocalDesktop provides desktop) {
        MenuContainer(desktop, sheet, onDismiss) {
                when (target) {
                    is MenuTarget.Song -> {
                        val song = resolved ?: SongItem(
                            id = target.videoId,
                            title = target.title,
                            artists = listOf(Artist(target.artist, null)),
                            thumbnail = target.artworkUrl.orEmpty(),
                        )
                        SheetHeader(target.artworkUrl, target.title, target.artist, round = false)
                        QuickRow {
                            QuickAction(Icons.Rounded.SkipNext, "Play next") { choose { actions.playNext(song) } }
                            QuickAction(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue") { choose { actions.addToQueue(song) } }
                            QuickAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist") {
                                if (app.wave.music.data.SessionRepository.isGuest) {
                                    app.wave.music.ui.components.Notices.show("Sign in with Google to make playlists")
                                } else choose {
                                    pendingVideo = target.videoId
                                    dialog = Dialog.AddToPlaylist
                                }
                            }
                            // Like lives on the player's big button; the menu shares instead.
                            QuickAction(Icons.Rounded.Share, "Share") {
                                choose { share(target.title, "https://music.youtube.com/watch?v=${target.videoId}") }
                            }
                        }
                        OptionGroup {
                            Option(Icons.Rounded.Sensors, "Start radio") { choose { actions.playItem(song, false) } }
                            // Named straight away from what the song already says; the links behind
                            // them are almost always known by now, and otherwise followed once they are.
                            val artistName = song.artists.firstOrNull { it.id != null }?.name
                                ?: target.artist.split(",", " & ", " x ", " feat. ").first().trim().ifBlank { null }
                            Option(
                                Icons.Rounded.Person,
                                artistName?.let { "Go to $it" } ?: "Go to artist",
                                enabled = !lookedUp || song.artists.any { it.id != null },
                            ) {
                                whenResolved { found ->
                                    found.artists.firstOrNull { it.id != null }?.id?.let { id -> choose { actions.navigate(Destination.Artist(id)) } }
                                }
                            }
                            val albumName = song.album?.name ?: target.album?.takeIf { it.isNotBlank() }
                            Option(
                                Icons.Rounded.Album,
                                albumName?.let { "Go to $it" } ?: "Go to album",
                                enabled = !lookedUp || song.album != null,
                            ) {
                                whenResolved { found -> found.album?.let { choose { actions.navigate(Destination.Album(it.id)) } } }
                            }
                            target.removeFrom?.let { from ->
                                Option(Icons.Rounded.PlaylistRemove, "Remove from this playlist", danger = true) {
                                    choose {
                                        scope.launch {
                                            LibraryRepository.remove(from.playlistId, target.videoId, from.setVideoId)
                                                .onSuccess { toast("Removed from playlist") }
                                                .onFailure { toast("Couldn't remove it") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    is MenuTarget.Collection -> {
                        val item = target.item
                        SheetHeader(item.thumbnail, item.title, subtitle(item).orEmpty(), round = item is ArtistItem)
                        QuickRow {
                            if (item is ArtistItem) {
                                QuickAction(Icons.Rounded.Shuffle, "Shuffle") { choose { actions.playItem(item, true) } }
                                QuickAction(Icons.Rounded.Sensors, "Radio") { choose { actions.playItem(item, false) } }
                                QuickAction(Icons.Rounded.Share, "Share") { choose { share(item.title, item.shareLink) } }
                            } else {
                                QuickAction(Icons.Rounded.PlayArrow, "Play", on = true) { choose { actions.playItem(item, false) } }
                                QuickAction(Icons.Rounded.Shuffle, "Shuffle") { choose { actions.playItem(item, true) } }
                                QuickAction(Icons.Rounded.SkipNext, "Play next") { choose { actions.playNext(item) } }
                                QuickAction(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue") { choose { actions.addToQueue(item) } }
                            }
                        }
                        val open = Destination.of(item)?.takeUnless { target.onItsPage }
                        val albumArtist = (item as? AlbumItem)?.artists?.firstOrNull { it.id != null }
                        val editable = item is PlaylistItem && item.isEditable && LibraryRepository.idOf(item) != "LM"
                        run {
                            OptionGroup {
                                if (item !is ArtistItem) {
                                    Option(Icons.Rounded.Share, "Share") { choose { share(item.title, item.shareLink) } }
                                }
                                if (open != null) {
                                    val label = when (item) {
                                        is ArtistItem -> "Open artist page"
                                        is AlbumItem -> "Open album"
                                        else -> "Open playlist"
                                    }
                                    Option(Icons.AutoMirrored.Rounded.OpenInNew, label) { choose { actions.navigate(open) } }
                                }
                                albumArtist?.let { artist ->
                                    Option(Icons.Rounded.Person, "Go to ${artist.name}") { choose { actions.navigate(Destination.Artist(artist.id!!)) } }
                                }
                                if (editable) {
                                    Option(Icons.Rounded.Edit, "Rename playlist") {
                                        choose {
                                            dialogTarget = item
                                            dialog = Dialog.Rename
                                        }
                                    }
                                    Option(Icons.Rounded.Delete, "Delete playlist", danger = true) {
                                        choose {
                                            dialogTarget = item
                                            dialog = Dialog.Delete
                                        }
                                    }
                                }
                            }
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
        val pick: (PlaylistItem) -> Unit = { playlist ->
            pendingVideo?.let { video ->
                dialog = Dialog.None
                scope.launch {
                    LibraryRepository.add(LibraryRepository.idOf(playlist), video)
                        .onSuccess { toast("Added to ${playlist.title}") }
                        .onFailure { toast("Couldn't add it") }
                }
            }
        }
        if (LocalDesktop.current) {
            // Computers: a dialog, not a panel sliding up.
            AlertDialog(
                onDismissRequest = { dialog = Dialog.None },
                title = { Text("Add to playlist") },
                text = {
                    LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        item { PlaylistChoice(null, "New playlist", isNew = true) { dialog = Dialog.Create } }
                        items(playlists.filter { it.isEditable }) { playlist ->
                            PlaylistChoice(playlist.thumbnail, playlist.title, isNew = false) { pick(playlist) }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { dialog = Dialog.None }) { Text("Cancel") } },
            )
        } else
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

/** Phones and tablets: a panel sliding up. Computers: a dropdown right where you clicked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuContainer(desktop: Boolean, sheet: androidx.compose.material3.SheetState, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    if (desktop) {
        MorphMenu(onDismiss, content)
    } else {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Box {
                Column(
                    Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) { content() }
                // Notices from inside the menu (liking a song) show on the menu itself, not under its shade,
                // over its header so they never cover an option.
                app.wave.music.ui.components.NoticeHost(Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

/**
 * The menu *is* the ⋮ you pressed: it starts as that exact circle (same spot, size, color and
 * dots), then stretches out into the menu while the dots fade and the options come in. Tapping
 * outside folds it back into the button. Long-press menus start from a small circle under your finger.
 */
@Composable
private fun MorphMenu(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val spot = PointerSpot.position
    // The ⋮ that was just pressed, if there was one; otherwise a finger-sized circle.
    val from = androidx.compose.runtime.remember {
        val a = MenuAnchor.pressed
        if (a != null && System.currentTimeMillis() - MenuAnchor.at < 1500) a
        else with(density) { MenuAnchor.Spot(androidx.compose.ui.geometry.Rect(spot, 22.dp.toPx()), Color.Black.copy(alpha = 0.32f), Color.Transparent) }
    }
    MenuAnchor.pressed = null
    val margin = with(density) { 12.dp.toPx() }
    val progress = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { progress.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.82f, stiffness = 360f)) }
    var closing by androidx.compose.runtime.remember { mutableStateOf(false) }
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                progress.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 700f))
                onDismiss()
            }
        }
    }
    app.wave.music.ui.components.predictiveBack(onBack = close)
    val c = MaterialTheme.colorScheme
    val haze = LocalAppHaze.current
    // Drawn in the app's own window (not a popup), so it can blur what's behind it.
    var offset by androidx.compose.runtime.remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { close() } },
    ) {
        val shape = androidx.compose.runtime.remember {
            object : androidx.compose.ui.graphics.Shape {
                override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
                    val p = progress.value.coerceIn(0f, 1.04f)
                    val start = from.rect.translate(-offset)
                    fun l(a: Float, b: Float) = a + (b - a) * p
                    val rect = androidx.compose.ui.geometry.Rect(
                        l(start.left, 0f).coerceAtLeast(0f), l(start.top, 0f).coerceAtLeast(0f),
                        l(start.right, size.width).coerceAtMost(size.width), l(start.bottom, size.height).coerceAtMost(size.height),
                    )
                    val corner = l(start.height / 2f, with(density) { app.wave.music.platform.PrismaLook.corner(24.dp).toPx() }).coerceAtMost(rect.minDimension / 2f)
                    return androidx.compose.ui.graphics.Outline.Rounded(androidx.compose.ui.geometry.RoundRect(rect, androidx.compose.ui.geometry.CornerRadius(corner)))
                }
            }
        }
        androidx.compose.ui.layout.Layout(
            content = {
                Box(
                    Modifier
                        .pointerInput(Unit) { detectTapGestures { } }
                        .graphicsLayer {
                            this.shape = shape
                            clip = true
                            shadowElevation = 16.dp.toPx() * progress.value.coerceIn(0f, 1f)
                        }
                        .glass(haze, 28.dp, c.background, listOf(dev.chrisbanes.haze.HazeTint(c.surfaceContainerHigh.copy(alpha = 0.62f))), Color.Transparent)
                        .drawBehind {
                            // Starts as the button's own color, then gives way to the frosted glass.
                            val p = (progress.value * 1.6f).coerceIn(0f, 1f)
                            val glass = if (haze != null && app.wave.music.platform.PrismaLook.blur > 0) Color.Transparent else c.surfaceContainerHigh
                            drawRect(androidx.compose.ui.graphics.lerp(from.color, glass, p))
                            drawRect(Color.White.copy(alpha = 0.04f))
                        },
                ) {
                    Column(
                        Modifier
                            .widthIn(min = 200.dp, max = 264.dp)
                            .width(androidx.compose.foundation.layout.IntrinsicSize.Max)
                            .graphicsLayer {
                                val p = progress.value
                                alpha = ((p - 0.3f) / 0.6f).coerceIn(0f, 1f)
                                translationY = (1f - p.coerceIn(0f, 1f)) * -10.dp.toPx()
                            }
                            .verticalScroll(androidx.compose.foundation.rememberScrollState())
                            .padding(vertical = 6.dp),
                    ) { content() }
                    // The ⋮ itself, right where the button is, fading as it opens up.
                    Icon(
                        androidx.compose.material.icons.Icons.Rounded.MoreVert, null, tint = from.ink,
                        modifier = Modifier
                            .offset {
                                val dots = from.rect.translate(-offset)
                                androidx.compose.ui.unit.IntOffset((dots.center.x - 12.dp.toPx()).toInt(), (dots.center.y - 12.dp.toPx()).toInt())
                            }
                            .size(24.dp)
                            .graphicsLayer { alpha = (1f - progress.value / 0.3f).coerceIn(0f, 1f) },
                    )
                }
            },
        ) { measurables, constraints ->
            val maxH = (constraints.maxHeight - 2 * margin).toInt().coerceAtLeast(0)
            val placeable = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0, maxHeight = maxH))
            val w = placeable.width.toFloat()
            val h = placeable.height.toFloat()
            val r = from.rect
            // Shares a corner with the button, opening toward the side with room.
            val toLeft = r.center.x > constraints.maxWidth / 2f
            val x = (if (toLeft) r.right - w else r.left).coerceIn(margin, (constraints.maxWidth - w - margin).coerceAtLeast(margin))
            val down = r.top + h + margin < constraints.maxHeight
            val y = (if (down) r.top else r.bottom - h).coerceIn(margin, (constraints.maxHeight - h - margin).coerceAtLeast(margin))
            offset = androidx.compose.ui.geometry.Offset(x, y)
            layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(x.toInt(), y.toInt()) }
        }
    }
}

/** The whole app, for menus to blur what's behind them. */
val LocalAppHaze = staticCompositionLocalOf<dev.chrisbanes.haze.HazeState?> { null }

/** The ⋮ buttons a menu grows out of. */
object MenuAnchor {
    class Spot(val rect: androidx.compose.ui.geometry.Rect, val color: Color, val ink: Color)
    var pressed: Spot? = null
    var at = 0L
}

/** Marks a ⋮ button so its menu grows out of it. [color] is the button's own fill, [ink] its dots. */
fun Modifier.menuAnchor(color: Color, ink: Color): Modifier = composed {
    var bounds by androidx.compose.runtime.remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    this
        .onGloballyPositioned { bounds = it.boundsInRoot() }
        .pointerInput(color, ink) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                    if (e.type == androidx.compose.ui.input.pointer.PointerEventType.Press) {
                        MenuAnchor.pressed = MenuAnchor.Spot(bounds, color, ink)
                        MenuAnchor.at = System.currentTimeMillis()
                    }
                }
            }
        }
}

/** Where the last click or tap landed, so a computer's dropdown opens right there. */
object PointerSpot {
    var position by androidx.compose.runtime.mutableStateOf(androidx.compose.ui.geometry.Offset.Zero)
}

private val LocalQuickRow = androidx.compose.runtime.compositionLocalOf<RowScope?> { null }

/** The most-used actions: big tiles in a row on phones, the first items of the dropdown on computers. */
@Composable
private fun QuickRow(content: @Composable () -> Unit) {
    if (LocalDesktop.current) {
        content()
        androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 3.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.runtime.CompositionLocalProvider(LocalQuickRow provides this) { content() }
        }
    }
}

@Composable
private fun SheetHeader(art: String?, title: String, subtitle: String, round: Boolean) {
    if (LocalDesktop.current) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AsyncImage(art?.atSize(200), null, Modifier.size(40.dp).clip(if (round) CircleShape else RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 3.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        return
    }
    Row(
        Modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AsyncImage(
            art?.atSize(200),
            null,
            Modifier.size(64.dp).clip(if (round) CircleShape else RoundedCornerShape(18.dp)),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A big tile for the most-used actions, in a row across the top (like the settings tiles). */
@Composable
private fun QuickAction(icon: ImageVector, label: String, on: Boolean = false, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val row = LocalQuickRow.current
    if (row == null) {
        androidx.compose.material3.DropdownMenuItem(
            text = { Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1) },
            leadingIcon = { Icon(icon, null, tint = if (on) c.primary else c.onSurface, modifier = Modifier.size(22.dp)) },
            onClick = onClick,
            modifier = Modifier.height(44.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
        )
        return
    }
    Column(
        with(row) { Modifier.weight(1f) }
            .height(88.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (on) c.primary else c.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(icon, null, tint = if (on) c.onPrimary else c.primary)
        Text(
            label,
            color = if (on) c.onPrimary else c.onSurface,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 15.sp,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

/** The rest of the options, grouped on one rounded card. */
@Composable
private fun OptionGroup(content: @Composable ColumnScope.() -> Unit) {
    if (LocalDesktop.current) {
        Column(content = content)
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        content = content,
    )
}

@Composable
private fun Option(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    loading: Boolean = false,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val tint = if (danger) c.error else c.onSurface
    if (LocalDesktop.current) {
        androidx.compose.material3.DropdownMenuItem(
            text = { Text(label, fontSize = 15.sp, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(icon, null, tint = if (danger) c.error else c.onSurfaceVariant, modifier = Modifier.size(22.dp)) },
            trailingIcon = if (loading) ({ CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) }) else null,
            enabled = enabled,
            onClick = onClick,
            modifier = Modifier.height(44.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
        )
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled || loading) 1f else 0.38f)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Icon(icon, null, tint = if (danger) c.error else c.onSurfaceVariant)
        Text(label, Modifier.weight(1f), fontSize = 16.sp, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = c.onSurfaceVariant)
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

/** The system's share sheet with a YouTube Music link (anyone with Wave can open it in Wave). */
private fun share(title: String, url: String) = app.wave.music.platform.WavePlatform.current.share(title, url)
