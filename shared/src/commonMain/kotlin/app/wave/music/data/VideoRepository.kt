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

    private const val ATV = "MUSIC_VIDEO_TYPE_ATV"
    private const val OMV = "MUSIC_VIDEO_TYPE_OMV"

    /** A real video (not an album track's still cover, not a podcast episode). */
    fun isRealVideo(item: SongItem): Boolean = item.musicVideoType != ATV && item.musicVideoType?.contains("PODCAST") != true && !item.isEpisode

    private val searchCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<SongItem>>>()

    /**
     * Music videos for [query]: real videos only, official ones first, then the rest in YouTube's
     * order. Results are kept for a while, and a search that comes back empty is tried once more.
     */
    suspend fun search(query: String): List<SongItem> = withContext(Dispatchers.IO) {
        val key = query.trim().lowercase()
        searchCache[key]?.takeIf { System.currentTimeMillis() - it.first < FRESH_FOR_MS }?.let { return@withContext it.second }
        suspend fun once() = YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()?.items
            ?.filterIsInstance<SongItem>()?.filter(::isRealVideo)?.distinctBy { it.id }.orEmpty()
        val found = once().ifEmpty { kotlinx.coroutines.delay(300); once() }
        val ranked = found.sortedBy { if (it.musicVideoType == OMV) 0 else 1 }
        if (ranked.isNotEmpty()) searchCache[key] = System.currentTimeMillis() to ranked
        ranked
    }

    private fun words(s: String) = plain(s).split(" ").filter { it.length > 1 }.toSet()

    /** How alike two names are: the share of the song's words found in the video's title (0..1). */
    private fun overlap(song: String, video: String): Float {
        val a = words(song)
        if (a.isEmpty()) return 0f
        val b = words(video)
        return a.count { it in b }.toFloat() / a.size
    }

    private fun byArtist(v: SongItem, artist: String): Boolean {
        val who = plain(artist.substringBefore(",").substringBefore(" & ").substringBefore(" feat"))
        if (who.isBlank()) return false
        return v.artists.any { plain(it.name).let { n -> n.contains(who) || who.contains(n) && n.isNotBlank() } } || plain(v.title).contains(who)
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
            val perArtist = artists.map { a -> async { runCatching { a to search("$a official music video").filter { v -> byArtist(v, a) }.take(10) }.getOrNull() } }
            val trending = async { runCatching { search("trending music videos this week").take(14) }.getOrDefault(emptyList()) }
            val fresh = async { runCatching { search("new music video ${java.time.Year.now().value}").take(14) }.getOrDefault(emptyList()) }
            val mine = perArtist.awaitAll().filterNotNull().filter { it.second.size >= 2 }
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

    private val videoOf = java.util.concurrent.ConcurrentHashMap<String, String>()
    private const val NONE = "-"

    private fun plain(s: String) = s.lowercase().replace(Regex("\\(.*?\\)|\\[.*?]"), "").replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    /** One key for a song and its music video ("Blinding Lights (Official Video)" is "Blinding Lights"). */
    fun sameSong(title: String, artist: String): String =
        plain(title.replace(Regex("(?i)official|music video|lyric video|lyrics|video|audio|hd|4k"), "")) + "|" + plain(artist.substringBefore(",").substringBefore(" & "))

    /** Known already (no network): the video for [videoId], "" for none, or null when not checked yet. */
    fun knownVideoFor(videoId: String): String? = videoOf[videoId]?.let { if (it == NONE) "" else it }

    /**
     * The music video for a song, or null when it has none. A song that's a video already is its
     * own; an album track (audio only) gets its official video when one clearly matches.
     */
    suspend fun videoFor(videoId: String, title: String, artist: String): String? = withContext(Dispatchers.IO) {
        videoOf[videoId]?.let { return@withContext it.takeIf { v -> v != NONE } }
        val type = YouTube.player(videoId, client = com.metrolist.innertube.models.YouTubeClient.WEB_REMIX).getOrNull()?.videoDetails?.musicVideoType
        val found = when {
            type == null -> null
            type != com.metrolist.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.MUSIC_VIDEO_TYPE_ATV -> videoId
            else -> bestVideo(title, artist)
        }
        if (type != null) videoOf[videoId] = found ?: NONE
        found
    }

    /**
     * The music video for a song by name: every candidate is scored (the song's words in its title,
     * the right artist, an official video, not a cover / live / lyric version unless the song is) and
     * the best one wins if it scores well enough. Tries "official video" first, then just the name.
     */
    private suspend fun bestVideo(title: String, artist: String): String? {
        val songWords = plain(title)
        val unwanted = listOf("cover", "live", "lyric", "lyrics", "karaoke", "instrumental", "remix", "slowed", "sped up", "reaction", "8d", "nightcore")
            .filter { it !in songWords }
        fun score(v: SongItem): Float {
            val t = plain(v.title)
            var s = overlap(title, v.title) * 3f
            if (byArtist(v, artist)) s += 2f
            if (v.musicVideoType == OMV) s += 1.5f
            if (t.contains("official")) s += 0.5f
            if (unwanted.any { t.contains(it) }) s -= 2.5f
            return s
        }
        for (q in listOf("$title $artist official video", "$title $artist")) {
            // Only the song's own artist's videos (or an official video with the whole name): a fan's
            // upload of a classical piece isn't that song's music video.
            val best = search(q).take(8)
                .filter { byArtist(it, artist) || (it.musicVideoType == OMV && overlap(title, it.title) >= 0.99f) }
                .map { it to score(it) }.maxByOrNull { it.second }
            // The name mostly there and the artist right (or an official video with the full name).
            if (best != null && best.second >= 4.2f) return best.first.id
        }
        return null
    }

    /** What plays after [target]: more from the same artist, then what's on the Videos page. */
    suspend fun upNextFor(target: VideoTarget): List<VideoTarget> {
        val artist = target.artist.substringBefore(",").substringBefore(" & ").trim()
        val more = runCatching { search("$artist music video") }.getOrDefault(emptyList())
        return (more + cached?.second?.flatMap { it.videos }.orEmpty())
            .distinctBy { it.id }.filter { it.id != target.videoId }.take(20).map { it.toVideoTarget() }
    }
}
