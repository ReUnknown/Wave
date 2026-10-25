package app.wave.music.playback

import app.wave.music.data.HomeRepository
import app.wave.music.data.LyricsRepository
import app.wave.music.data.RecentPlays
import app.wave.music.data.SessionRepository
import app.wave.music.data.SongLinks
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.WatchEndpoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Player chores every platform's player shares. */
object PlaybackSupport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Counts a play on your YouTube Music account, so it shows in History, "Recently played" and
     * shapes recommendations. Call once a song has played for a few seconds, like YouTube does.
     * The report needs a playback link made for your account (the audio itself may come from a
     * signed-out client), so it asks for one as YouTube Music on the web does.
     */
    suspend fun reportPlay(song: NowPlaying) {
        RecentPlays.add(song.videoId, song.title, song.artist, song.artworkUrl, song.album, song.durationMs)
        withContext(Dispatchers.IO) {
            if (!SessionRepository.restored.value) SessionRepository.restore()
            if (YouTube.cookie.isNullOrEmpty()) return@withContext
            val tracking = YouTube.player(song.videoId, client = com.metrolist.innertube.models.YouTubeClient.WEB_REMIX).getOrNull()
                ?.playbackTracking?.videostatsPlaybackUrl?.baseUrl ?: return@withContext
            YouTube.registerPlayback(playbackTracking = tracking).onSuccess { HomeRepository.clear() }
        }
    }

    /** Songs like [seedId] to keep the music going when the queue ends (none of the ones in [have]). */
    suspend fun similar(seedId: String, have: Set<String>): List<NowPlaying> = withContext(Dispatchers.IO) {
        runCatching {
            YouTube.next(WatchEndpoint(videoId = seedId, playlistId = "RDAMVM$seedId")).getOrThrow().items
                .filter { it.id !in have && !it.isVideoSong && !it.isEpisode }
                .distinctBy { it.id }
                .take(25)
                .map { with(QueueBuilder) { it.toQueueItem() } }
        }.getOrDefault(emptyList())
    }

    /** Gets lyrics and artist/album links ready before you look for them. */
    fun prefetch(song: NowPlaying) {
        SongLinks.prefetch(song.videoId)
        scope.launch { LyricsRepository.load(song.videoId, song.title, song.artist, song.durationMs ?: 0L, song.album) }
    }
}
