package app.wave.music.data

import android.content.Context
import app.wave.music.playback.PlayerConnection
import app.wave.music.playback.ResumeStore
import app.wave.music.ui.detail.clearPageCache
import app.wave.music.widget.WaveWidget

/**
 * Signing out forgets everything tied to that account, so whoever signs in next starts clean:
 * the login, the queue and where you were, liked songs, recent searches, cached home and pages.
 * (Saved audio and covers aren't personal and stay, within their size limits.)
 */
suspend fun signOutAndForget(context: Context) {
    val app = context.applicationContext
    PlayerConnection.reset()
    Likes.clear(app)
    ResumeStore.clear(app)
    SearchRepository.clearRecent(app)
    HomeRepository.clear()
    RecentPlays.clear()
    LibraryRepository.clear()
    clearPageCache()
    WaveWidget.update(app, null)
    SessionRepository.signOut(app)
}
