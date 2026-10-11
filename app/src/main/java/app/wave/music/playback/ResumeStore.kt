package app.wave.music.playback

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Your place in the last playlist or album you played, so home can offer "Resume at song N". */
data class ResumeState(
    val contextId: String,
    val title: String,
    val artworkUrl: String?,
    val playlistId: String,
    val index: Int,
    val total: Int,
    val positionMs: Long,
    val currentSong: String?,
    val updatedAt: Long,
) {
    /** Unfinished = stopped before the last song, within the past week. */
    val isUnfinished: Boolean
        get() = index < total - 1 && System.currentTimeMillis() - updatedAt < 7L * 24 * 60 * 60 * 1000
}

private val Context.resumeStore by preferencesDataStore("resume")

object ResumeStore {
    private val contextId = stringPreferencesKey("context_id")
    private val title = stringPreferencesKey("title")
    private val artwork = stringPreferencesKey("artwork")
    private val playlistId = stringPreferencesKey("playlist_id")
    private val index = intPreferencesKey("index")
    private val total = intPreferencesKey("total")
    private val position = longPreferencesKey("position")
    private val song = stringPreferencesKey("song")
    private val updated = longPreferencesKey("updated")

    fun observe(context: Context): Flow<ResumeState?> = context.resumeStore.data.map { p ->
        val id = p[contextId] ?: return@map null
        val list = p[playlistId] ?: return@map null
        ResumeState(id, p[title].orEmpty(), p[artwork], list, p[index] ?: 0, p[total] ?: 0, p[position] ?: 0, p[song], p[updated] ?: 0)
    }

    suspend fun save(context: Context, state: ResumeState) {
        context.resumeStore.edit {
            it[contextId] = state.contextId
            it[title] = state.title
            state.artworkUrl?.let { url -> it[artwork] = url }
            it[playlistId] = state.playlistId
            it[index] = state.index
            it[total] = state.total
            it[position] = state.positionMs
            state.currentSong?.let { s -> it[song] = s }
            it[updated] = state.updatedAt
        }
    }
}
