package app.wave.music.data

import app.wave.music.playback.VideoTarget
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** A row of music videos. */
data class VideoShelf(val title: String, val label: String?, val videos: List<SongItem>)

fun SongItem.toVideoTarget() = VideoTarget(id, title, artists.joinToString { it.name }, app.wave.music.playback.Video.thumbnail(id))

/** Music videos picked for you: the artists you play, what's trending, and what's new. */
object VideoRepository {
    @Volatile private var cached: Pair<Long, List<VideoShelf>>? = null
    private const val FRESH_FOR_MS = 30 * 60_000L

    /** Music videos for [query] (official videos first, the way YouTube Music ranks them). */
    suspend fun search(query: String): List<SongItem> = withContext(Dispatchers.IO) {
        YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()?.items
            ?.filterIsInstance<SongItem>()?.distinctBy { it.id }.orEmpty()
    }

    /** Your artists, most played first: from what you played in Wave, then your home. */
    private fun yourArtists(): List<String> {
        val played = RecentPlays.songs.value.flatMap { s -> s.artists.map { it.name } }
        return played.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
            .filter { it.isNotBlank() && !it.equals("Various Artists", true) }
    }

    /** Every shelf on the Videos page. A shelf that fails is just left out. */
    suspend fun shelves(homeArtists: List<String> = emptyList(), refresh: Boolean = false): List<VideoShelf> {
        cached?.takeIf { !refresh && System.currentTimeMillis() - it.first < FRESH_FOR_MS }?.let { return it.second }
        val artists = (yourArtists() + homeArtists).distinct().take(6)
        val shelves = coroutineScope {
            val perArtist = artists.map { a -> async { runCatching { a to search("$a official music video").filter { v -> v.artists.any { it.name.equals(a, true) } }.take(10) }.getOrNull() } }
            val trending = async { runCatching { search("trending music videos this week").take(14) }.getOrDefault(emptyList()) }
            val fresh = async { runCatching { search("new music video ${java.time.Year.now().value}").take(14) }.getOrDefault(emptyList()) }
            val mine = perArtist.awaitAll().filterNotNull().filter { it.second.size >= 3 }
            // For you: the top two from each of your artists, taking turns, so it's not one artist's row.
            val forYou = (0 until 3).flatMap { round -> mine.mapNotNull { it.second.getOrNull(round) } }.distinctBy { it.id }
            buildList {
                if (forYou.isNotEmpty()) add(VideoShelf("Videos for you", "From artists you play", forYou))
                trending.await().takeIf { it.isNotEmpty() }?.let { add(VideoShelf("Trending now", "Popular this week", it)) }
                fresh.await().takeIf { it.isNotEmpty() }?.let { add(VideoShelf("New music videos", "Just out", it)) }
                mine.take(4).forEach { (artist, videos) -> add(VideoShelf("More from $artist", "Music videos", videos)) }
            }
        }
        if (shelves.isNotEmpty()) cached = System.currentTimeMillis() to shelves
        return shelves
    }

    /** What plays after [target]: more from the same artist, then what's on the Videos page. */
    suspend fun upNextFor(target: VideoTarget): List<VideoTarget> {
        val artist = target.artist.substringBefore(",").substringBefore(" & ").trim()
        val more = runCatching { search("$artist music video") }.getOrDefault(emptyList())
        return (more + cached?.second?.flatMap { it.videos }.orEmpty())
            .distinctBy { it.id }.filter { it.id != target.videoId }.take(20).map { it.toVideoTarget() }
    }
}
