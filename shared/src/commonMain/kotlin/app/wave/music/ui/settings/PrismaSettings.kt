package app.wave.music.ui.settings

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
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
    Playback("playback", "Playback & sound", "Quality, fades, more songs when a queue ends", Icons.Rounded.MusicNote),
    Storage("storage", "Storage", "Saved songs and covers, history, erase", Icons.Rounded.Storage),
    Battery("battery", "Battery", "Battery saver, calmer motion", Icons.Rounded.BatteryStd),
    About("about", "About", "Version, updates, what's new, credits", Icons.Rounded.Info),
    Credits("credits", "Attribution", "", Icons.Rounded.Favorite),
    WhatsNew("whatsnew", "What's new", "", Icons.Rounded.NewReleases);

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Customize
        /** The ones listed on the main settings page, in groups. */
        val groups = listOf(
            "Make it yours" to listOf(Customize, Looks),
            "Listening" to listOf(Playback),
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
            if (title.isNotEmpty()) item { Text(title, Modifier.padding(start = 20.dp, top = 14.dp, bottom = 12.dp), color = c.onBackground, style = display(34.sp).copy(letterSpacing = (-0.6).sp)) }
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
    if (section == SettingsSection.About) return AboutPage(onBack, open)
    if (section == SettingsSection.Credits) return CreditsPage(onBack)
    if (section == SettingsSection.WhatsNew) return WhatsNewPage(onBack)
    SettingsPageFrame(section.title, onBack) {
        when (section) {
            SettingsSection.Customize -> {
                item { GroupLabel("Look") }
                item {
                    Choice(Icons.Rounded.AutoAwesome, "Style", "Modern is Prisma; Legacy is a modern take on Wave 1.0", listOf(false to "Modern", true to "Legacy"), settings.legacyUi) { v -> change { it.copy(legacyUi = v) } }
                }
                item {
                    Choice(Icons.Rounded.RoundedCorner, "Corners", "How round cards, menus and the bar are", listOf(0 to "Rounded", 1 to "Squircle", 2 to "Sharp"), settings.cornerStyle) { v -> change { it.copy(cornerStyle = v) } }
                }
                item { Toggle(Icons.Rounded.ViewCompact, "Denser home", "Smaller cards, so you see more at once", settings.denseHome) { on -> change { it.copy(denseHome = on) } } }
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
            SettingsSection.Playback -> {
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
            else -> Unit
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
            GroupLabel("Saved songs")
            UsageBar(Icons.Rounded.MusicNote, "Songs you've played", usage.first, settings.songCacheMb)
            Choice(Icons.Rounded.Storage, "Room for songs", "Wave keeps songs you play so they start instantly next time (applies when Wave restarts)", listOf(256 to "256 MB", 512 to "512 MB", 1024 to "1 GB", 2048 to "2 GB"), settings.songCacheMb) { v -> change { it.copy(songCacheMb = v) } }
            ActionRow(Icons.Rounded.DeleteForever, "Clear saved songs", "They'll download again when you play them") {
                scope.launch { withContext(Dispatchers.IO) { WavePlatform.current.clearSongCache() }; refresh++ }
            }
            GroupLabel("Covers")
            UsageBar(Icons.Rounded.Image, "Album and artist art", usage.second, settings.coverCacheMb)
            Choice(Icons.Rounded.Storage, "Room for covers", "Applies when Wave restarts", listOf(100 to "100 MB", 200 to "200 MB", 500 to "500 MB"), settings.coverCacheMb) { v -> change { it.copy(coverCacheMb = v) } }
            ActionRow(Icons.Rounded.DeleteForever, "Clear covers", "They'll load again as you browse") {
                scope.launch { withContext(Dispatchers.IO) { WavePlatform.current.clearCoverCache() }; refresh++ }
            }
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
                Text("Prisma ${WavePlatform.current.appVersion}", color = c.onBackground.copy(alpha = 0.65f), style = body(15.sp))
                Row(
                    Modifier.padding(top = 14.dp).height(42.dp).pressable(CircleShape, c.onBackground.copy(alpha = 0.08f), pressedScale = 0.94f) { WavePlatform.current.openUrl(GITHUB) }.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Code, null, Modifier.size(20.dp), tint = c.onBackground)
                    Text("  GitHub", color = c.onBackground, style = body(14.sp, FontWeight.SemiBold))
                }
            }
        }
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
                NavRow(Icons.Rounded.NewReleases, "What's new", "Every version, newest first") { open(SettingsSection.WhatsNew) }
                NavRow(Icons.Rounded.Favorite, "Attribution", "What Wave is built on and inspired by") { open(SettingsSection.Credits) }
                NavRow(Icons.Rounded.BugReport, "Report a bug", "Opens a new issue on GitHub", external = true) { WavePlatform.current.openUrl("$GITHUB/issues/new") }
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
    Credit("Metrolist InnerTube & InnerTubeX", "The YouTube Music client Wave talks to YouTube Music with (GPL v3)", "https://github.com/MetrolistGroup/Metrolist"),
    Credit("Haze", "The frosted glass blur, by Chris Banes (Apache 2.0)", "https://github.com/chrisbanes/haze"),
    Credit("Jetpack Compose & Compose Multiplatform", "Wave's whole interface (Apache 2.0)", "https://www.jetbrains.com/compose-multiplatform/"),
    Credit("Material 3 Expressive", "Shapes, motion and components (Apache 2.0)", "https://m3.material.io"),
    Credit("Media3 / ExoPlayer", "Playback, the notification and lock screen controls (Apache 2.0)", "https://github.com/androidx/media"),
    Credit("Coil", "Loading covers and art (Apache 2.0)", "https://github.com/coil-kt/coil"),
    Credit("Ktor & kotlinx", "Networking, JSON and coroutines (Apache 2.0)", "https://ktor.io"),
    Credit("Reorderable", "Drag to reorder Up next, by Calvin Liang (Apache 2.0)", "https://github.com/Calvin-LL/Reorderable"),
    Credit("Timber", "Logging, by Jake Wharton (Apache 2.0)", "https://github.com/JakeWharton/timber"),
    Credit("LRCLIB", "Time-synced lyrics", "https://lrclib.net"),
    Credit("Better Lyrics", "Word-by-word lyrics", "https://github.com/better-lyrics"),
    Credit("Google Sans", "Prisma's font, from your phone", "https://fonts.google.com"),
)

private val inspiredBy = listOf(
    Credit("InnerTune", "Settings on their own pages, and the About page", "https://github.com/z-huang/InnerTune"),
    Credit("BitChord", "Home's top picks, and Apple-style lyric motion (credited to binimum)", "https://alternativeto.net/software/bitchord/about/"),
    Credit("Apple Music", "The big artist name tag, and lyrics that ripple into place", "https://music.apple.com"),
    Credit("YouTube Music", "Where all the music comes from", "https://music.youtube.com"),
    Credit("Netflix & YouTube for TV", "The photo that stays behind the page as you scroll", "https://www.netflix.com"),
)

@Composable
private fun CreditsPage(onBack: () -> Unit) {
    SettingsPageFrame("Attribution", onBack) {
        item { GroupLabel("Built with") }
        builtOn.forEach { credit -> item(key = credit.name) { CreditRow(credit) } }
        item { GroupLabel("Inspired by") }
        inspiredBy.forEach { credit -> item(key = "i-" + credit.name) { CreditRow(credit) } }
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
    val c = MaterialTheme.colorScheme
    SettingsPageFrame("What's new", onBack) {
        Changelog.versions.forEachIndexed { i, v ->
            item(key = v.version) {
                Column(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (i == 0) c.primaryContainer else c.surfaceContainer)
                        .padding(18.dp),
                ) {
                    val ink = if (i == 0) c.onPrimaryContainer else c.onSurface
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(v.version, Modifier.weight(1f), color = ink, style = display(19.sp))
                        if (i == 0) Text("This version", color = ink.copy(alpha = 0.75f), style = body(12.sp, FontWeight.SemiBold))
                    }
                    Text(v.title, Modifier.padding(top = 2.dp), color = ink.copy(alpha = 0.8f), style = body(14.sp, FontWeight.Medium))
                    v.notes.forEach { note ->
                        Text("•  $note", Modifier.padding(top = 6.dp), color = ink.copy(alpha = 0.75f), style = body(13.sp).copy(lineHeight = 18.sp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(),
        Modifier.padding(start = 24.dp, end = 20.dp, top = 22.dp, bottom = 8.dp),
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
                ) { Text(label, color = ink, style = body(14.sp, FontWeight.SemiBold), maxLines = 1) }
            }
        }
    }
}
