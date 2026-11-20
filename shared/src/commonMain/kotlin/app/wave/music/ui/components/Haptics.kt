package app.wave.music.ui.components

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Customize › Haptics. [level] 0 passes everything through; 1 (subtle) keeps only the light
 * ticks and turns heavier buzzes into a tick; 2 is silent.
 */
class FilteredHaptics(private val base: HapticFeedback, private val level: Int) : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        when (level) {
            0 -> base.performHapticFeedback(hapticFeedbackType)
            1 -> base.performHapticFeedback(
                if (hapticFeedbackType == HapticFeedbackType.TextHandleMove || hapticFeedbackType == HapticFeedbackType.SegmentFrequentTick) hapticFeedbackType
                else HapticFeedbackType.SegmentFrequentTick,
            )
            else -> Unit
        }
    }
}
