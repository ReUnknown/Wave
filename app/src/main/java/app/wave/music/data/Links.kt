package app.wave.music.data

import android.content.Intent
import android.net.Uri
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

    /** Takes a link out of an incoming intent, if it carries one. */
    fun receive(intent: Intent?) {
        intent ?: return
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                ?.let { Regex("""https?://\S+""").find(it)?.value }
                ?.let(Uri::parse)
            else -> null
        } ?: return
        parse(uri)?.let { _pending.value = it }
    }

    fun consume() {
        _pending.value = null
    }

    fun parse(uri: Uri): Link? {
        val host = uri.host?.removePrefix("www.")?.removePrefix("m.") ?: return null
        if (host == "youtu.be") return uri.lastPathSegment?.let(Link::Song)
        if (host != "youtube.com" && host != "music.youtube.com") return null
        val path = uri.pathSegments
        return when (path.firstOrNull()) {
            "watch" -> uri.getQueryParameter("v")?.let(Link::Song)
                ?: uri.getQueryParameter("list")?.let { Link.Playlist(it) }
            "playlist" -> uri.getQueryParameter("list")?.let { Link.Playlist(it) }
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
