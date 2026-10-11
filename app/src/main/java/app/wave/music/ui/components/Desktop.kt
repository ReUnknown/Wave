package app.wave.music.ui.components

import androidx.compose.ui.unit.dp
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Desktop-class Android: Googlebook OS (Android + ChromeOS), ChromeOS, and anything Android itself
 * reports as a PC. Layout doesn't depend on this (it follows the window's size); this only turns
 * on desktop niceties, like listing the keyboard shortcuts in settings.
 */
fun isDesktop(context: Context): Boolean {
    val pm = context.packageManager
    return pm.hasSystemFeature(PackageManager.FEATURE_PC) ||
        pm.hasSystemFeature("org.chromium.arc") ||
        pm.hasSystemFeature("org.chromium.arc.device_management")
}

/**
 * Running on a computer (Googlebook, ChromeOS): Wave swaps phone patterns for desktop ones:
 * dropdown menus where you click instead of panels sliding up, a settings dialog, no swipe gestures.
 */
val LocalDesktop = androidx.compose.runtime.staticCompositionLocalOf { false }

/** How much room the floating mini player takes at the bottom, so nothing important sits under it. */
val LocalMiniPlayerSpace = androidx.compose.runtime.compositionLocalOf { 0.dp }

/** A real keyboard is attached (desktop, or a tablet with a keyboard case). */
fun hasKeyboard(context: Context): Boolean =
    context.resources.configuration.keyboard == Configuration.KEYBOARD_QWERTY

/** Keyboard shortcuts the activity hands to whichever screen handles them. */
object Shortcuts {
    /** True while a text field has focus, so typing a space doesn't pause the music. */
    @Volatile var typing = false

    private val _openSearch = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openSearch: SharedFlow<Unit> = _openSearch.asSharedFlow()
    fun requestSearch() {
        _openSearch.tryEmit(Unit)
    }
}

/** Right-click (or a two-finger click on a touchpad) runs [onSecondaryClick]: Wave uses it for item menus. */
fun Modifier.onRightClick(onSecondaryClick: () -> Unit): Modifier = pointerInput(onSecondaryClick) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                event.changes.forEach { it.consume() }
                onSecondaryClick()
            }
        }
    }
}

/**
 * Whether Wave is in front: on screen and focused. Minimized, behind another window, or with the
 * notification shade down, decorative motion (the progress wave, spinning tiles, beat effects,
 * per-frame lyrics) pauses. Music keeps playing either way.
 */
object AppFocus {
    private val _active = kotlinx.coroutines.flow.MutableStateFlow(true)
    val active: kotlinx.coroutines.flow.StateFlow<Boolean> = _active.asStateFlow()
    private var started = false
    private var focused = true

    fun onStarted(value: Boolean) { started = value; update() }
    fun onFocus(value: Boolean) { focused = value; update() }
    private fun update() { _active.value = started && focused }
}
