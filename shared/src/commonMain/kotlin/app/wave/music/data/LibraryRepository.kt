package app.wave.music.data

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.YTItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Your playlists on YouTube Music, and the edits Wave can make to them. */
object LibraryRepository {
    private val _playlists = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val playlists: StateFlow<List<PlaylistItem>> = _playlists.asStateFlow()

    /** Bumped after every edit so open playlist pages reload. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    fun clear() {
        _playlists.value = emptyList()
    }

    /** Playlist id as the edit endpoints expect it (no "VL" prefix). */
    fun idOf(playlist: PlaylistItem) = playlist.id.removePrefix("VL")

    /** Every page of a library shelf, following continuations to the end. */
    private suspend fun loadAll(browseId: String): List<YTItem> {
        val first = YouTube.library(browseId).getOrThrow()
        val items = first.items.toMutableList()
        var next = first.continuation
        var pages = 0
        while (next != null && pages++ < 50) {
            val more = YouTube.libraryContinuation(next).getOrNull() ?: break
            items += more.items
            next = more.continuation
        }
        return items
    }

    /** Guests have no library: nothing to load, and edits need an account. */
    private fun <T> needsAccount(): Result<T> {
        app.wave.music.ui.components.Notices.show("Sign in with Google to make playlists")
        return Result.failure(IllegalStateException("Not signed in"))
    }

    /** Albums you've saved to your library. */
    suspend fun savedAlbums(): Result<List<AlbumItem>> = if (SessionRepository.isGuest) Result.success(emptyList()) else runCatching {
        loadAll("FEmusic_liked_albums").filterIsInstance<AlbumItem>().distinctBy { it.browseId }
    }

    /** Artists you follow or have saved music from. */
    suspend fun savedArtists(): Result<List<ArtistItem>> = if (SessionRepository.isGuest) Result.success(emptyList()) else runCatching {
        loadAll("FEmusic_library_corpus_artists").filterIsInstance<ArtistItem>().filterNot { it.isProfile }.distinctBy { it.id }
    }

    suspend fun refresh(): Result<List<PlaylistItem>> = if (SessionRepository.isGuest) Result.success(emptyList<PlaylistItem>().also { _playlists.value = it }) else runCatching {
        val items = loadAll("FEmusic_liked_playlists")
        items.filterIsInstance<PlaylistItem>()
            .filterNot { it.isPodcast }
            .distinctBy { idOf(it) }
            .also { _playlists.value = it }
    }

    /** Playlists you can add songs to (your own, plus Liked music). */
    fun editable(): List<PlaylistItem> = _playlists.value.filter { it.isEditable }

    fun search(query: String): List<PlaylistItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return _playlists.value.filter { it.title.contains(q, ignoreCase = true) }
    }

    suspend fun create(title: String): Result<String> = if (SessionRepository.isGuest) needsAccount() else runCatching {
        YouTube.createPlaylist(title.trim()).also { edited() }
    }

    suspend fun add(playlistId: String, videoId: String): Result<Unit> = if (SessionRepository.isGuest) needsAccount() else
        YouTube.addToPlaylist(playlistId, videoId).map { edited() }

    suspend fun remove(playlistId: String, videoId: String, setVideoId: String): Result<Unit> =
        YouTube.removeFromPlaylist(playlistId, videoId, setVideoId).map { edited() }

    suspend fun rename(playlistId: String, name: String): Result<Unit> =
        YouTube.renamePlaylist(playlistId, name.trim()).map { edited() }

    suspend fun delete(playlistId: String): Result<Unit> =
        YouTube.deletePlaylist(playlistId).map { edited() }

    private suspend fun edited() {
        _version.value++
        refresh()
    }
}
