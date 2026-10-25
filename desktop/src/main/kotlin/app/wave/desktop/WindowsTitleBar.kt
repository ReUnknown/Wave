package app.wave.desktop

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference
import java.awt.Window

/**
 * Windows: a dark title bar that blends into Wave (Windows 10/11 draw it light by default).
 * Uses the system's own window attributes, so the real caption buttons stay.
 */
object WindowsTitleBar {
    private interface Dwm : Library {
        fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attribute: Int, value: Pointer, size: Int): Int
    }

    private const val USE_IMMERSIVE_DARK_MODE = 20
    private const val CAPTION_COLOR = 35

    fun apply(window: Window, argb: Int) {
        runCatching {
            val dwm = Native.load("dwmapi", Dwm::class.java)
            val hwnd = WinDef.HWND(Native.getComponentPointer(window))
            dwm.DwmSetWindowAttribute(hwnd, USE_IMMERSIVE_DARK_MODE, IntByReference(1).pointer, 4)
            // Windows 11: the caption in Wave's own background color (COLORREF is 0x00BBGGRR).
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            dwm.DwmSetWindowAttribute(hwnd, CAPTION_COLOR, IntByReference((b shl 16) or (g shl 8) or r).pointer, 4)
        }
    }
}
