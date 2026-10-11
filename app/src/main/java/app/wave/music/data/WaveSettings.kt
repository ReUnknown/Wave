package app.wave.music.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.wave.music.playback.AudioQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

data class Settings(
    /** The whole app takes on the colors of what's playing (off: your Pixel's colors). */
    val matchSongColors: Boolean = true,
    /** Shapes, waves and lyrics react to the beat. */
    val moveWithMusic: Boolean = true,
    val audioQuality: AudioQuality = AudioQuality.HIGH,
    val skipSilence: Boolean = false,
)

private val Context.settingsStore by preferencesDataStore("settings")

/** App settings, readable anywhere in the process (UI and playback service). */
object WaveSettings {
    private val matchColors = booleanPreferencesKey("match_song_colors")
    private val move = booleanPreferencesKey("move_with_music")
    private val quality = stringPreferencesKey("audio_quality")
    private val silence = booleanPreferencesKey("skip_silence")

    private val _state = MutableStateFlow(Settings())
    val state: StateFlow<Settings> = _state.asStateFlow()
    private var loaded = false

    suspend fun load(context: Context) {
        if (loaded) return
        val p = context.applicationContext.settingsStore.data.first()
        _state.value = Settings(
            matchSongColors = p[matchColors] ?: true,
            moveWithMusic = p[move] ?: true,
            audioQuality = p[quality]?.let { runCatching { AudioQuality.valueOf(it) }.getOrNull() } ?: AudioQuality.HIGH,
            skipSilence = p[silence] ?: false,
        )
        loaded = true
    }

    suspend fun update(context: Context, change: (Settings) -> Settings) {
        val next = change(_state.value)
        _state.value = next
        context.applicationContext.settingsStore.edit {
            it[matchColors] = next.matchSongColors
            it[move] = next.moveWithMusic
            it[quality] = next.audioQuality.name
            it[silence] = next.skipSilence
        }
    }
}
