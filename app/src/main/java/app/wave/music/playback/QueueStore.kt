package app.wave.music.playback

import android.content.Context
import androidx.media3.common.MediaItem
import org.json.JSONObject
import java.io.File

/**
 * The last queue, saved to the phone: the song list (queue.json, rewritten only when it changes)
 * and where you are in it (queue-state.json, tiny, saved often). Read by the app on start and by
 * the playback service to resume from the widget, lock screen or Pixel's media controls.
 */
object QueueStore {
    class Saved(
        val items: List<MediaItem>,
        val index: Int,
        val positionMs: Long,
        val shuffle: Boolean,
        val repeatMode: Int,
        /** Shuffled play order, so a restored shuffled queue plays in the same order. */
        val order: List<Int>?,
        val context: PlaybackContext?,
    )

    fun itemsFile(context: Context) = File(context.applicationContext.filesDir, "queue.json")
    fun stateFile(context: Context) = File(context.applicationContext.filesDir, "queue-state.json")

    fun read(context: Context): Saved? {
        val json = runCatching { JSONObject(itemsFile(context).readText()) }.getOrNull() ?: return null
        // Where you were lives in its own small file (older saves kept it in queue.json).
        val spot = runCatching { JSONObject(stateFile(context).readText()) }.getOrNull() ?: json
        val array = json.optJSONArray("items") ?: return null
        val items = (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            WaveService.mediaItem(
                videoId = o.getString("id"),
                title = o.optString("title"),
                artist = o.optString("artist"),
                artworkUrl = o.optString("art").ifBlank { null },
                album = o.optString("album").ifBlank { null },
                durationMs = o.optLong("duration", -1L).takeIf { it > 0 },
            )
        }
        if (items.isEmpty()) return null
        val order = spot.optJSONArray("order")?.let { a -> (0 until a.length()).map { a.getInt(it) } }
            ?.takeIf { it.size == items.size && it.toSet().size == items.size }
        val ctx = json.optJSONObject("context")?.let { o ->
            PlaybackContext(
                id = o.optString("id"),
                title = o.optString("title"),
                artworkUrl = o.optString("art").ifBlank { null },
                playlistId = o.optString("playlistId").ifBlank { null },
                isCollection = o.optBoolean("collection"),
            )
        }
        return Saved(
            items = items,
            index = spot.optInt("index").coerceIn(0, items.size - 1),
            positionMs = spot.optLong("position"),
            shuffle = spot.optBoolean("shuffle"),
            repeatMode = spot.optInt("repeat"),
            order = order,
            context = ctx,
        )
    }

    /** Sign-out: forget the last account's queue. */
    fun clear(context: Context) {
        runCatching { itemsFile(context).delete() }
        runCatching { stateFile(context).delete() }
    }
}
