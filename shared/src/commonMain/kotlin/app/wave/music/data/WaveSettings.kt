package app.wave.music.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
    /** When the queue runs out, keep going with songs like the last one. */
    val autoplay: Boolean = true,
    /** Turn down songs mastered louder than YouTube's reference, so everything plays at an even volume. */
    val evenVolume: Boolean = true,
    /** Seconds songs overlap as one fades into the next; 0 = off. */
    val crossfadeSeconds: Int = 0,
    /** In the background, let the phone's audio chip decode music (saves battery). */
    val lowPowerPlayback: Boolean = true,
    /** Look for a new version of Wave on GitHub once a day. */
    val checkUpdates: Boolean = true,
    /** Computers: the player opens beside the app (half the window) instead of filling it. */
    val splitPlayer: Boolean = false,
    /** Wave's own volume (0..1), separate from the device's, like YouTube's volume slider. */
    val appVolume: Float = 1f,
    /** Computers: how big Wave draws everything (1 = the system's own size). */
    val uiScale: Float = 0.9f,
)


/** App settings, readable anywhere in the process (UI and playback service). */
object WaveSettings {
    private val matchColors = booleanPreferencesKey("match_song_colors")
    private val move = booleanPreferencesKey("move_with_music")
    private val quality = stringPreferencesKey("audio_quality")
    private val silence = booleanPreferencesKey("skip_silence")
    private val auto = booleanPreferencesKey("autoplay")
    private val even = booleanPreferencesKey("even_volume")
    private val crossfade = androidx.datastore.preferences.core.intPreferencesKey("crossfade_seconds")
    private val lowPower = booleanPreferencesKey("low_power_playback")
    private val updates = booleanPreferencesKey("check_updates")
    private val split = booleanPreferencesKey("split_player")
    private val volume = androidx.datastore.preferences.core.floatPreferencesKey("app_volume")
    private val scale = androidx.datastore.preferences.core.floatPreferencesKey("ui_scale")

    private val _state = MutableStateFlow(Settings())
    val state: StateFlow<Settings> = _state.asStateFlow()
    private var loaded = false

    suspend fun load() {
        if (loaded) return
        val p = Stores.prefs("settings").data.first()
        _state.value = Settings(
            matchSongColors = p[matchColors] ?: true,
            moveWithMusic = p[move] ?: true,
            audioQuality = p[quality]?.let { runCatching { AudioQuality.valueOf(it) }.getOrNull() } ?: AudioQuality.HIGH,
            skipSilence = p[silence] ?: false,
            autoplay = p[auto] ?: true,
            evenVolume = p[even] ?: true,
            crossfadeSeconds = p[crossfade] ?: 0,
            lowPowerPlayback = p[lowPower] ?: true,
            checkUpdates = p[updates] ?: true,
            splitPlayer = p[split] ?: false,
            appVolume = p[volume] ?: 1f,
            uiScale = p[scale] ?: 0.9f,
        )
        loaded = true
    }

    suspend fun update(change: (Settings) -> Settings) {
        val next = change(_state.value)
        _state.value = next
        Stores.prefs("settings").edit {
            it[matchColors] = next.matchSongColors
            it[move] = next.moveWithMusic
            it[quality] = next.audioQuality.name
            it[silence] = next.skipSilence
            it[auto] = next.autoplay
            it[even] = next.evenVolume
            it[crossfade] = next.crossfadeSeconds
            it[lowPower] = next.lowPowerPlayback
            it[updates] = next.checkUpdates
            it[split] = next.splitPlayer
            it[volume] = next.appVolume
            it[scale] = next.uiScale
        }
    }

    /** Changes a setting for this session only (a slider mid-drag); [update] saves it. */
    fun preview(change: (Settings) -> Settings) {
        _state.value = change(_state.value)
    }
}
