package app.wave.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.loadImageBitmap

/** Wave's icon (the two waves), for the window and the dock / taskbar. */
object WaveIcon {
    @Composable
    fun painter(): Painter = remember {
        BitmapPainter(requireNotNull(WaveIcon::class.java.getResourceAsStream("/wave.png")).use(::loadImageBitmap))
    }
}

/** This version of Wave (the installers pass it in too). */
object WaveVersion {
    const val NAME = "1.1.0-beta.2"
}
