package app.wave.music.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * For full-screen layers drawn over another screen (player, search, pages): makes the whole
 * layer a touch target, so taps on its empty parts stop here instead of reaching buttons
 * on the screen underneath.
 */
fun Modifier.blocksTouchesBelow(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent()
    }
}
