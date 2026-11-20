package app.wave.music.ui.settings

import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.ViewCompact
import androidx.compose.material.icons.rounded.RoundedCorner
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Dashboard
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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
import app.wave.music.ui.components.WaveMotion
import app.wave.music.ui.components.blocksTouchesBelow
import app.wave.music.ui.components.pressable
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/*
 * Prisma's settings: their own page, like InnerTune's. Your account at the top, then a short list
 * of sections, each opening its own page of settings.
 */

/** The sections, in the order they're listed. */
enum class SettingsSection(val id: String, val title: String, val summary: String, val icon: ImageVector) {
    Customize("customize", "Customize", "Modern or Legacy, layout, corners, buttons, haptics", Icons.Rounded.Dashboard),
    Looks("looks", "Appearance", "Theme, background, blur, text size, colors", Icons.Rounded.Palette),
    Sound("sound", "Sound", "Quality, even volume, crossfade", Icons.Rounded.Tune),
    Playback("playback", "Playback", "Autoplay, skip silence, battery", Icons.Rounded.PlayCircle),
    About("about", "Updates & about", "Version and updates", Icons.Rounded.Info);

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: Looks
    }
}

private fun display(size: TextUnit, weight: FontWeight = FontWeight.Bold) = TextStyle(fontFamily = prismaDisplayFont, fontSize = size, fontWeight = weight)
private fun body(size: TextUnit, weight: FontWeight = FontWeight.Normal) = TextStyle(fontFamily = prismaTextFont, fontSize = size, fontWeight = weight)

/** A settings page: a back button and a big title, then [content]. */
@Composable
private fun SettingsPageFrame(title: String, onBack: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    val top = topInset()
    Box(Modifier.fillMaxSize().blocksTouchesBelow().background(c.background)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = top + 8.dp, bottom = 40.dp + LocalMiniPlayerSpace.current)) {
            item {
                Box(
                    Modifier.padding(start = 12.dp).size(48.dp).pressable(CircleShape, c.onBackground.copy(alpha = 0.08f), pressedScale = 0.88f, onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = c.onBackground) }
            }
            item { Text(title, Modifier.padding(start = 20.dp, top = 14.dp, bottom = 16.dp), color = c.onBackground, style = display(34.sp).copy(letterSpacing = (-0.6).sp)) }
            content()
        }
    }
}

/** The main settings page: your account, then the sections. */
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
                        color = c.onSurfaceVariant,
                        style = body(13.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    Modifier.height(40.dp).clip(CircleShape).border(1.dp, c.outline, CircleShape).clickable(onClick = onSignOut).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(if (s == null || s.guest) "Sign in" else "Sign out", color = c.primary, style = body(14.sp, FontWeight.SemiBold)) }
            }
        }
        item { Box(Modifier.height(12.dp)) }
        SettingsSection.entries.forEach { section ->
            item(key = section.id) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSection(section) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(c.secondaryContainer), contentAlignment = Alignment.Center) {
                        Icon(section.icon, null, tint = c.onSecondaryContainer)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(section.title, color = c.onBackground, style = display(17.sp, FontWeight.SemiBold))
                        Text(section.summary, color = c.onBackground.copy(alpha = 0.6f), style = body(13.sp))
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = c.onBackground.copy(alpha = 0.5f))
                }
            }
        }
    }
}

/** One section's own page. */
@Composable
fun PrismaSettingsPage(section: SettingsSection, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val settings by WaveSettings.state.collectAsState()
    val update by Updater.state.collectAsState()
    val c = MaterialTheme.colorScheme
    fun change(block: (Settings) -> Settings) {
        scope.launch { WaveSettings.update(block) }
    }
    SettingsPageFrame(section.title, onBack) {
        when (section) {
            SettingsSection.Customize -> {
                item {
                    Choice(
                        Icons.Rounded.AutoAwesome, "Look", "Modern is Prisma; Legacy is a modern take on Wave 1.0",
                        listOf(false to "Modern", true to "Legacy"), settings.legacyUi,
                    ) { v -> change { it.copy(legacyUi = v) } }
                }
                item {
                    Choice(
                        Icons.Rounded.Explore, "Getting around", "The bar at the bottom, or a search bar at the top that also opens your library",
                        listOf(false to "Bottom bar", true to "Top search"), settings.topSearch,
                    ) { v -> change { it.copy(topSearch = v) } }
                }
                item {
                    Toggle(
                        Icons.Rounded.PictureInPicture, "Mini player on its own",
                        if (settings.topSearch) "Always on its own with the top search" else "Floats above the bar instead of joining it",
                        settings.separateMiniPlayer || settings.topSearch,
                    ) { on -> change { it.copy(separateMiniPlayer = on) } }
                }
                item {
                    Choice(
                        Icons.Rounded.RoundedCorner, "Corners", "How round cards, menus and the bar are",
                        listOf(0 to "Rounded", 1 to "Squircle", 2 to "Sharp"), settings.cornerStyle,
                    ) { v -> change { it.copy(cornerStyle = v) } }
                }
                item { Toggle(Icons.Rounded.ViewCompact, "Denser home", "Smaller cards, so you see more at once", settings.denseHome) { on -> change { it.copy(denseHome = on) } } }
                item {
                    Choice(
                        Icons.Rounded.Forward10, "Buttons beside play", "In the player: skip songs, or jump 10 seconds",
                        listOf(false to "Skip songs", true to "10 seconds"), settings.seekButtons,
                    ) { v -> change { it.copy(seekButtons = v) } }
                }
                item { Toggle(Icons.Rounded.SkipNext, "Previous and next in the mini player", "Off leaves just the play button", settings.dockSkip) { on -> change { it.copy(dockSkip = on) } } }
                item {
                    Choice(
                        Icons.Rounded.Vibration, "Haptics", "The little buzzes when you tap and drag",
                        listOf(0 to "On", 1 to "Subtle", 2 to "Off"), settings.haptics,
                    ) { v -> change { it.copy(haptics = v) } }
                }
            }
            SettingsSection.Looks -> {
                item {
                    Choice(
                        Icons.Rounded.Contrast, "Theme", "Follow your phone, or always light or dark",
                        listOf(0 to "System", 1 to "Light", 2 to "Dark"), settings.theme,
                    ) { v -> change { it.copy(theme = v) } }
                }
                item {
                    Choice(
                        Icons.Rounded.Wallpaper, "Background", "With song colors on: the cover blurred behind everything, a glow at the top, or plain",
                        listOf(0 to "Cover", 1 to "Glow", 2 to "Plain"), settings.backdropStyle,
                    ) { v -> change { it.copy(backdropStyle = v) } }
                }
                item {
                    Choice(
                        Icons.Rounded.BlurOn, "Blur", "Frosted glass on the bar, menus and page tops (off is easier on slower phones)",
                        listOf(0 to "Off", 1 to "Light", 2 to "Full"), settings.blurLevel,
                    ) { v -> change { it.copy(blurLevel = v) } }
                }
                item {
                    Choice(
                        Icons.Rounded.FormatSize, "Text size", "On top of your phone's own text size",
                        listOf(0.9f to "Smaller", 1f to "Default", 1.12f to "Bigger"), settings.textScale,
                    ) { v -> change { it.copy(textScale = v) } }
                }
                item { Toggle(Icons.Rounded.Palette, "Match song colors", "Wave takes on the colors of what's playing", settings.matchSongColors) { on -> change { it.copy(matchSongColors = on) } } }
                item { Toggle(Icons.Rounded.DarkMode, "AMOLED black", "Pure black backgrounds in dark mode", settings.amoled) { on -> change { it.copy(amoled = on) } } }
                item { Toggle(Icons.Rounded.GraphicEq, "Move with the music", "Shapes pulse and waves flow with the beat", settings.moveWithMusic) { on -> change { it.copy(moveWithMusic = on) } } }
            }
            SettingsSection.Sound -> {
                item {
                    Choice(
                        Icons.Rounded.HighQuality,
                        "Audio quality",
                        when (settings.audioQuality) {
                            AudioQuality.HIGH -> "The best Wave can get"
                            AudioQuality.AUTO -> "Best on Wi-Fi, lighter on mobile data"
                            AudioQuality.LOW -> "Uses the least data"
                        },
                        listOf(AudioQuality.LOW to "Low", AudioQuality.AUTO to "Auto", AudioQuality.HIGH to "High"),
                        settings.audioQuality,
                    ) { q -> change { it.copy(audioQuality = q) } }
                }
                item { Toggle(Icons.AutoMirrored.Rounded.VolumeUp, "Even volume", "Loud songs are turned down so everything sounds the same", settings.evenVolume) { on -> change { it.copy(evenVolume = on) } } }
                item {
                    Choice(
                        Icons.Rounded.SwapHoriz,
                        "Crossfade",
                        if (settings.crossfadeSeconds == 0) "Songs play one after another" else "Songs blend over ${settings.crossfadeSeconds} seconds",
                        listOf(0 to "Off", 3 to "3s", 6 to "6s", 9 to "9s", 12 to "12s"),
                        settings.crossfadeSeconds,
                    ) { s -> change { it.copy(crossfadeSeconds = s) } }
                }
            }
            SettingsSection.Playback -> {
                item { Toggle(Icons.Rounded.PlayCircle, "Keep the music going", "When a playlist ends, carry on with similar songs", settings.autoplay) { on -> change { it.copy(autoplay = on) } } }
                item { Toggle(Icons.Rounded.FastForward, "Skip silence", "Trims the quiet gaps in songs", settings.skipSilence) { on -> change { it.copy(skipSilence = on) } } }
                if (!WavePlatform.current.isComputer) {
                    item { Toggle(Icons.Rounded.BatterySaver, "Save battery while playing", "The phone's audio chip plays when Wave isn't on screen (off with Skip silence)", settings.lowPowerPlayback) { on -> change { it.copy(lowPowerPlayback = on) } } }
                }
            }
            SettingsSection.About -> {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Tile(Icons.Rounded.SystemUpdate)
                        Column(Modifier.weight(1f)) {
                            Text("Wave Prisma ${WavePlatform.current.appVersion}", color = c.onBackground, style = display(17.sp, FontWeight.SemiBold))
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
                                color = c.onBackground.copy(alpha = 0.6f),
                                style = body(13.sp),
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
                }
                (update as? Updater.State.Downloading)?.let { u ->
                    item { LinearProgressIndicator(progress = { u.progress }, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) }
                }
                item { Toggle(Icons.Rounded.Autorenew, "Update automatically", "Wave looks for a new version once a day", settings.checkUpdates) { on -> change { it.copy(checkUpdates = on) } } }
                item {
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
                        text = { Text("You'll be signed out, and your settings, searches, history and downloaded lists are cleared from this phone. Your YouTube Music account isn't touched.") },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                asking = false
                                scope.launch { app.wave.music.data.eraseEverything() }
                            }) { Text("Erase", color = c.error) }
                        },
                        dismissButton = { androidx.compose.material3.TextButton(onClick = { asking = false }) { Text("Cancel") } },
                    )
                }
            }
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
                ) { Text(label, color = ink, style = body(14.sp, FontWeight.SemiBold)) }
            }
        }
    }
}
