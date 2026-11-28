package app.wave.music.ui.settings

import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.InvertColors
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.ViewCompact
import androidx.compose.material.icons.rounded.Wallpaper
import app.wave.music.ui.i18n.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import app.wave.music.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wave.music.data.SessionRepository
import app.wave.music.data.Settings
import app.wave.music.data.Updater
import app.wave.music.data.WaveSettings
import app.wave.music.platform.WavePlatform
import app.wave.music.platform.prismaDisplayFont
import app.wave.music.platform.prismaTextFont
import app.wave.music.platform.topInset
import app.wave.music.playback.AudioQuality
import app.wave.music.ui.components.LocalMiniPlayerSpace
import app.wave.music.ui.components.WaveMark
import app.wave.music.ui.components.WaveMotion
import app.wave.music.ui.components.blocksTouchesBelow
import app.wave.music.ui.components.pressable
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Prisma's settings: their own pages, like InnerTune's. Your account at the top, then the
 * sections in groups, each opening its own page. About has its own pages too.
 */

private const val GITHUB = "https://github.com/ReUnknown/Wave"

/** A settings page (and the extra pages under About). */
enum class SettingsSection(val id: String, val title: String, val summary: String, val icon: ImageVector) {
    Customize("customize", "Customize", "Modern or Legacy, layout, buttons, haptics", Icons.Rounded.Dashboard),
    Looks("looks", "Appearance", "Theme, background, blur, text size, colors", Icons.Rounded.Palette),
    Accessibility("accessibility", "Accessibility", "Contrast, text size, motion, TalkBack", Icons.Rounded.AccessibilityNew),
    Language("language", "Language", "The language Wave speaks", Icons.Rounded.Translate),
    Playback("playback", "Playback & sound", "Quality, fades, more songs when a queue ends", Icons.Rounded.MusicNote),
    Lyrics("lyrics", "Lyrics", "Style, motion, size, timing", Icons.Rounded.Lyrics),
    Storage("storage", "Storage", "Saved songs and covers, history, erase", Icons.Rounded.Storage),
    Battery("battery", "Battery", "Battery saver, calmer motion", Icons.Rounded.BatteryStd),
    About("about", "About", "Version, updates, what's new, credits", Icons.Rounded.Info),
    Credits("credits", "Attribution", "", Icons.Rounded.Favorite),
    WhatsNew("whatsnew", "What's new", "", Icons.Rounded.NewReleases),
    Update("update", "Update", "", Icons.Rounded.SystemUpdate),
    HomeRowsPage("homerows", "Home rows", "", Icons.Rounded.Home);

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Customize
        /** The ones listed on the main settings page, in groups. */
        val groups = listOf(
            "Make it yours" to listOf(Customize, Looks, Accessibility, Language),
            "Listening" to listOf(Playback, Lyrics),
            "This phone" to listOf(Storage, Battery),
            "Wave" to listOf(About),
        )
    }
}

private fun display(size: TextUnit, weight: FontWeight = FontWeight.Bold) = TextStyle(fontFamily = prismaDisplayFont, fontSize = size, fontWeight = weight)
private fun body(size: TextUnit, weight: FontWeight = FontWeight.Normal) = TextStyle(fontFamily = prismaTextFont, fontSize = size, fontWeight = weight)

/** A settings page: a back button and a big title, then [content]. A readable width on tablets. */
@Composable
private fun SettingsPageFrame(title: String, onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    val top = topInset()
    Box(Modifier.fillMaxSize().blocksTouchesBelow().background(c.background), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize(), contentPadding = PaddingValues(top = top + 8.dp, bottom = 40.dp + LocalMiniPlayerSpace.current)) {
            item {
                Box(
                    Modifier.padding(start = 12.dp).size(48.dp).pressable(CircleShape, c.onBackground.copy(alpha = 0.08f), pressedScale = 0.88f, onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = c.onBackground) }
            }
            if (title.isNotEmpty()) item { Text(title, Modifier.padding(start = 20.dp, top = 14.dp, bottom = 12.dp).semantics { heading() }, color = c.onBackground, style = display(34.sp).copy(letterSpacing = (-0.6).sp)) }
            content()
        }
    }
}

/** The main settings page: your account, then the sections in groups. */
@Composable
fun PrismaSettingsHome(onBack: () -> Unit, onSection: (SettingsSection) -> Unit, onSignOut: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val session by SessionRepository.session.collectAsState()
    SettingsPageFrame("Settings", onBack) {
        item {
            val s = session
            Row(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(c.surfaceContainerHigh)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (s?.photoUrl != null) {
                    AsyncImage(s.photoUrl, null, Modifier.size(56.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Rounded.AccountCircle, null, Modifier.size(56.dp), tint = c.onSurfaceVariant)
                }
                Column(Modifier.weight(1f)) {
                    Text(if (s == null || s.guest) "Not signed in" else s.name, color = c.onSurface, style = display(19.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (s == null || s.guest) "Sign in for your playlists and likes" else (s.email ?: "YouTube Music account"),
                        color = c.onSurfaceVariant, style = body(13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    Modifier.height(40.dp).clip(CircleShape).border(1.dp, c.outline, CircleShape).clickable(onClick = onSignOut).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(if (s == null || s.guest) "Sign in" else "Sign out", color = c.primary, style = body(14.sp, FontWeight.SemiBold)) }
            }
        }
        // A new version: a small banner you can close (it comes back for the next one).
        item {
            val settings by WaveSettings.state.collectAsState()
            val update by Updater.state.collectAsState()
            val release = when (val u = update) {
                is Updater.State.Available -> u.release
                is Updater.State.Downloading -> u.release
                is Updater.State.Ready -> u.release
                else -> null
            }
            val scope = rememberCoroutineScope()
            androidx.compose.animation.AnimatedVisibility(release != null && settings.updateBannerClosedFor != release.version) {
                val r = release ?: return@AnimatedVisibility
                Row(
                    Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(c.primaryContainer)
                        .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Rounded.SystemUpdate, null, tint = c.onPrimaryContainer)
                    Text("Wave ${r.version} is ready", Modifier.weight(1f), color = c.onPrimaryContainer, style = body(14.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(
                        Modifier.height(36.dp).pressable(CircleShape, c.primary, pressedScale = 0.94f) { onSection(SettingsSection.Update) }.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Update", color = c.onPrimary, style = body(13.sp, FontWeight.Bold)) }
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).clickable { scope.launch { WaveSettings.update { it.copy(updateBannerClosedFor = r.version) } } },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Close, "Close", Modifier.size(18.dp), tint = c.onPrimaryContainer) }
                }
            }
        }
        item { Box(Modifier.height(12.dp)) }
        // The same plain list as before, just in the new order.
        SettingsSection.groups.flatMap { it.second }.forEach { section ->
            item(key = section.id) { NavRow(section.icon, section.title, section.summary) { onSection(section) } }
        }
    }
}

/** One section's own page. [open] goes to another settings page (About's own pages). */
@Composable
fun PrismaSettingsPage(section: SettingsSection, onBack: () -> Unit, open: (SettingsSection) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val settings by WaveSettings.state.collectAsState()
    val c = MaterialTheme.colorScheme
    fun change(block: (Settings) -> Settings) {
        scope.launch { WaveSettings.update(block) }
    }
    val bigScreen = app.wave.music.platform.currentWindowDp().screenWidthDp >= 840
    if (section == SettingsSection.About) return AboutPage(onBack, open)
    if (section == SettingsSection.Credits) return CreditsPage(onBack)
    if (section == SettingsSection.WhatsNew) return WhatsNewPage(onBack)
    if (section == SettingsSection.Update) return UpdatePage(onBack)
    if (section == SettingsSection.HomeRowsPage) return HomeRowsEditor(onBack)
    SettingsPageFrame(section.title, onBack) {
        when (section) {
            SettingsSection.Customize -> {
                item { GroupLabel("Look") }
                item {
                    Choice(Icons.Rounded.AutoAwesome, "Style", "Modern is Wave 2.0; Legacy is a modern take on Wave 1.0", listOf(false to "Modern", true to "Legacy"), settings.legacyUi) { v -> change { it.copy(legacyUi = v) } }
                }
                item {
                    Choice(Icons.Rounded.RoundedCorner, "Corners", "How round cards, menus and the bar are", listOf(0 to "Rounded", 1 to "Squircle", 2 to "Sharp"), settings.cornerStyle) { v -> change { it.copy(cornerStyle = v) } }
                }
                item { Toggle(Icons.Rounded.ViewCompact, "Denser home", "Smaller cards, so you see more at once", settings.denseHome) { on -> change { it.copy(denseHome = on) } } }
                item { NavRow(Icons.Rounded.Home, "Home rows", "Pick and arrange what's on your home, with a preview") { open(SettingsSection.HomeRowsPage) } }
                item { GroupLabel("Getting around") }
                item {
                    Choice(Icons.Rounded.Explore, "Navigation", "The bar at the bottom, or a search bar at the top that also opens your library", listOf(false to "Bottom bar", true to "Top search"), settings.topSearch) { v -> change { it.copy(topSearch = v) } }
                }
                item {
                    Choice(Icons.Rounded.Home, "Open Wave on", "The tab you see first", listOf(0 to "Home", 1 to "Library", 2 to "Search"), settings.startTab) { v -> change { it.copy(startTab = v) } }
                }
                item { GroupLabel("Mini player and player") }
                item {
                    Toggle(
                        Icons.Rounded.PictureInPicture, "Mini player on its own",
                        if (settings.topSearch) "Always on its own with the top search" else "Floats above the bar instead of joining it",
                        settings.separateMiniPlayer || settings.topSearch,
                    ) { on -> change { it.copy(separateMiniPlayer = on) } }
                }
                if (bigScreen) item {
                    Toggle(Icons.Rounded.ViewCompact, "Mini player beside the tabs", "Big screens: one row at the bottom instead of two", settings.dockOneRow) { on -> change { it.copy(dockOneRow = on) } }
                }
                item { Toggle(Icons.Rounded.SkipNext, "Previous and next in the mini player", "Off leaves just the play button", settings.dockSkip) { on -> change { it.copy(dockSkip = on) } } }
                item {
                    Choice(Icons.Rounded.Forward10, "Buttons beside play", "In the player: skip songs, or jump 10 seconds", listOf(false to "Skip songs", true to "10 seconds"), settings.seekButtons) { v -> change { it.copy(seekButtons = v) } }
                }
                item { GroupLabel("Feel") }
                item {
                    Choice(Icons.Rounded.Vibration, "Haptics", "The little buzzes when you tap and drag", listOf(0 to "On", 1 to "Subtle", 2 to "Off"), settings.haptics) { v -> change { it.copy(haptics = v) } }
                }
            }
            SettingsSection.Looks -> {
                item {
                    Choice(Icons.Rounded.Contrast, "Theme", "Follow your phone, or always light or dark", listOf(0 to "System", 1 to "Light", 2 to "Dark"), settings.theme) { v -> change { it.copy(theme = v) } }
                }
                item { Toggle(Icons.Rounded.Palette, "Match song colors", "Wave takes on the colors of what's playing", settings.matchSongColors) { on -> change { it.copy(matchSongColors = on) } } }
                item {
                    Choice(Icons.Rounded.Wallpaper, "Background", "With song colors on: the cover blurred behind everything, a glow at the top, or plain", listOf(0 to "Cover", 1 to "Glow", 2 to "Plain"), settings.backdropStyle) { v -> change { it.copy(backdropStyle = v) } }
                }
                item {
                    Choice(Icons.Rounded.BlurOn, "Blur", "Frosted glass on the bar, menus and page tops", listOf(0 to "Off", 1 to "Light", 2 to "Full"), settings.blurLevel) { v -> change { it.copy(blurLevel = v) } }
                }
                item { Toggle(Icons.Rounded.DarkMode, "AMOLED black", "Pure black backgrounds in dark mode", settings.amoled) { on -> change { it.copy(amoled = on) } } }
                item {
                    Choice(Icons.Rounded.FormatSize, "Text size", "On top of your phone's own text size", listOf(0.9f to "Smaller", 1f to "Default", 1.12f to "Bigger"), settings.textScale) { v -> change { it.copy(textScale = v) } }
                }
            }
            SettingsSection.Accessibility -> {
                item { GroupLabel("Seeing") }
                item { Toggle(Icons.Rounded.Contrast, "High contrast", "Black or white backgrounds, stronger text and colors, and solid surfaces instead of frosted glass", settings.highContrast) { on -> change { it.copy(highContrast = on) } } }
                item { Toggle(Icons.Rounded.InvertColors, "Fix low contrast automatically", "When a song's colors would make text hard to read, Wave adjusts them", settings.autoContrast) { on -> change { it.copy(autoContrast = on) } } }
                item { GroupLabel("Text") }
                item {
                    Choice(Icons.Rounded.FormatSize, "Text size", "Bigger or smaller text all through Wave", listOf(0.9f to "Smaller", 1f to "Default", 1.15f to "Bigger", 1.3f to "Biggest"), settings.textScale) { v -> change { it.copy(textScale = v) } }
                }
                item { Toggle(Icons.Rounded.TextFields, "Follow your phone's text size", "Your phone's text size applies on top of Wave's", settings.followSystemText) { on -> change { it.copy(followSystemText = on) } } }
                item { GroupLabel("Motion") }
                item {
                    Toggle(
                        Icons.Rounded.SelfImprovement, "Reduce motion",
                        if (WavePlatform.current.systemReduceMotion) "On, because your phone is set to remove animations" else "No bounces, still waves and fewer moving things",
                        settings.reduceMotion || WavePlatform.current.systemReduceMotion,
                    ) { on -> change { it.copy(reduceMotion = on) } }
                }
                item { GroupLabel("TalkBack") }
                item {
                    val reader by WavePlatform.current.screenReader.collectAsState()
                    Toggle(
                        Icons.Rounded.RecordVoiceOver, "Keep controls on screen",
                        if (reader) "On while TalkBack is running: video controls never tuck themselves away" else "Video controls stay put instead of tucking away. Turns on by itself with TalkBack",
                        settings.keepControls || reader,
                    ) { on -> change { it.copy(keepControls = on) } }
                }
            }
            SettingsSection.Language -> {
                item {
                    Text(
                        "Wave is in English everywhere. Other languages are being filled in, so some text may still show in English.",
                        Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp),
                        color = c.onBackground.copy(alpha = 0.65f), style = body(14.sp).copy(lineHeight = 19.sp),
                    )
                }
                item { LanguageRow("", "Phone's language", app.wave.music.i18n.L10n.languages.firstOrNull { it.code == java.util.Locale.getDefault().language }?.name ?: "English", settings.language.isBlank()) { change { it.copy(language = "") } } }
                app.wave.music.i18n.L10n.languages.forEach { lang ->
                    item(key = "lang-" + lang.code) { LanguageRow(lang.code, lang.name, lang.english, settings.language == lang.code) { change { it.copy(language = lang.code) } } }
                }
            }
            SettingsSection.Playback -> {
                item { GroupLabel("Videos") }
                item { Toggle(Icons.Rounded.SmartDisplay, "Ambient mode", "Upright videos: the player's colors follow what's on screen as it plays", settings.videoAmbient) { on -> change { it.copy(videoAmbient = on) } } }
                item { GroupLabel("Sound") }
                item {
                    Choice(
                        Icons.Rounded.HighQuality, "Audio quality",
                        when (settings.audioQuality) {
                            AudioQuality.HIGH -> "The best Wave can get"
                            AudioQuality.AUTO -> "Best on Wi-Fi, lighter on mobile data"
                            AudioQuality.LOW -> "Uses the least data"
                        },
                        listOf(AudioQuality.LOW to "Low", AudioQuality.AUTO to "Auto", AudioQuality.HIGH to "High"), settings.audioQuality,
                    ) { q -> change { it.copy(audioQuality = q) } }
                }
                item {
                    Choice(
                        Icons.Rounded.SwapHoriz, "Fade between songs",
                        if (settings.crossfadeSeconds == 0) "Songs play one after another" else "Each song fades into the next over ${settings.crossfadeSeconds} seconds",
                        listOf(0 to "Off", 3 to "3s", 6 to "6s", 9 to "9s", 12 to "12s"), settings.crossfadeSeconds,
                    ) { s -> change { it.copy(crossfadeSeconds = s) } }
                }
                item { Toggle(Icons.AutoMirrored.Rounded.VolumeUp, "Even volume", "Loud songs are turned down so everything sounds the same", settings.evenVolume) { on -> change { it.copy(evenVolume = on) } } }
                item { GroupLabel("Queue") }
                item { Toggle(Icons.Rounded.PlayCircle, "Auto-load more songs", "When your queue ends, Wave keeps going with songs like the last one", settings.autoplay) { on -> change { it.copy(autoplay = on) } } }
                item { Toggle(Icons.Rounded.FastForward, "Skip silence", "Trims the quiet gaps in songs", settings.skipSilence) { on -> change { it.copy(skipSilence = on) } } }
            }
            SettingsSection.Lyrics -> {
                item {
                    Text(
                        "Wave checks each song's lyrics against the song and fixes the timing by itself when something's off.",
                        Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        color = c.onBackground.copy(alpha = 0.7f), style = body(14.sp).copy(lineHeight = 20.sp),
                    )
                }
                item { GroupLabel("Look") }
                item { Choice(Icons.Rounded.Lyrics, "Light up", "Word by word as they're sung, or the whole line at once", listOf(true to "Word by word", false to "Line by line"), settings.lyricsWords) { v -> change { it.copy(lyricsWords = v) } } }
                item { Choice(Icons.Rounded.AutoAwesome, "Motion", "Words lift gently as they're sung; held notes can glow too", listOf(0 to "Still", 1 to "Lift", 2 to "Lift & glow"), settings.lyricsMotion) { v -> change { it.copy(lyricsMotion = v) } } }
                item { Choice(Icons.Rounded.FormatSize, "Size", "How big the lines are", listOf(0 to "Small", 1 to "Medium", 2 to "Large"), settings.lyricsSize) { v -> change { it.copy(lyricsSize = v) } } }
                item { Choice(Icons.AutoMirrored.Rounded.FormatAlignLeft, "Alignment", "Lines on the left, or centered", listOf(false to "Left", true to "Center"), settings.lyricsCenter) { v -> change { it.copy(lyricsCenter = v) } } }
                item { Toggle(Icons.Rounded.BlurOn, "Blur other lines", "Lines away from the one being sung soften, so it stands out", settings.lyricsBlur) { on -> change { it.copy(lyricsBlur = on) } } }
                item { GroupLabel("Timing") }
                item { Toggle(Icons.Rounded.Timer, "Fix timing automatically", "When lyrics run early or late, Wave lines them up with the song. Off: exactly as the lyrics source times them", settings.lyricsAutoSync) { on -> change { it.copy(lyricsAutoSync = on) } } }
            }
            SettingsSection.Storage -> storagePage(settings, ::change)
            SettingsSection.Battery -> {
                item {
                    val saving by WavePlatform.current.powerSave.collectAsState()
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Tile(Icons.Rounded.BatterySaver)
                        Text(
                            if (saving) "Battery saver is on. Wave is going easy on your battery." else "Battery saver is off. These kick in when you turn it on.",
                            color = c.onBackground.copy(alpha = 0.75f), style = body(14.sp),
                        )
                    }
                }
                item { GroupLabel("When battery saver is on") }
                item { Toggle(Icons.Rounded.BlurOn, "Simpler look", "No blur or cover background, and the progress line holds still", settings.saverLook) { on -> change { it.copy(saverLook = on) } } }
                item { Toggle(Icons.Rounded.HighQuality, "Lighter audio", "Plays lower quality, which takes less work and data", settings.saverQuality) { on -> change { it.copy(saverQuality = on) } } }
                item { GroupLabel("All the time") }
                if (!WavePlatform.current.isComputer) {
                    item { Toggle(Icons.Rounded.BatterySaver, "Save battery while playing", "The phone's audio chip plays when Wave isn't on screen (off with Skip silence)", settings.lowPowerPlayback) { on -> change { it.copy(lowPowerPlayback = on) } } }
                }
                item { Toggle(Icons.Rounded.SelfImprovement, "Calm motion", "Fewer moving things, like the wavy progress line", settings.calmMotion) { on -> change { it.copy(calmMotion = on) } } }
            }
            SettingsSection.About, SettingsSection.Credits, SettingsSection.WhatsNew, SettingsSection.Update, SettingsSection.HomeRowsPage -> Unit
        }
    }
}

/** Storage: what saved songs and covers take, their limits, and clearing things. */
private fun LazyListScope.storagePage(settings: Settings, change: ((Settings) -> Settings) -> Unit) {
    item {
        val c = MaterialTheme.colorScheme
        val scope = rememberCoroutineScope()
        var refresh by remember { mutableIntStateOf(0) }
        var usage by remember { mutableStateOf(0L to 0L) }
        LaunchedEffect(refresh) { usage = withContext(Dispatchers.IO) { WavePlatform.current.storageUsage() } }
        Column {
            GroupLabel("Space")
            UsageBar(Icons.Rounded.Storage, "Saved songs and covers", usage.first + usage.second, settings.storageMb)
            StoragePool(settings.storageMb) { v -> change { it.copy(storageMb = v) } }
            ActionRow(Icons.Rounded.DeleteForever, "Clear saved songs and covers", "They load again when you need them") {
                scope.launch { withContext(Dispatchers.IO) { WavePlatform.current.clearSongCache(); WavePlatform.current.clearCoverCache() }; refresh++ }
            }
            GroupLabel("Mobile data")
            Toggle(Icons.Rounded.DataSaverOn, "Save mobile data", "On mobile data, Wave plays lighter audio and loads smaller covers", settings.saveData) { on -> change { it.copy(saveData = on) } }
            GroupLabel("History")
            ActionRow(Icons.Rounded.History, "Clear search history", "Your recent searches") {
                scope.launch {
                    app.wave.music.data.clearSearchHistory()
                    app.wave.music.ui.components.Notices.show("Search history cleared")
                }
            }
            ActionRow(Icons.Rounded.Autorenew, "Clear saved pages", "Home, albums and artists load fresh") {
                app.wave.music.ui.detail.clearPageCache()
                app.wave.music.ui.components.Notices.show("Saved pages cleared")
            }
            GroupLabel("Everything")
            var asking by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().clickable { asking = true }.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(c.errorContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.DeleteForever, null, tint = c.onErrorContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text("Erase info", color = c.error, style = display(17.sp, FontWeight.SemiBold))
                    Text("Signs you out and resets Wave as if it were just installed", color = c.onBackground.copy(alpha = 0.6f), style = body(13.sp))
                }
            }
            if (asking) androidx.compose.material3.AlertDialog(
                onDismissRequest = { asking = false },
                icon = { Icon(Icons.Rounded.DeleteForever, null) },
                title = { Text("Erase everything?") },
                text = { Text("You'll be signed out, and your settings, searches, history and saved pages are cleared from this phone. Your YouTube Music account isn't touched.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        asking = false
                        scope.launch {
                            withContext(Dispatchers.IO) { WavePlatform.current.clearSongCache(); WavePlatform.current.clearCoverCache() }
                            app.wave.music.data.eraseEverything()
                        }
                    }) { Text("Erase", color = c.error) }
                },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { asking = false }) { Text("Cancel") } },
            )
        }
    }
}

private fun mb(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    else -> "${bytes / (1024 * 1024)} MB"
}

@Composable
private fun UsageBar(icon: ImageVector, title: String, used: Long, limitMb: Int) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Tile(icon)
        Column(Modifier.weight(1f)) {
            Text(title, color = c.onBackground, style = display(17.sp, FontWeight.SemiBold))
            Text("${mb(used)} of ${mb(limitMb * 1024L * 1024)}", color = c.onBackground.copy(alpha = 0.6f), style = body(13.sp))
            LinearProgressIndicator(progress = { (used / (limitMb * 1024f * 1024f)).coerceIn(0f, 1f) }, modifier = Modifier.padding(top = 8.dp).fillMaxWidth())
        }
    }
}

/** About, like InnerTune's: the Wave icon, name, version and GitHub, then updates and pages. */
@Composable
private fun AboutPage(onBack: () -> Unit, open: (SettingsSection) -> Unit) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val settings by WaveSettings.state.collectAsState()
    val update by Updater.state.collectAsState()
    SettingsPageFrame("", onBack) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // Just the Wave mark, white on the page (dark in light mode).
                WaveMark(c.onBackground, size = 96.dp)
                Text("Wave", Modifier.padding(top = 16.dp), color = c.onBackground, style = display(32.sp))
                Text("Version ${WavePlatform.current.appVersion}", color = c.onBackground.copy(alpha = 0.65f), style = body(15.sp))
                Row(
                    Modifier.padding(top = 14.dp).height(42.dp).pressable(CircleShape, c.onBackground.copy(alpha = 0.08f), pressedScale = 0.94f) { WavePlatform.current.openUrl(GITHUB) }.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Code, null, Modifier.size(20.dp), tint = c.onBackground)
                    Text("  GitHub", color = c.onBackground, style = body(14.sp, FontWeight.SemiBold))
                }
            }
        }
        // A new version: a card with a peek at what's in it, opening the full preview.
        val found = when (val u = update) {
            is Updater.State.Available -> u.release
            is Updater.State.Downloading -> u.release
            is Updater.State.Ready -> u.release
            else -> null
        }
        if (found != null) item { UpdateCard(found) { open(SettingsSection.Update) } }
        item {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Tile(Icons.Rounded.SystemUpdate)
                    Column(Modifier.weight(1f)) {
                        Text("Updates", color = c.onBackground, style = display(17.sp, FontWeight.SemiBold))
                        Text(
                            when (val u = update) {
                                Updater.State.Idle -> "Tap Check to look for a new version"
                                Updater.State.Checking -> "Checking…"
                                Updater.State.UpToDate -> "You're up to date"
                                Updater.State.Failed -> "Couldn't check right now"
                                is Updater.State.Available -> "Wave ${u.release.version} is ready to download"
                                is Updater.State.Downloading -> "Downloading Wave ${u.release.version}…"
                                is Updater.State.Ready -> "Wave ${u.release.version} is ready to install"
                            },
                            color = c.onBackground.copy(alpha = 0.6f), style = body(13.sp),
                        )
                    }
                    val (label, action) = when (val u = update) {
                        is Updater.State.Available -> "Update" to { scope.launch { Updater.download(u.release) }; Unit }
                        is Updater.State.Ready -> "Install" to { Updater.install(u.file) }
                        is Updater.State.Downloading, Updater.State.Checking -> null to null
                        else -> "Check" to { scope.launch { Updater.check(force = true) }; Unit }
                    }
                    if (label != null && action != null) {
                        Box(
                            Modifier.height(40.dp).pressable(CircleShape, c.primary, pressedScale = 0.92f, onClick = action).padding(horizontal = 18.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(label, color = c.onPrimary, style = body(14.sp, FontWeight.SemiBold)) }
                    }
                }
                (update as? Updater.State.Downloading)?.let { u ->
                    LinearProgressIndicator(progress = { u.progress }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
                }
                Toggle(Icons.Rounded.Autorenew, "Update automatically", "Wave looks for a new version once a day", settings.checkUpdates) { on -> scope.launch { WaveSettings.update { it.copy(checkUpdates = on) } } }
            }
        }
        item { Box(Modifier.height(12.dp)) }
        item {
            Column(Modifier.fillMaxWidth()) {
                NavRow(Icons.Rounded.Explore, "Show me around", "The quick tour of Wave again") {
                    app.wave.music.ui.home.Tour.requested = true
                }
                NavRow(Icons.Rounded.NewReleases, "What's new", "Every release, newest first, straight from GitHub") { open(SettingsSection.WhatsNew) }
                NavRow(Icons.Rounded.Favorite, "Attribution", "What Wave is built on and inspired by") { open(SettingsSection.Credits) }
                NavRow(Icons.Rounded.BugReport, "Report a bug", "Opens a new issue on GitHub", external = true) { WavePlatform.current.openUrl("$GITHUB/issues/new") }
                var goingBack by remember { mutableStateOf(false) }
                NavRow(Icons.Rounded.History, "Go back to Wave 1.x", "How to switch back to the finished version") { goingBack = true }
                if (goingBack) androidx.compose.material3.AlertDialog(
                    onDismissRequest = { goingBack = false },
                    icon = { Icon(Icons.Rounded.History, null) },
                    title = { Text("Going back to Wave 1.x") },
                    text = {
                        Text(
                            "Android can't step an app back a version, so it's a quick reinstall:\n\n" +
                                "1. Uninstall Wave (your likes and playlists live in your YouTube Music account, so they're safe; Wave's own settings and saved songs are cleared).\n" +
                                "2. Download the latest Wave 1.x APK from GitHub and open it.\n" +
                                "3. Sign in again.\n\n" +
                                "Wave 1.x only offers finished versions, so you'll stay on it until you choose otherwise.",
                        )
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            goingBack = false
                            WavePlatform.current.openUrl("$GITHUB/releases/latest")
                        }) { Text("Get Wave 1.x") }
                    },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { goingBack = false }) { Text("Stay on 2.0") } },
                )
            }
        }
        item {
            Text(
                "Wave is free software under the GNU GPL v3. It isn't affiliated with or endorsed by YouTube or Google.",
                Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 20.dp),
                color = c.onBackground.copy(alpha = 0.5f), style = body(12.sp).copy(textAlign = TextAlign.Center),
            )
        }
    }
}

private class Credit(val name: String, val what: String, val url: String)

private val builtOn = listOf(
    Credit("Metrolist InnerTube & InnerTubeX", "How Wave talks to YouTube Music (GPL v3)", "https://github.com/MetrolistGroup/Metrolist"),
    Credit("Compose Multiplatform & Jetpack Compose", "Wave's whole interface (Apache 2.0)", "https://www.jetbrains.com/compose-multiplatform/"),
    Credit("Material 3", "Components, shapes and motion (Apache 2.0)", "https://m3.material.io"),
    Credit("Media3 / ExoPlayer", "Playback and the notification controls (Apache 2.0)", "https://github.com/androidx/media"),
    Credit("Haze", "The frosted glass, by Chris Banes (Apache 2.0)", "https://github.com/chrisbanes/haze"),
    Credit("Coil", "Loading covers (Apache 2.0)", "https://github.com/coil-kt/coil"),
    Credit("Ktor", "Networking (Apache 2.0)", "https://ktor.io"),
    Credit("Reorderable", "Drag to reorder Up next, by Calvin Liang (Apache 2.0)", "https://github.com/Calvin-LL/Reorderable"),
    Credit("LRCLIB", "Time-synced lyrics", "https://lrclib.net"),
    Credit("Better Lyrics", "Word-by-word lyrics", "https://github.com/better-lyrics"),
)

@Composable
private fun CreditsPage(onBack: () -> Unit) {
    SettingsPageFrame("Attribution", onBack) {
        builtOn.forEach { credit -> item(key = credit.name) { CreditRow(credit) } }
        item {
            Text(
                "Thank you to everyone who works on these. Wave isn't affiliated with any of them.",
                Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f), style = body(13.sp),
            )
        }
    }
}

@Composable
private fun CreditRow(credit: Credit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable { WavePlatform.current.openUrl(credit.url) }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(credit.name, color = c.onBackground, style = display(16.sp, FontWeight.SemiBold))
            Text(credit.what, color = c.onBackground.copy(alpha = 0.6f), style = body(13.sp))
        }
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp), tint = c.onBackground.copy(alpha = 0.45f))
    }
}

@Composable
private fun WhatsNewPage(onBack: () -> Unit) {
    // Straight from GitHub: every release, newer ones you don't have yet included.
    val c = MaterialTheme.colorScheme
    val current = WavePlatform.current.appVersion
    var notes by remember { mutableStateOf<List<Updater.Notes>?>(null) }
    LaunchedEffect(Unit) { notes = Updater.allNotes() }
    SettingsPageFrame("What's new", onBack) {
        val list = notes
        when {
            list == null -> item {
                Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            }
            list.isEmpty() -> item {
                Text("Couldn't reach GitHub. Check your connection and try again.", Modifier.padding(20.dp), color = c.onBackground.copy(alpha = 0.7f), style = body(15.sp))
            }
            else -> list.forEachIndexed { i, v ->
                val installed = Updater.compareVersions(v.version, current) == 0
                val newer = Updater.compareVersions(v.version, current) > 0
                item(key = v.version + i) {
                    val appear = remember { androidx.compose.animation.core.Animatable(0f) }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay((i * 40L).coerceAtMost(400L))
                        appear.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 300f))
                    }
                    Column(
                        Modifier
                            .graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * 16.dp.toPx() }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (installed) c.primaryContainer else c.surfaceContainer)
                            .clickable { WavePlatform.current.openUrl(v.pageUrl) }
                            .padding(18.dp),
                    ) {
                        val ink = if (installed) c.onPrimaryContainer else c.onSurface
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(v.title, Modifier.weight(1f), color = ink, style = display(18.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            when {
                                installed -> Tag("You have this", c.primary, c.onPrimary)
                                newer -> Tag("New", c.tertiary, c.onTertiary)
                                v.beta -> Tag("Beta", c.secondaryContainer, c.onSecondaryContainer)
                            }
                        }
                        Text(v.date, Modifier.padding(top = 2.dp), color = ink.copy(alpha = 0.6f), style = body(12.sp))
                        parseNotes(v.body).take(10).forEach { line ->
                            when (line) {
                                is NoteLine.Heading -> Text(line.text, Modifier.padding(top = 10.dp), color = ink, style = display(15.sp))
                                is NoteLine.Bullet -> Text("•  ${line.text}", Modifier.padding(top = 5.dp), color = ink.copy(alpha = 0.8f), style = body(13.sp).copy(lineHeight = 18.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                                is NoteLine.Plain -> Text(line.text, Modifier.padding(top = 5.dp), color = ink.copy(alpha = 0.75f), style = body(13.sp).copy(lineHeight = 18.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String, fill: androidx.compose.ui.graphics.Color, ink: androidx.compose.ui.graphics.Color) {
    Box(Modifier.clip(CircleShape).background(fill).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, color = ink, style = body(11.sp, FontWeight.Bold))
    }
}

/** One language: its own name, what it is in English (left as is, never translated), and a check. */
@Composable
private fun LanguageRow(code: String, name: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(if (selected) c.primary else c.secondaryContainer), contentAlignment = Alignment.Center) {
            if (code.isBlank()) Icon(Icons.Rounded.PhoneAndroid, null, tint = if (selected) c.onPrimary else c.onSecondaryContainer)
            else androidx.compose.material3.Text(code.uppercase(), color = if (selected) c.onPrimary else c.onSecondaryContainer, style = display(15.sp))
        }
        Column(Modifier.weight(1f)) {
            // A language's name is shown in that language, whatever Wave is in.
            if (code.isBlank()) Text(name, color = c.onBackground, style = display(17.sp, FontWeight.SemiBold))
            else androidx.compose.material3.Text(name, color = c.onBackground, style = display(17.sp, FontWeight.SemiBold))
            androidx.compose.material3.Text(sub, color = c.onBackground.copy(alpha = 0.6f), style = body(13.sp))
        }
        if (selected) Icon(Icons.Rounded.Check, null, tint = c.primary)
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(),
        Modifier.padding(start = 24.dp, end = 20.dp, top = 22.dp, bottom = 8.dp).semantics { heading() },
        color = MaterialTheme.colorScheme.primary,
        style = body(12.sp, FontWeight.Bold).copy(letterSpacing = 0.8.sp),
    )
}

@Composable
private fun NavRow(icon: ImageVector, title: String, summary: String, external: Boolean = false, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Tile(icon)
        Column(Modifier.weight(1f)) {
            Text(title, color = c.onBackground, style = display(17.sp, FontWeight.SemiBold))
            if (summary.isNotEmpty()) Text(summary, color = c.onBackground.copy(alpha = 0.6f), style = body(13.sp))
        }
        Icon(if (external) Icons.AutoMirrored.Rounded.OpenInNew else Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.onBackground.copy(alpha = 0.5f))
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Tile(icon)
        Column(Modifier.weight(1f)) {
            Text(title, color = c.onBackground, style = display(17.sp, FontWeight.SemiBold))
            Text(description, color = c.onBackground.copy(alpha = 0.6f), style = body(13.sp))
        }
    }
}

@Composable
private fun Tile(icon: ImageVector) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(c.secondaryContainer), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = c.onSecondaryContainer)
    }
}

@Composable
private fun Toggle(icon: ImageVector, title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Tile(icon)
        Column(Modifier.weight(1f)) {
            Text(title, color = c.onBackground, style = display(17.sp, FontWeight.SemiBold))
            Text(description, color = c.onBackground.copy(alpha = 0.6f), style = body(13.sp).copy(lineHeight = 17.sp))
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> Choice(icon: ImageVector, title: String, description: String, options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Tile(icon)
            Column(Modifier.weight(1f)) {
                Text(title, color = c.onBackground, style = display(17.sp, FontWeight.SemiBold))
                Text(description, color = c.onBackground.copy(alpha = 0.6f), style = body(13.sp))
            }
        }
        Row(Modifier.padding(top = 12.dp).fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            options.forEachIndexed { i, (value, label) ->
                val on = value == selected
                val outer by animateDpAsState(if (on) 22.dp else 8.dp, WaveMotion.bouncy(), label = "segment")
                val start = if (i == 0) 22.dp else outer
                val end = if (i == options.lastIndex) 22.dp else outer
                val fill by animateColorAsState(if (on) c.primary else c.surfaceContainerHighest, tween(160), label = "segmentFill")
                val ink by animateColorAsState(if (on) c.onPrimary else c.onSurface, tween(160), label = "segmentInk")
                Box(
                    Modifier.weight(1f).height(44.dp).pressable(RoundedCornerShape(start, end, end, start), fill, pressedScale = 0.94f) { onPick(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    // Four choices (or a long language) get smaller text, so no word is cut off.
                    Text(label, Modifier.padding(horizontal = 4.dp), color = ink, style = body(if (options.size >= 4) 12.sp else 14.sp, FontWeight.SemiBold), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}


/** A release's notes, read from its markdown: headings, bullet points and plain lines. */
private sealed interface NoteLine {
    class Heading(val text: String) : NoteLine
    class Bullet(val text: String) : NoteLine
    class Plain(val text: String) : NoteLine
}

/** Release notes as Wave shows them: without the "Installing by hand" part (that's for GitHub). */
internal fun appNotes(notes: String): String = notes.split(Regex("(?im)^#+\\s*installing by hand.*$")).first().trim()

private fun parseNotes(notes: String): List<NoteLine> = appNotes(notes).lines().mapNotNull { raw ->
    val line = raw.trim().replace("**", "").replace("__", "")
    when {
        line.isBlank() -> null
        line.startsWith("#") -> NoteLine.Heading(line.trimStart('#').trim())
        line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ") -> NoteLine.Bullet(line.drop(2).trim())
        else -> NoteLine.Plain(line)
    }
}

/** The Wave mark, gently rising and falling, for the update screens. */
@Composable
private fun FloatingMark(color: androidx.compose.ui.graphics.Color, size: androidx.compose.ui.unit.Dp) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "mark")
    val lift by t.animateFloat(-1f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(1800, easing = androidx.compose.animation.core.FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "lift")
    Box(Modifier.graphicsLayer { translationY = lift * 6.dp.toPx(); rotationZ = lift * 3f }) { WaveMark(color, size = size) }
}


/** About's update card: song-colored, with the first few changes. */
@Composable
private fun UpdateCard(release: Updater.Release, onOpen: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val preview = parseNotes(release.notes).filterIsInstance<NoteLine.Bullet>().take(3)
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(c.primaryContainer, c.tertiaryContainer)))
            .clickable(onClick = onOpen)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FloatingMark(c.onPrimaryContainer, 40.dp)
            Column(Modifier.weight(1f)) {
                Text("NEW VERSION", color = c.onPrimaryContainer.copy(alpha = 0.7f), style = body(11.sp, FontWeight.Bold).copy(letterSpacing = 1.sp))
                Text("Wave ${release.version} is here", color = c.onPrimaryContainer, style = display(20.sp))
            }
        }
        preview.forEach { note ->
            Text("•  ${note.text}", Modifier.padding(top = 8.dp), color = c.onPrimaryContainer.copy(alpha = 0.85f), style = body(14.sp).copy(lineHeight = 19.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier.padding(top = 16.dp).height(44.dp).pressable(CircleShape, c.primary, pressedScale = 0.95f, onClick = onOpen).padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) { Text("See what's new", color = c.onPrimary, style = body(15.sp, FontWeight.SemiBold)) }
    }
}

/** The update preview: what's new in it, line by line, and one button that downloads then installs. */
@Composable
private fun UpdatePage(onBack: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val update by Updater.state.collectAsState()
    val release = when (val u = update) {
        is Updater.State.Available -> u.release
        is Updater.State.Downloading -> u.release
        is Updater.State.Ready -> u.release
        else -> null
    }
    Box(Modifier.fillMaxSize()) {
        SettingsPageFrame("", onBack) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingMark(c.primary, 88.dp)
                    Text(if (release != null) "Wave ${release.version}" else "You're up to date", Modifier.padding(top = 14.dp), color = c.onBackground, style = display(32.sp))
                    Text("You have ${WavePlatform.current.appVersion}", color = c.onBackground.copy(alpha = 0.6f), style = body(14.sp))
                }
            }
            if (release != null) {
                parseNotes(release.notes).forEachIndexed { i, line ->
                    item(key = "n$i") {
                        // Each line slides up into place, one after another.
                        val appear = remember { androidx.compose.animation.core.Animatable(0f) }
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay((i * 45L).coerceAtMost(600L))
                            appear.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 260f))
                        }
                        Box(Modifier.graphicsLayer { alpha = appear.value; translationY = (1f - appear.value) * 18.dp.toPx() }) {
                            when (line) {
                                is NoteLine.Heading -> Text(line.text, Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp), color = c.primary, style = display(18.sp))
                                is NoteLine.Bullet -> Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(Modifier.padding(top = 7.dp).size(7.dp).clip(CircleShape).background(c.primary))
                                    Text(line.text, color = c.onBackground.copy(alpha = 0.85f), style = body(15.sp).copy(lineHeight = 21.sp))
                                }
                                is NoteLine.Plain -> Text(line.text, Modifier.padding(horizontal = 20.dp, vertical = 6.dp), color = c.onBackground.copy(alpha = 0.75f), style = body(15.sp).copy(lineHeight = 21.sp))
                            }
                        }
                    }
                }
                item {
                    Text(
                        "See it on GitHub",
                        Modifier.padding(horizontal = 20.dp, vertical = 16.dp).clickable { WavePlatform.current.openUrl(release.pageUrl) },
                        color = c.primary, style = body(14.sp, FontWeight.SemiBold),
                    )
                }
                item { Box(Modifier.height(110.dp)) }
            }
        }
        // One button at the bottom: Download, then a filling progress bar, then Install.
        if (release != null) {
            val progress = (update as? Updater.State.Downloading)?.progress
            val fill by androidx.compose.animation.core.animateFloatAsState(progress ?: if (update is Updater.State.Ready) 1f else 0f, tween(300), label = "dl")
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    // Above the bottom bar and mini player, which would otherwise cover it.
                    .padding(bottom = LocalMiniPlayerSpace.current)
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(CircleShape)
                    .background(c.primary.copy(alpha = if (progress != null) 0.35f else 1f))
                    .clickable(enabled = progress == null) {
                        when (val u = update) {
                            is Updater.State.Available -> scope.launch { Updater.download(u.release) }
                            is Updater.State.Ready -> Updater.install(u.file)
                            else -> Unit
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (progress != null) Box(Modifier.align(Alignment.CenterStart).fillMaxSize().fillMaxWidth(fill.coerceIn(0f, 1f)).background(c.primary))
                Text(
                    when {
                        progress != null -> "Downloading ${(progress * 100).toInt()}%"
                        update is Updater.State.Ready -> "Install now"
                        else -> "Download update"
                    },
                    color = c.onPrimary, style = display(17.sp),
                )
            }
        }
    }
}



/** One space for saved songs and covers: 8 MB to 2 GB (songs get most of it). Applies next start. */
@Composable
private fun StoragePool(mb: Int, onPick: (Int) -> Unit) {
    val c = MaterialTheme.colorScheme
    val steps = listOf(8, 16, 32, 64, 128, 256, 512, 1024, 2048)
    var at by remember(mb) { mutableStateOf(steps.indexOf(mb).takeIf { it >= 0 } ?: steps.indexOf(256)) }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Room for both", Modifier.weight(1f), color = c.onBackground, style = display(17.sp, FontWeight.SemiBold))
            Text(mb(steps[at] * 1024L * 1024), color = c.primary, style = display(17.sp))
        }
        androidx.compose.material3.Slider(
            value = at.toFloat(),
            onValueChange = { at = it.toInt().coerceIn(0, steps.lastIndex) },
            onValueChangeFinished = { onPick(steps[at]) },
            valueRange = 0f..steps.lastIndex.toFloat(),
            steps = steps.size - 2,
        )
        Text("Less room means songs download again more often. Applies when Wave restarts.", color = c.onBackground.copy(alpha = 0.6f), style = body(13.sp))
    }
}
