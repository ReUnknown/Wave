package app.wave.music.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.pages.HomePage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** What sits at the very top of home, and why it was picked. */
data class HeroPick(val item: YTItem, val reason: HeroReason)

enum class HeroReason { NowPlaying, Unfinished, TopPick }

data class HomeFeed(
    val hero: HeroPick?,
    val recent: List<SongItem>,
    val sections: List<HomePage.Section>,
)

object HomeRepository {
    /** How many extra pages of home sections to pull after the first. */
    private const val EXTRA_PAGES = 2

    /** The last feed, so coming back to the app (or a rotated screen) shows home instantly. */
    @Volatile private var cached: Pair<Long, HomeFeed>? = null
    private const val FRESH_FOR_MS = 15 * 60_000L

    /**
     * Home, as fast as possible: a recent cached feed right away; otherwise the first page as soon
     * as it arrives (with history fetched alongside), then again once the extra pages are in.
     * Throws if even the first page fails.
     */
    fun feed(refresh: Boolean = false): Flow<HomeFeed> = flow {
        val recentCache = cached?.takeIf { !refresh && System.currentTimeMillis() - it.first < FRESH_FOR_MS }
        if (recentCache != null) {
            emit(recentCache.second)
            return@flow
        }
        coroutineScope {
            val historyCall = async { YouTube.musicHistory() }
            val first = YouTube.home().getOrThrow()
            val sections = first.sections.toMutableList()
            val recent = historyCall.await().getOrNull()
                ?.sections.orEmpty()
                .flatMap { it.songs }
                .filterNot(::isPodcast)
                .distinctBy { it.id }
                .take(12)
            emit(build(sections, recent))

            var continuation = first.continuation
            var added = false
            repeat(EXTRA_PAGES) {
                val next = continuation?.let { YouTube.home(continuation = it).getOrNull() } ?: return@repeat
                sections += next.sections
                continuation = next.continuation
                added = true
            }
            val full = build(sections, recent)
            cached = System.currentTimeMillis() to full
            if (added) emit(full)
        }
    }.flowOn(Dispatchers.IO)

    private fun build(sections: List<HomePage.Section>, recent: List<SongItem>): HomeFeed {
        val music = sections
            .map { section -> section.copy(items = section.items.filterNot(::isPodcast).distinctBy { it.id }) }
            .filter { it.items.isNotEmpty() }
            .distinctBy { it.title }
        return HomeFeed(hero = pickHero(music), recent = recent, sections = music)
    }

    /**
     * Priority: what's playing, then an unfinished playlist (both arrive with playback),
     * otherwise today's #1 recommendation: the first mix or playlist YouTube Music suggests.
     */
    private fun pickHero(sections: List<HomePage.Section>): HeroPick? {
        val items = sections.flatMap { it.items }
        val pick = items.firstOrNull { it is PlaylistItem }
            ?: items.firstOrNull { it is AlbumItem }
            ?: items.firstOrNull()
            ?: return null
        return HeroPick(pick, HeroReason.TopPick)
    }

    private fun isPodcast(item: YTItem): Boolean = when (item) {
        is PodcastItem, is EpisodeItem -> true
        is PlaylistItem -> item.isPodcast
        is SongItem -> item.isEpisode
        else -> false
    }
}
