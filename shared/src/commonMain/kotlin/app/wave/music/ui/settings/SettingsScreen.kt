package app.wave.music.ui.settings

import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wave.music.data.Settings
import app.wave.music.data.Updater
import app.wave.music.data.WaveSettings
import app.wave.music.playback.AudioQuality
import app.wave.music.ui.components.WaveMotion
import app.wave.music.ui.components.blocksTouchesBelow
import app.wave.music.ui.components.pressable
import kotlinx.coroutines.launch

/**
 * Wave's settings, grouped so they stay easy to scan: how Wave looks, how it plays, battery,
 * keyboard (on computers) and about. Each setting says in plain words what it does. Shown at the
 * bottom of the account panel on phones and tablets (swipe it up), and in a dialog on computers.
 */
@Composable
fun SettingsContent() {
    val scope = rememberCoroutineScope()
    val settings by WaveSettings.state.collectAsState()
    val update by Updater.state.collectAsState()
    val c = MaterialTheme.colorScheme
    fun change(block: (Settings) -> Settings) {
        scope.launch { WaveSettings.update(block) }
    }
    Column {
    Eyebrow("Look & feel")
    Group {
        Toggle("Match song colors", "The whole app takes on the colors of what's playing", settings.matchSongColors) { on ->
            change { it.copy(matchSongColors = on) }
        }
        Divider()
        Toggle("Move with the music", "Shapes pulse and waves flow with the beat", settings.moveWithMusic) { on ->
            change { it.copy(moveWithMusic = on) }
        }
        // Computers: the player can open beside the app instead of over it.
        if (app.wave.music.ui.components.LocalDesktop.current) {
            Divider()
            Toggle("Player beside the app", "Opens in half the window so you can keep browsing; expand it any time", settings.splitPlayer) { on ->
                change { it.copy(splitPlayer = on) }
            }
        }
    }

    Eyebrow("Playback")
    Group {
        Choice(
            title = "Audio quality",
            description = when (settings.audioQuality) {
                AudioQuality.HIGH -> "Best available, about 160 kbps"
                AudioQuality.AUTO -> "High on Wi-Fi, lower on mobile data"
                AudioQuality.LOW -> "Saves data, about 50 kbps"
            },
            options = listOf(AudioQuality.LOW to "Low", AudioQuality.AUTO to "Auto", AudioQuality.HIGH to "High"),
            selected = settings.audioQuality,
        ) { q -> change { it.copy(audioQuality = q) } }
        Divider()
        Toggle("Even volume", "Loud songs are turned down so everything plays at about the same volume", settings.evenVolume) { on ->
            change { it.copy(evenVolume = on) }
        }
        Divider()
        Choice(
            title = "Crossfade",
            description = if (settings.crossfadeSeconds == 0) "Songs play one after another" else "Each song fades into the next over ${settings.crossfadeSeconds} seconds",
            options = listOf(0 to "Off", 3 to "3s", 6 to "6s", 9 to "9s", 12 to "12s"),
            selected = settings.crossfadeSeconds,
        ) { s -> change { it.copy(crossfadeSeconds = s) } }
        Divider()
        Toggle("Autoplay", "When a playlist ends, keep going with similar songs", settings.autoplay) { on ->
            change { it.copy(autoplay = on) }
        }
        Divider()
        Toggle("Skip silence", "Trim quiet gaps between songs", settings.skipSilence) { on ->
            change { it.copy(skipSilence = on) }
        }
    }

    Eyebrow("Battery")
    Group {
        Toggle(
            "Low-power playback",
            "When Wave isn't on screen, your phone's audio chip plays the music so the rest of the phone can rest. Not used with Skip silence.",
            settings.lowPowerPlayback,
        ) { on -> change { it.copy(lowPowerPlayback = on) } }
    }

    // Googlebook, ChromeOS or a keyboard case: show what the keys do.
    if (app.wave.music.platform.WavePlatform.current.isComputer || app.wave.music.platform.WavePlatform.current.hasKeyboard) {
        Eyebrow("Keyboard")
        Group {
            listOf(
                "Play or pause" to "Space",
                "Next / previous song" to "Ctrl  →  /  ←",
                "Search" to "Ctrl  F   or   /",
                "Like the song" to "Ctrl  L",
                "Back" to "Esc",
            ).forEachIndexed { i, (action, keys) ->
                if (i > 0) Divider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(action, Modifier.weight(1f), fontSize = 15.sp, color = c.onSurface)
                    Text(
                        keys,
                        Modifier
                            .background(c.surfaceContainerHighest, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = c.onSurfaceVariant,
                    )
                }
            }
        }
    }

    Eyebrow("About")
    Group {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Wave ${app.wave.music.platform.WavePlatform.current.appVersion}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface)
                Text(
                    when (val u = update) {
                        Updater.State.Idle -> "Tap to check for a new version"
                        Updater.State.Checking -> "Checking…"
                        Updater.State.UpToDate -> "You're up to date"
                        Updater.State.Failed -> "Couldn't check for updates"
                        is Updater.State.Available -> "Wave ${u.release.version} is available"
                        is Updater.State.Downloading -> "Downloading Wave ${u.release.version}…"
                        is Updater.State.Ready -> "Wave ${u.release.version} is ready to install"
                    },
                    fontSize = 13.sp,
                    color = c.onSurfaceVariant,
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
                    Modifier
                        .height(40.dp)
                        .pressable(RoundedCornerShape(20.dp), c.primary, pressedScale = 0.92f, onClick = action)
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = c.onPrimary, fontWeight = FontWeight.SemiBold) }
            }
        }
        (update as? Updater.State.Downloading)?.let { u ->
            LinearProgressIndicator(
                progress = { u.progress },
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 14.dp),
            )
        }
        Divider()
        Toggle("Check for updates automatically", "Once a day, from Wave's GitHub releases", settings.checkUpdates) { on ->
            change { it.copy(checkUpdates = on) }
        }
    }
    }
}

@Composable
internal fun Eyebrow(text: String) {
    Text(
        text.uppercase(),
        Modifier.padding(start = 8.dp, top = 24.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp),
        content = content,
    )
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
}

@Composable
private fun Toggle(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** A connected segmented choice: the picked one fills in and rounds out. */
@Composable
private fun <T> Choice(title: String, description: String, options: List<Pair<T, String>>, selected: T, onPick: (T) -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 8.dp, vertical = 14.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface)
        Text(description, fontSize = 13.sp, color = c.onSurfaceVariant)
        Row(Modifier.padding(top = 10.dp).fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            options.forEachIndexed { i, (value, label) ->
                val on = value == selected
                val outer by animateDpAsState(if (on) 22.dp else 8.dp, WaveMotion.bouncy(), label = "segment")
                val start = if (i == 0) 22.dp else outer.coerceAtMost(if (on) 22.dp else 8.dp)
                val end = if (i == options.lastIndex) 22.dp else outer.coerceAtMost(if (on) 22.dp else 8.dp)
                val fill by animateColorAsState(if (on) c.primary else c.surfaceContainerHighest, tween(160), label = "segmentFill")
                val ink by animateColorAsState(if (on) c.onPrimary else c.onSurface, tween(160), label = "segmentInk")
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .pressable(RoundedCornerShape(start, end, end, start), fill, pressedScale = 0.94f) { onPick(value) },
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
            }
        }
    }
}


/** On computers: settings in a centered dialog instead of a panel sliding up from the bottom. */
@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(28.dp),
            color = c.surfaceContainer,
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.86f),
        ) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 12.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Settings", Modifier.weight(1f), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = c.onSurface)
                    androidx.compose.material3.IconButton(onClick = onDismiss) {
                        Icon(androidx.compose.material.icons.Icons.Rounded.Close, "Close settings", tint = c.onSurfaceVariant)
                    }
                }
                Column(
                    Modifier
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                ) { SettingsContent() }
            }
        }
    }
}
