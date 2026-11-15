package app.wave.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.wave.music.data.WaveSettings
import app.wave.music.platform.WavePlatform
import kotlinx.coroutines.launch

/**
 * Wave's computer apps (Windows, macOS, Linux) keep working but no longer get updates: Wave is
 * Android only from here, and WaveMini is the desktop app. This says so, once at the top of the
 * window (until it's closed) and always in Settings.
 */
object DesktopSunset {
    const val URL = "https://github.com/ReUnknown/WaveMini/releases/latest"

    /** The computer app, not Android on a Chromebook or Googlebook. */
    val applies: Boolean get() = WavePlatform.current.scalesInterface

    private val onWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")

    val title = "Wave for computers is no longer updated"
    val message: String
        get() = if (onWindows) "It still works, but new features and fixes go to WaveMini, the new desktop app. Install WaveMini to keep using Wave on your desktop."
        else "It still works, but new features and fixes go to WaveMini, the new desktop app (on Windows for now)."
}

/** The notice across the top of the window; the × hides it for good. */
@Composable
fun DesktopSunsetBanner(modifier: Modifier = Modifier) {
    val settings by WaveSettings.state.collectAsState()
    if (!DesktopSunset.applies || settings.desktopNoticeClosed) return
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme
    Row(
        modifier
            .padding(horizontal = 16.dp)
            .widthIn(max = 760.dp)
            .shadow(12.dp, RoundedCornerShape(20.dp))
            .background(c.primaryContainer, RoundedCornerShape(20.dp))
            .padding(start = 18.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(DesktopSunset.title, color = c.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(DesktopSunset.message, color = c.onPrimaryContainer.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 17.sp)
        }
        GetWaveMiniButton()
        Box(
            Modifier.size(36.dp).pressable(CircleShape, Color.Transparent, pressedScale = 0.86f) {
                scope.launch { WaveSettings.update { it.copy(desktopNoticeClosed = true) } }
            },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Close, "Close this notice", tint = c.onPrimaryContainer) }
    }
}

@Composable
fun GetWaveMiniButton() {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier
            .height(40.dp)
            .pressable(RoundedCornerShape(20.dp), c.primary, pressedScale = 0.92f) { WavePlatform.current.openUrl(DesktopSunset.URL) }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Text("Get WaveMini", color = c.onPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
}
