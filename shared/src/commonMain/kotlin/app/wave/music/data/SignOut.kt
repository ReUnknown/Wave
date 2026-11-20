package app.wave.music.data

import androidx.datastore.preferences.core.edit
import app.wave.music.playback.PlayerConnection
import app.wave.music.playback.ResumeStore
import app.wave.music.ui.detail.clearPageCache

/**
 * Signing out forgets everything tied to that account, so whoever signs in next starts clean:
 * the login, the queue and where you were, liked songs, recent searches, cached home and pages.
 * (Saved audio and covers aren't personal and stay, within their size limits.)
 */
suspend fun signOutAndForget() {
    PlayerConnection.reset()
    Likes.clear()
    ResumeStore.clear()
    SearchRepository.clearRecent()
    HomeRepository.clear()
    RecentPlays.clear()
    LibraryRepository.clear()
    clearPageCache()
    SessionRepository.signOut()
}


/** Erase info: signs out, forgets everything Wave kept on this device, and resets every setting. */
suspend fun eraseEverything() {
    signOutAndForget()
    runCatching { Stores.prefs("search").edit { it.clear() } }
    WaveSettings.reset()
}
