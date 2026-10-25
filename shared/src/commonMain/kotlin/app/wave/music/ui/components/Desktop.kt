package app.wave.music.ui.components

import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Running on a computer (Googlebook, ChromeOS): Wave swaps phone patterns for desktop ones:
 * dropdown menus where you click instead of panels sliding up, a settings dialog, no swipe gestures.
 */
val LocalDesktop = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * The big-screen layout (computers, and tablets/unfolded Folds in landscape): a sidebar for your
 * library, a player bar across the bottom, pages laid out for width.
 */
val LocalShell = androidx.compose.runtime.staticCompositionLocalOf { false }

/** How much room the floating mini player takes at the bottom, so nothing important sits under it. */
val LocalMiniPlayerSpace = androidx.compose.runtime.compositionLocalOf { 0.dp }

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

/**
 * Computers: an ordinary mouse wheel scrolls sideways lists too (not everyone has a touchpad or a
 * tilting wheel). Only while the list can still move that way; at either end the page scrolls.
 */
fun Modifier.wheelScrollsSideways(state: androidx.compose.foundation.gestures.ScrollableState): Modifier = pointerInput(state) {
    kotlinx.coroutines.coroutineScope {
        var remaining = 0f
        var job: kotlinx.coroutines.Job? = null
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type != PointerEventType.Scroll) continue
                val change = event.changes.firstOrNull() ?: continue
                if (change.isConsumed) continue
                val d = change.scrollDelta
                val notch = if (kotlin.math.abs(d.x) > kotlin.math.abs(d.y)) d.x else d.y
                val px = notch * 96.dp.toPx()
                if (px == 0f || (px > 0 && !state.canScrollForward) || (px < 0 && !state.canScrollBackward)) continue
                change.consume()
                remaining += px
                job?.cancel()
                job = launch {
                    state.scroll {
                        var done = 0f
                        val total = remaining
                        androidx.compose.animation.core.animate(0f, total, animationSpec = androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.LinearOutSlowInEasing)) { v, _ ->
                            val used = scrollBy(v - done)
                            done = v
                            remaining -= used
                        }
                        remaining = 0f
                    }
                }
            }
        }
    }
}

/** A sideways row of cards that a mouse wheel can scroll too. */
@androidx.compose.runtime.Composable
fun WheelRow(
    modifier: Modifier = Modifier,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
    spacing: androidx.compose.ui.unit.Dp = 12.dp,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.foundation.lazy.LazyRow(
        modifier.wheelScrollsSideways(state),
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(spacing),
        content = content,
    )
}
