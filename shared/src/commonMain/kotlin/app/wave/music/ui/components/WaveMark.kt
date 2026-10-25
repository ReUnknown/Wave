package app.wave.music.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The Wave logo: two parallel waves. Drawn on the same 108-unit grid as the launcher icon. */
@Composable
fun WaveMark(color: Color, modifier: Modifier = Modifier, size: Dp = 26.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.width / 60f
        fun wave(y: Float) = Path().apply {
            moveTo((26 - 24) * s, (y - 26) * s)
            quadraticTo((40 - 24) * s, (y - 38) * s, (53 - 24) * s, (y - 26) * s)
            quadraticTo((66 - 24) * s, (y - 14) * s, (80 - 24) * s, (y - 26) * s)
        }
        val stroke = Stroke(width = 6.5f * s, cap = StrokeCap.Round)
        drawPath(wave(44f), color, style = stroke)
        drawPath(wave(62f), color, style = stroke)
    }
}
