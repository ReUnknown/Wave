package app.wave.music.data

import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Songs played in Wave since it started, newest first. YouTube's history only catches up after a
 * refresh, so "Recently played" on home puts these in front of it right away.
 */
object RecentPlays {
    private val _songs = MutableStateFlow<List<SongItem>>(emptyList())
    val songs: StateFlow<List<SongItem>> = _songs.asStateFlow()

    fun add(id: String, title: String, artist: String, art: String?, album: String?, durationMs: Long?) {
        val song = SongItem(
            id = id,
            title = title,
            artists = listOf(Artist(artist, null)),
            album = album?.takeIf { it.isNotBlank() }?.let { Album(it, "") },
            duration = durationMs?.takeIf { it > 0 }?.let { (it / 1000).toInt() },
            thumbnail = art.orEmpty(),
        )
        _songs.value = (listOf(song) + _songs.value.filter { it.id != id }).take(12)
    }

    fun clear() {
        _songs.value = emptyList()
    }
}
