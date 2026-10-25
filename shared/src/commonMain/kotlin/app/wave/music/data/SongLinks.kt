package app.wave.music.data


import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.util.concurrent.ConcurrentHashMap

/**
 * Each song's artist and album links (a song from the player or queue only knows its id and
 * names). Looked up in the background as songs come up, so a song's menu can go straight to its
 * artist or album without waiting.
 */
object SongLinks {
    private val known = Lru<String, SongItem>(300)
    private val pending = ConcurrentHashMap<String, Deferred<SongItem?>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun cached(videoId: String): SongItem? = known.get(videoId)

    /** Remembers a song whose links are already known (from a search, album or playlist). */
    fun remember(song: SongItem) {
        if (song.album != null && song.artists.any { it.id != null }) known.put(song.id, song)
    }

    fun prefetch(videoId: String) {
        lookup(videoId)
    }

    fun lookup(videoId: String): Deferred<SongItem?> {
        known.get(videoId)?.let { return CompletableDeferred(it) }
        return pending.getOrPut(videoId) {
            scope.async {
                try {
                    YouTube.next(WatchEndpoint(videoId = videoId)).getOrNull()
                        ?.items?.firstOrNull { it.id == videoId }
                        ?.also { known.put(videoId, it) }
                } finally {
                    pending.remove(videoId)
                }
            }
        }
    }
}
