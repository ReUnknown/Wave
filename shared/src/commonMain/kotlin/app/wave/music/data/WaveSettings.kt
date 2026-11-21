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
    val moveWithMusic: Boolean = false,
    val audioQuality: AudioQuality = AudioQuality.AUTO,
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
    /** Computers: the mini player is WaveMini, a whole Wave the size of a phone, on top of everything. */
    val waveMini: Boolean = false,
    /** Computers: the "no longer updated" notice at the top of the window has been closed. */
    val desktopNoticeClosed: Boolean = false,
    /** Pure black behind everything in dark mode (saves battery on OLED screens). */
    val amoled: Boolean = false,
    /** Prisma: the modern look, or Legacy (a modern take on Wave 1.0's look). */
    val legacyUi: Boolean = false,
    /** Prisma: get around with the search bar at the top instead of the bar at the bottom. */
    val topSearch: Boolean = false,
    /** Prisma: the mini player floats on its own instead of joining the bar at the bottom. */
    val separateMiniPlayer: Boolean = false,
    /** Prisma: the first-run setup has been finished. */
    val setupDone: Boolean = false,
    /** 0 follow the phone, 1 light, 2 dark. */
    val theme: Int = 0,
    /** Background with song colors: 0 blurred cover, 1 a glow at the top, 2 plain. */
    val backdropStyle: Int = 0,
    /** Frosted glass: 0 off (solid), 1 light, 2 full. */
    val blurLevel: Int = 2,
    /** Corners: 0 rounded, 1 squircle, 2 sharp. */
    val cornerStyle: Int = 0,
    /** Text size across Wave, on top of the phone's own. */
    val textScale: Float = 1f,
    /** Home shows more at once, with smaller cards. */
    val denseHome: Boolean = false,
    /** Haptics: 0 on, 1 subtle, 2 off. */
    val haptics: Int = 0,
    /** The player's buttons beside play skip 10 seconds instead of songs. */
    val seekButtons: Boolean = false,
    /** The dock's mini player has previous and next beside play. */
    val dockSkip: Boolean = true,
    /** Where Wave opens: 0 Home, 1 Library, 2 Search. */
    val startTab: Int = 0,
    /** Most space saved songs may take, in MB (applies the next time Wave starts). */
    val songCacheMb: Int = 512,
    /** Most space saved covers may take, in MB (next start). */
    val coverCacheMb: Int = 200,
    /** With the phone's battery saver on: no blur, plain background, still waves. */
    val saverLook: Boolean = true,
    /** With the phone's battery saver on: lighter audio. */
    val saverQuality: Boolean = false,
    /** Fewer moving things all the time (still progress line, no crossfades). */
    val calmMotion: Boolean = false,
    /** One storage pool for saved songs and covers, in MB (next start). Songs get most of it. */
    val storageMb: Int = 256,
    /** On mobile data: lighter audio and smaller covers. */
    val saveData: Boolean = true,
    /** Tablets: the mini player sits beside the tabs in one row. */
    val dockOneRow: Boolean = true,
    /** Lyrics light up word by word (off: the whole line at once). */
    val lyricsWords: Boolean = true,
    /** Lyric motion: 0 still, 1 words lift as they're sung, 2 lift and held words glow. */
    val lyricsMotion: Int = 1,
    /** Lyrics size: 0 small, 1 medium, 2 large. */
    val lyricsSize: Int = 1,
    val lyricsCenter: Boolean = false,
    /** Lines away from the sung one blur. */
    val lyricsBlur: Boolean = true,
    /** Wave spots lyrics that are out of time with the song and lines them up by itself. */
    val lyricsAutoSync: Boolean = true,
    /** Has seen Wave 2.0's welcome (people coming from 1.x get the transform and the tour once). */
    val seenV2: Boolean = false,
    /** The update the one-time pop-up was shown for (it never shows twice for the same one). */
    val updatePopupShownFor: String = "",
    /** The update whose banner in Settings you closed. */
    val updateBannerClosedFor: String = "",
    /** Home's rows in order, each on (1) or off (0): see HomeRows. */
    val homeRows: String = HomeRows.DEFAULT,
    /** YouTube Music rows on home you've hidden, by name. */
    val hiddenSections: String = "",
    /** The version whose "What's new" was last shown (after updating, the new one's notes show once). */
    val lastSeenVersion: String = "",
)

/** Home's rows: which ones show, in what order. */
object HomeRows {
    const val DEFAULT = "picks:1,recent:1,playlists:1,feed:1,radio:1"
    val names = mapOf(
        "picks" to "Top picks for you",
        "recent" to "Recently played",
        "playlists" to "Your playlists",
        "feed" to "From YouTube Music",
        "radio" to "Radio for you",
    )

    /** Every row in order, with whether it's on (new rows Wave adds later show up at the end). */
    fun parse(value: String): List<Pair<String, Boolean>> {
        val listed = value.split(",").mapNotNull { part ->
            val (key, on) = part.split(":").let { it.getOrNull(0).orEmpty() to (it.getOrNull(1) != "0") }
            key.takeIf { it in names }?.let { it to on }
        }.distinctBy { it.first }
        return listed + names.keys.filter { k -> listed.none { it.first == k } }.map { it to true }
    }

    fun write(rows: List<Pair<String, Boolean>>): String = rows.joinToString(",") { "${it.first}:${if (it.second) 1 else 0}" }
}


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
    private val mini = booleanPreferencesKey("wave_mini")
    private val noticeClosed = booleanPreferencesKey("desktop_notice_closed")
    private val amoledKey = booleanPreferencesKey("amoled")
    private val legacyKey = booleanPreferencesKey("legacy_ui")
    private val topSearchKey = booleanPreferencesKey("top_search")
    private val separateKey = booleanPreferencesKey("separate_mini_player")
    private val setupKey = booleanPreferencesKey("setup_done")
    private val seenV2Key = booleanPreferencesKey("seen_v2")
    private val homeRowsKey = stringPreferencesKey("home_rows")
    private val hiddenSectionsKey = stringPreferencesKey("hidden_sections")
    private val lastSeenKey = stringPreferencesKey("last_seen_version")
    private val popupShownKey = stringPreferencesKey("update_popup_shown_for")
    private val bannerClosedKey = stringPreferencesKey("update_banner_closed_for")
    private val setupCompletedKey = booleanPreferencesKey("setup_completed")
    private val lyWordsKey = androidx.datastore.preferences.core.booleanPreferencesKey("lyrics_words")
    private val lyMotionKey = androidx.datastore.preferences.core.intPreferencesKey("lyrics_motion")
    private val lySizeKey = androidx.datastore.preferences.core.intPreferencesKey("lyrics_size")
    private val lyCenterKey = androidx.datastore.preferences.core.booleanPreferencesKey("lyrics_center")
    private val lyBlurKey = androidx.datastore.preferences.core.booleanPreferencesKey("lyrics_blur")
    private val lyAutoKey = androidx.datastore.preferences.core.booleanPreferencesKey("lyrics_auto_sync")
    private val storageKey = androidx.datastore.preferences.core.intPreferencesKey("storage_mb")
    private val saveDataKey = androidx.datastore.preferences.core.booleanPreferencesKey("save_data")
    private val oneRowKey = androidx.datastore.preferences.core.booleanPreferencesKey("dock_one_row")
    private val startTabKey = androidx.datastore.preferences.core.intPreferencesKey("start_tab")
    private val songCacheKey = androidx.datastore.preferences.core.intPreferencesKey("song_cache_mb")
    private val coverCacheKey = androidx.datastore.preferences.core.intPreferencesKey("cover_cache_mb")
    private val saverLookKey = androidx.datastore.preferences.core.booleanPreferencesKey("saver_look")
    private val saverQualityKey = androidx.datastore.preferences.core.booleanPreferencesKey("saver_quality")
    private val calmKey = androidx.datastore.preferences.core.booleanPreferencesKey("calm_motion")
    private val themeKey = androidx.datastore.preferences.core.intPreferencesKey("theme")
    private val backdropKey = androidx.datastore.preferences.core.intPreferencesKey("backdrop_style")
    private val blurKey = androidx.datastore.preferences.core.intPreferencesKey("blur_level")
    private val cornerKey = androidx.datastore.preferences.core.intPreferencesKey("corner_style")
    private val textKey = androidx.datastore.preferences.core.floatPreferencesKey("text_scale")
    private val denseKey = androidx.datastore.preferences.core.booleanPreferencesKey("dense_home")
    private val hapticsKey = androidx.datastore.preferences.core.intPreferencesKey("haptics")
    private val seekKey = androidx.datastore.preferences.core.booleanPreferencesKey("seek_buttons")
    private val dockSkipKey = androidx.datastore.preferences.core.booleanPreferencesKey("dock_skip")

    private val _state = MutableStateFlow(Settings())
    val state: StateFlow<Settings> = _state.asStateFlow()
    private var loaded = false
    /** True once the saved settings have been read (so first-run setup doesn't flash). */
    val ready = MutableStateFlow(false)

    suspend fun load() {
        if (loaded) return
        val p = Stores.prefs("settings").data.first()
        _state.value = Settings(
            matchSongColors = p[matchColors] ?: true,
            // Prisma has no beat-reactive motion, so the audio isn't analysed at all (saves CPU).
            moveWithMusic = false,
            audioQuality = p[quality]?.let { runCatching { AudioQuality.valueOf(it) }.getOrNull() } ?: AudioQuality.AUTO,
            skipSilence = p[silence] ?: false,
            autoplay = p[auto] ?: true,
            evenVolume = p[even] ?: true,
            crossfadeSeconds = p[crossfade] ?: 0,
            lowPowerPlayback = p[lowPower] ?: true,
            checkUpdates = p[updates] ?: true,
            splitPlayer = p[split] ?: false,
            appVolume = p[volume] ?: 1f,
            uiScale = p[scale] ?: 0.9f,
            waveMini = p[mini] ?: false,
            desktopNoticeClosed = p[noticeClosed] ?: false,
            amoled = p[amoledKey] ?: false,
            legacyUi = p[legacyKey] ?: false,
            topSearch = p[topSearchKey] ?: false,
            separateMiniPlayer = p[separateKey] ?: false,
            // Anyone already using Wave has effectively set it up.
            // Set up = finished the new setup. People coming from 1.x (settings, but nothing from 2.0)
            // get the welcome and transform instead; anyone on 2.0 who never went through setup
            // (older 2.0 builds) gets setup now.
            setupDone = p[setupCompletedKey] ?: run {
                val fromV1 = p[matchColors] != null && p[legacyKey] == null
                val finishedBefore = p[setupKey] == true && p[seenV2Key] == true
                fromV1 || finishedBefore
            },
            seenV2 = p[seenV2Key] ?: false,
            homeRows = p[homeRowsKey] ?: HomeRows.DEFAULT,
            hiddenSections = p[hiddenSectionsKey] ?: "",
            lastSeenVersion = p[lastSeenKey] ?: if (p[matchColors] != null) "earlier" else "",
            updatePopupShownFor = p[popupShownKey] ?: "",
            updateBannerClosedFor = p[bannerClosedKey] ?: "",
            lyricsWords = p[lyWordsKey] ?: true,
            lyricsMotion = p[lyMotionKey] ?: 1,
            lyricsSize = p[lySizeKey] ?: 1,
            lyricsCenter = p[lyCenterKey] ?: false,
            lyricsBlur = p[lyBlurKey] ?: true,
            lyricsAutoSync = p[lyAutoKey] ?: true,
            storageMb = p[storageKey] ?: 256,
            saveData = p[saveDataKey] ?: true,
            dockOneRow = p[oneRowKey] ?: true,
            startTab = p[startTabKey] ?: 0,
            songCacheMb = p[songCacheKey] ?: 512,
            coverCacheMb = p[coverCacheKey] ?: 200,
            saverLook = p[saverLookKey] ?: true,
            saverQuality = p[saverQualityKey] ?: false,
            calmMotion = p[calmKey] ?: false,
            theme = p[themeKey] ?: 0,
            backdropStyle = p[backdropKey] ?: 0,
            blurLevel = p[blurKey] ?: 2,
            cornerStyle = p[cornerKey] ?: 0,
            textScale = p[textKey] ?: 1f,
            denseHome = p[denseKey] ?: false,
            haptics = p[hapticsKey] ?: 0,
            seekButtons = p[seekKey] ?: false,
            dockSkip = p[dockSkipKey] ?: true,
        )
        loaded = true
        ready.value = true
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
            it[mini] = next.waveMini
            it[noticeClosed] = next.desktopNoticeClosed
            it[amoledKey] = next.amoled
            it[legacyKey] = next.legacyUi
            it[topSearchKey] = next.topSearch
            it[separateKey] = next.separateMiniPlayer
            it[setupKey] = next.setupDone
            it[seenV2Key] = next.seenV2
            it[homeRowsKey] = next.homeRows
            it[hiddenSectionsKey] = next.hiddenSections
            it[lastSeenKey] = next.lastSeenVersion
            it[popupShownKey] = next.updatePopupShownFor
            it[bannerClosedKey] = next.updateBannerClosedFor
            it[setupCompletedKey] = next.setupDone
            it[lyWordsKey] = next.lyricsWords
            it[lyMotionKey] = next.lyricsMotion
            it[lySizeKey] = next.lyricsSize
            it[lyCenterKey] = next.lyricsCenter
            it[lyBlurKey] = next.lyricsBlur
            it[lyAutoKey] = next.lyricsAutoSync
            it[storageKey] = next.storageMb
            it[saveDataKey] = next.saveData
            it[oneRowKey] = next.dockOneRow
            it[startTabKey] = next.startTab
            it[songCacheKey] = next.songCacheMb
            it[coverCacheKey] = next.coverCacheMb
            it[saverLookKey] = next.saverLook
            it[saverQualityKey] = next.saverQuality
            it[calmKey] = next.calmMotion
            it[themeKey] = next.theme
            it[backdropKey] = next.backdropStyle
            it[blurKey] = next.blurLevel
            it[cornerKey] = next.cornerStyle
            it[textKey] = next.textScale
            it[denseKey] = next.denseHome
            it[hapticsKey] = next.haptics
            it[seekKey] = next.seekButtons
            it[dockSkipKey] = next.dockSkip
        }
    }

    /** Back to how Wave came: every setting reset (setup runs again next time). */
    suspend fun reset() {
        _state.value = Settings()
        Stores.prefs("settings").edit { it.clear() }
    }

    /** Changes a setting for this session only (a slider mid-drag); [update] saves it. */
    fun preview(change: (Settings) -> Settings) {
        _state.value = change(_state.value)
    }
}
