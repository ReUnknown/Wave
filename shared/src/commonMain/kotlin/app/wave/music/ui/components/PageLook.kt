package app.wave.music.ui.components

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The colors and art of the page on top (an artist, album, playlist, search results), so the
 * parts of the window around it (the computer's sidebar, the top of the window) match it
 * instead of staying in home's colors.
 */
@Stable
class PageLook {
    @Stable
    class Entry {
        var scheme by mutableStateOf<ColorScheme?>(null)
        var art by mutableStateOf<String?>(null)
    }

    internal val entries = mutableStateListOf<Entry>()

    /** The most recently opened page still showing, or null on home. */
    val current: Entry? get() = entries.lastOrNull()
}

val LocalPageLook = staticCompositionLocalOf<PageLook?> { null }

/** A page tells the window around it what it looks like, for as long as it's showing. */
@Composable
fun ReportPageLook(scheme: ColorScheme, art: String?) {
    val look = LocalPageLook.current ?: return
    val entry = remember { PageLook.Entry() }
    entry.scheme = scheme
    entry.art = art
    DisposableEffect(look, entry) {
        look.entries.add(entry)
        onDispose { look.entries.remove(entry) }
    }
}
