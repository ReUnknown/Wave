package app.wave.music.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.first

/** Per-song lyric timing you set with the nudge buttons in Lyrics, remembered for next time. */
object LyricOffsets {
    private fun key(videoId: String) = intPreferencesKey("offset_$videoId")

    suspend fun get(videoId: String): Int = runCatching { Stores.prefs("lyrics").data.first()[key(videoId)] ?: 0 }.getOrDefault(0)

    suspend fun set(videoId: String, ms: Int) {
        runCatching {
            Stores.prefs("lyrics").edit { if (ms == 0) it.remove(key(videoId)) else it[key(videoId)] = ms }
        }
    }
}
