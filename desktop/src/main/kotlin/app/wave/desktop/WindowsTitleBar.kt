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

    /** Height of Wave's own title bar strip, in window units. */
    const val HEIGHT = 36

    private var titleBar: com.jetbrains.WindowDecorations.CustomTitleBar? = null

    /**
     * Wave draws all the way to the top of the window, like on macOS: the system's caption
     * buttons (minimize, maximize, close) float over Wave's colors, and the strip they sit in
     * still drags, snaps and double-click-maximizes the window. Works on the JetBrains Runtime
     * Wave ships with; returns false (keep the normal title bar) anywhere else.
     */
    fun extendIntoTitleBar(window: java.awt.Frame, dark: Boolean): Boolean = runCatching {
        if (!com.jetbrains.JBR.isWindowDecorationsSupported()) return false
        val decorations = com.jetbrains.JBR.getWindowDecorations()
        val bar = decorations.createCustomTitleBar().apply {
            height = HEIGHT.toFloat()
            putProperty("controls.dark", dark)
        }
        decorations.setCustomTitleBar(window, bar)
        titleBar = bar
        dragStrip(window, bar)
        true
    }.getOrDefault(false)

    /**
     * Wave draws under the title bar, so its own drawing would get the clicks there and the
     * window couldn't be dragged. Every mouse event in the top strip (left of the window buttons)
     * is marked as title bar, so Windows drags, snaps and double-click-maximizes as usual.
     */
    private fun dragStrip(window: java.awt.Frame, bar: com.jetbrains.WindowDecorations.CustomTitleBar) {
        java.awt.Toolkit.getDefaultToolkit().addAWTEventListener({ event ->
            val e = event as? java.awt.event.MouseEvent ?: return@addAWTEventListener
            if (javax.swing.SwingUtilities.getWindowAncestor(e.component) !== window && e.component !== window) return@addAWTEventListener
            val p = javax.swing.SwingUtilities.convertPoint(e.component, e.point, window)
            val buttons = bar.rightInset.toInt().coerceAtLeast(140)
            if (p.y in 0 until HEIGHT && p.x < window.width - buttons) runCatching { bar.forceHitTest(false) }
        }, java.awt.AWTEvent.MOUSE_EVENT_MASK or java.awt.AWTEvent.MOUSE_MOTION_EVENT_MASK)
    }

    /** Light or dark caption buttons, to suit what's under them. */
    private fun controls(window: Window, dark: Boolean): Boolean {
        val bar = titleBar ?: return false
        val frame = window as? java.awt.Frame ?: return false
        return runCatching {
            if (bar.properties["controls.dark"] != dark) {
                bar.putProperty("controls.dark", dark)
                com.jetbrains.JBR.getWindowDecorations().setCustomTitleBar(frame, bar)
            }
            true
        }.getOrDefault(false)
    }

    private val dwm by lazy { runCatching { Native.load("dwmapi", Dwm::class.java) }.getOrNull() }

    fun apply(window: Window, argb: Int, dark: Boolean = true) {
        // Wave's own title bar: only the buttons need to suit the colors.
        if (controls(window, dark)) return
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
