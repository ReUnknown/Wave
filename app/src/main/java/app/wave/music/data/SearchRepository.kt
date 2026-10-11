package app.wave.music.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class SearchTab(val label: String, val filter: YouTube.SearchFilter?) {
    Top("Top", null),
    Songs("Songs", YouTube.SearchFilter.FILTER_SONG),
    Artists("Artists", YouTube.SearchFilter.FILTER_ARTIST),
    Albums("Albums", YouTube.SearchFilter.FILTER_ALBUM),
    Playlists("Playlists", YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST),
}

data class SearchGroup(val title: String, val items: List<YTItem>)

/** What to show while typing: matching artists, songs and albums, plus query completions. */
data class Suggestions(val items: List<YTItem> = emptyList(), val queries: List<String> = emptyList())

private val Context.searchStore by preferencesDataStore("search")

/** YouTube Music search with podcasts filtered out, plus your recent searches. */
object SearchRepository {
    private val recentKey = stringPreferencesKey("recent")
    private const val SEPARATOR = "\u001F"

    suspend fun suggestions(query: String): Suggestions {
        val result = YouTube.searchSuggestions(query).getOrNull() ?: return Suggestions()
        return Suggestions(
            items = result.recommendedItems.filterNot(::isPodcast).distinctBy { it.id }.take(4),
            queries = result.queries.take(6),
        )
    }

    suspend fun search(query: String, tab: SearchTab): Result<List<SearchGroup>> = runCatching {
        if (tab.filter == null) {
            YouTube.searchSummary(query).getOrThrow().summaries
                .map { SearchGroup(it.title, it.items.filterNot(::isPodcast).distinctBy { item -> item.id }) }
                .filter { it.items.isNotEmpty() }
        } else {
            val items = YouTube.search(query, tab.filter).getOrThrow().items.filterNot(::isPodcast).distinctBy { it.id }
            listOf(SearchGroup(tab.label, items))
        }
    }

    fun recent(context: Context): Flow<List<String>> = context.searchStore.data.map { prefs ->
        prefs[recentKey]?.split(SEPARATOR)?.filter { it.isNotBlank() }.orEmpty()
    }

    suspend fun remember(context: Context, query: String) {
        context.searchStore.edit { prefs ->
            val list = prefs[recentKey]?.split(SEPARATOR).orEmpty().filter { it.isNotBlank() && !it.equals(query, ignoreCase = true) }
            prefs[recentKey] = (listOf(query) + list).take(10).joinToString(SEPARATOR)
        }
    }

    suspend fun clearRecent(context: Context) {
        context.searchStore.edit { it.clear() }
    }

    suspend fun forget(context: Context, query: String) {
        context.searchStore.edit { prefs ->
            prefs[recentKey] = prefs[recentKey]?.split(SEPARATOR).orEmpty().filter { it != query }.joinToString(SEPARATOR)
        }
    }

    private fun isPodcast(item: YTItem): Boolean = when (item) {
        is PodcastItem, is EpisodeItem -> true
        is PlaylistItem -> item.isPodcast
        // Music only: no music videos, episodes or user channels.
        is SongItem -> item.isEpisode || item.isVideoSong
        is com.metrolist.innertube.models.ArtistItem -> item.isProfile
        else -> false
    }
}
