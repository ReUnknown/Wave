package app.wave.music.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A YouTube Music (or YouTube) link opened in Wave, or shared to it. */
sealed interface Link {
    data class Song(val videoId: String) : Link
    data class Playlist(val id: String) : Link
    data class Album(val browseId: String) : Link
    data class Artist(val channelId: String) : Link
}

/** Links waiting for home to open them (Wave may still be starting up). */
object Links {
    private val _pending = MutableStateFlow<Link?>(null)
    val pending: StateFlow<Link?> = _pending.asStateFlow()

    /** Opens a link handed to Wave (tapped, shared to it, or passed on the command line). */
    fun offer(url: String) {
        parse(url)?.let { _pending.value = it }
    }

    /** Finds a YouTube Music link inside some shared text. */
    fun offerText(text: String) {
        Regex("""https?://\S+""").find(text)?.value?.let(::offer)
    }

    fun consume() {
        _pending.value = null
    }

    fun parse(url: String): Link? {
        val uri = runCatching { java.net.URI(url.trim()) }.getOrNull() ?: return null
        val host = uri.host?.removePrefix("www.")?.removePrefix("m.") ?: return null
        val path = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        val query = uri.rawQuery.orEmpty().split('&').mapNotNull { pair ->
            pair.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to java.net.URLDecoder.decode(it[1], "UTF-8") }
        }.toMap()
        if (host == "youtu.be") return path.lastOrNull()?.let(Link::Song)
        if (host != "youtube.com" && host != "music.youtube.com") return null
        return when (path.firstOrNull()) {
            "watch" -> query["v"]?.let(Link::Song)
                ?: query["list"]?.let { Link.Playlist(it) }
            "playlist" -> query["list"]?.let { Link.Playlist(it) }
            "browse" -> path.getOrNull(1)?.let { id ->
                when {
                    id.startsWith("MPREb") -> Link.Album(id)
                    id.startsWith("VL") -> Link.Playlist(id.removePrefix("VL"))
                    id.startsWith("UC") -> Link.Artist(id)
                    else -> null
                }
            }
            "channel" -> path.getOrNull(1)?.let(Link::Artist)
            else -> null
        }
    }
}
