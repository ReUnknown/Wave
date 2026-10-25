package app.wave.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference
import java.awt.Window

/**
 * Windows: a title bar in Wave's own background color, dark or light with Wave's theme
 * (Windows 10/11 draw it in the system accent otherwise). Uses the system's own window
 * attributes, so the real caption buttons, snapping and dragging all stay.
 */
object WindowsTitleBar {
    private interface Dwm : Library {
        fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attribute: Int, value: Pointer, size: Int): Int
    }

    private const val USE_IMMERSIVE_DARK_MODE = 20
    private const val CAPTION_COLOR = 35
    private const val TEXT_COLOR = 36

    private val dwm by lazy { runCatching { Native.load("dwmapi", Dwm::class.java) }.getOrNull() }

    fun apply(window: Window, argb: Int, dark: Boolean = true) {
        runCatching {
            val dwm = dwm ?: return
            val hwnd = WinDef.HWND(Native.getComponentPointer(window))
            // Windows 10: dark or light caption buttons. Windows 11 also takes exact colors below.
            dwm.DwmSetWindowAttribute(hwnd, USE_IMMERSIVE_DARK_MODE, IntByReference(if (dark) 1 else 0).pointer, 4)
            dwm.DwmSetWindowAttribute(hwnd, CAPTION_COLOR, IntByReference(colorRef(argb)).pointer, 4)
            dwm.DwmSetWindowAttribute(hwnd, TEXT_COLOR, IntByReference(colorRef(if (dark) 0xFFE3E6EA.toInt() else 0xFF1A1C1E.toInt())).pointer, 4)
        }
    }

    /** COLORREF is 0x00BBGGRR. */
    private fun colorRef(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (b shl 16) or (g shl 8) or r
    }
}
