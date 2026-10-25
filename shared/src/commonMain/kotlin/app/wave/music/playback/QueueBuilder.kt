package app.wave.music.playback

import app.wave.music.ui.components.atSize
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.YTItem

/** Where the current queue came from: a playlist, album, mix, radio or home section. */
data class PlaybackContext(
    val id: String,
    val title: String,
    val artworkUrl: String?,
    /** Playlist id to rebuild this queue later, for "resume where you left off". */
    val playlistId: String?,
    val isCollection: Boolean,
)

class PlayRequest(
    /** The songs, in queue order (each platform turns them into its own player's items). */
    val items: List<NowPlaying>,
    val startIndex: Int,
    val context: PlaybackContext,
    val shuffle: Boolean,
    /** The rest of a long queue, loaded in the background after playback has started. */
    val more: (suspend () -> List<NowPlaying>)? = null,
)

object QueueBuilder {
    /** Pages loaded in the background after the first: plenty for albums and playlists, a few for endless radios. */
    private const val COLLECTION_PAGES = 40
    private const val RADIO_PAGES = 3

    fun SongItem.toQueueItem(): NowPlaying = NowPlaying(
        videoId = id,
        title = title,
        artist = artists.joinToString { it.name },
        artworkUrl = thumbnail.atSize(1080),
        album = album?.name,
        durationMs = duration?.let { it * 1000L },
    )

    suspend fun forItem(item: YTItem, shuffle: Boolean = false): Result<PlayRequest> = runCatching {
        when (item) {
            is SongItem -> fromEndpoint(
                WatchEndpoint(videoId = item.id),
                PlaybackContext("radio-${item.id}", "${item.title} radio", item.thumbnail, null, isCollection = false),
                shuffle = false,
            )
            is PlaylistItem -> {
                val endpoint = (if (shuffle) item.shuffleEndpoint else item.playEndpoint)
                    ?: WatchEndpoint(playlistId = item.id.removePrefix("VL"))
                fromEndpoint(
                    endpoint,
                    PlaybackContext(item.id, item.title, item.thumbnail, endpoint.playlistId ?: item.id.removePrefix("VL"), isCollection = true),
                    shuffle = false,
                )
            }
            is AlbumItem -> fromEndpoint(
                WatchEndpoint(playlistId = item.playlistId),
                PlaybackContext(item.id, item.title, item.thumbnail, item.playlistId, isCollection = true),
                shuffle,
            )
            is ArtistItem -> {
                val endpoint = (if (shuffle) item.shuffleEndpoint else item.radioEndpoint)
                    ?: error("Nothing to play for ${item.title}")
                fromEndpoint(endpoint, PlaybackContext(item.id, "${item.title} radio", item.thumbnail, endpoint.playlistId, isCollection = false), shuffle = false)
            }
            else -> error("Can't play this item")
        }
    }

    /** A known list of songs (an album or playlist page), starting at [startIndex]. */
    fun forList(songs: List<SongItem>, startIndex: Int, context: PlaybackContext, shuffle: Boolean): PlayRequest = PlayRequest(
        items = songs.map { it.toQueueItem() },
        startIndex = if (shuffle) songs.indices.random() else startIndex.coerceIn(0, (songs.size - 1).coerceAtLeast(0)),
        context = context,
        shuffle = shuffle,
    )

    /** Just this song, for "Play next". */
    fun single(song: SongItem): PlayRequest = PlayRequest(
        items = listOf(song.toQueueItem()),
        startIndex = 0,
        context = PlaybackContext("song-${song.id}", song.title, song.thumbnail, null, isCollection = false),
        shuffle = false,
    )

    /** "Play all" on a home section of songs. */
    fun forSongs(sectionTitle: String, songs: List<SongItem>, shuffle: Boolean): PlayRequest = PlayRequest(
        items = songs.map { it.toQueueItem() },
        startIndex = if (shuffle) songs.indices.random() else 0,
        context = PlaybackContext("section-$sectionTitle", sectionTitle, songs.firstOrNull()?.thumbnail, null, isCollection = false),
        shuffle = shuffle,
    )

    /** Rebuilds a saved playlist queue to resume it. */
    suspend fun forResume(state: ResumeState): Result<PlayRequest> = runCatching {
        fromEndpoint(
            WatchEndpoint(playlistId = state.playlistId),
            PlaybackContext(state.contextId, state.title, state.artworkUrl, state.playlistId, isCollection = true),
            shuffle = false,
        ).let { request ->
            // The saved spot may be past the first page: load up to it before resuming.
            val items = if (state.index >= request.items.size) request.items + (request.more?.invoke().orEmpty()) else request.items
            val rest = if (state.index >= request.items.size) null else request.more
            PlayRequest(items, state.index.coerceIn(0, (items.size - 1).coerceAtLeast(0)), request.context, false, rest)
        }
    }

    private suspend fun fromEndpoint(endpoint: WatchEndpoint, context: PlaybackContext, shuffle: Boolean): PlayRequest {
        // Start playing as soon as the first page arrives; fetch the rest afterwards.
        val first = YouTube.next(endpoint).getOrThrow()
        val songs = first.items.distinctBy { it.id }
        check(songs.isNotEmpty()) { "Nothing to play" }
        val ctx = context.copy(title = first.title ?: context.title)
        val seen = songs.map { it.id }.toMutableSet()
        val more: suspend () -> List<NowPlaying> = {
            val extra = mutableListOf<SongItem>()
            var continuation = first.continuation
            var pages = 0
            val limit = if (context.isCollection) COLLECTION_PAGES else RADIO_PAGES
            while (continuation != null && pages++ < limit) {
                val page = YouTube.next(endpoint, continuation).getOrNull() ?: break
                extra += page.items.filter { seen.add(it.id) }
                continuation = page.continuation
            }
            extra.map { it.toQueueItem() }
        }
        return PlayRequest(
            items = songs.map { it.toQueueItem() },
            startIndex = first.currentIndex?.coerceIn(0, songs.size - 1) ?: 0,
            context = ctx,
            shuffle = shuffle,
            more = if (first.continuation != null) more else null,
        )
    }
}
