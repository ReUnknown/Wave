package app.wave.music.data

import com.metrolist.innertube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Which songs you've liked, shared by the app, the playback service (lock screen like button)
 * and the widget. Kept on the phone so it's there instantly, and re-read from your Liked music
 * playlist (up to 50 pages) when the copy is more than 6 hours old.
 */
object Likes {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()
    private const val FRESH_MS = 6 * 60 * 60_000L

    private val file: File get() = Stores.file("likes.txt")

    fun isLiked(videoId: String) = videoId in _ids.value

    suspend fun load() {
        val f = file
        val saved = withContext(Dispatchers.IO) { runCatching { f.readLines().filter { it.isNotBlank() } }.getOrNull() }
        if (saved != null) {
            _ids.value = _ids.value + saved
            if (System.currentTimeMillis() - f.lastModified() < FRESH_MS) return
        }
        // Guests: likes live on this device only.
        if (SessionRepository.isGuest) return
        val fresh = withContext(Dispatchers.IO) {
            runCatching {
                val first = YouTube.playlist("LM").getOrThrow()
                val all = first.songs.map { it.id }.toMutableList()
                var next = first.songsContinuation ?: first.continuation
                var pages = 0
                while (next != null && pages++ < 50) {
                    val more = YouTube.playlistContinuation(next).getOrNull() ?: break
                    all += more.songs.map { it.id }
                    next = more.continuation
                }
                all.toSet()
            }.getOrNull()
        } ?: return
        _ids.value = fresh
        save()
    }

    /** Likes or unlikes on YouTube Music, updating everywhere at once. */
    fun set(videoId: String, like: Boolean) {
        _ids.value = if (like) _ids.value + videoId else _ids.value - videoId
        save()
        if (!SessionRepository.isGuest) scope.launch { YouTube.likeVideo(videoId, like) }
    }

    private fun save() {
        val snapshot = _ids.value.toList()
        val f = file
        scope.launch { runCatching { f.writeText(snapshot.joinToString("\n")) } }
    }

    /** Sign-out: forget everything about the last account's likes. */
    fun clear() {
        _ids.value = emptySet()
        runCatching { file.delete() }
    }
}
