package app.wave.desktop

import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.platform.unix.X11
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WTypes
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.awt.Window

/**
 * Keeps a window (the mini player, WaveMini) on every virtual desktop, so it comes along when you
 * switch desktops.
 * - Linux: the window manager's own "on all workspaces" flag (_NET_WM_STATE_STICKY).
 * - Windows: Windows' documented virtual desktop API can't pin a window, but it can move one of
 *   Wave's own windows to a desktop: so when you switch, the window is moved to the desktop
 *   you're now on (the one the window in front of you is on).
 */
object Desktops {
    fun everywhere(window: Window) {
        when {
            DesktopPlatform.isWindows -> WindowsFollower.follow(window)
            !DesktopPlatform.isMac -> runCatching { sticky(window) }
        }
    }

    fun stop(window: Window) {
        if (DesktopPlatform.isWindows) WindowsFollower.unfollow(window)
    }

    // ---------------------------------------------------------------- Linux (X11, and XWayland)

    private fun sticky(window: Window) {
        val x = X11.INSTANCE
        val display = x.XOpenDisplay(null) ?: return
        try {
            val id = X11.Window(Native.getComponentID(window))
            val root = x.XDefaultRootWindow(display)
            val state = x.XInternAtom(display, "_NET_WM_STATE", false)
            val stickyAtom = x.XInternAtom(display, "_NET_WM_STATE_STICKY", false)
            val desktop = x.XInternAtom(display, "_NET_WM_DESKTOP", false)
            // Ask the window manager: add "sticky" to the window's state...
            send(x, display, root, id, state, longArrayOf(1, stickyAtom.toLong(), 0, 1, 0))
            // ...and put it on "all desktops" (0xFFFFFFFF), which some window managers prefer.
            send(x, display, root, id, desktop, longArrayOf(0xFFFFFFFFL, 1, 0, 0, 0))
            x.XFlush(display)
        } finally {
            x.XCloseDisplay(display)
        }
    }

    private fun send(x: X11, display: X11.Display, root: X11.Window, window: X11.Window, type: X11.Atom, data: LongArray) {
        val event = X11.XEvent()
        val msg = X11.XClientMessageEvent()
        msg.type = X11.ClientMessage
        msg.serial = NativeLong(0)
        msg.send_event = 1
        msg.display = display
        msg.window = window
        msg.message_type = type
        msg.format = 32
        for (i in data.indices) msg.data.l[i] = NativeLong(data[i])
        event.setTypedValue(msg)
        x.XSendEvent(display, root, 0, NativeLong((X11.SubstructureRedirectMask or X11.SubstructureNotifyMask).toLong()), event)
    }

    // ---------------------------------------------------------------- Windows

    /** One background thread that checks every half second whether Wave's floating windows are on the current desktop. */
    private object WindowsFollower {
        private val windows = java.util.concurrent.CopyOnWriteArraySet<Window>()
        @Volatile private var thread: Thread? = null

        fun follow(window: Window) {
            windows += window
            if (thread?.isAlive == true) return
            thread = Thread(::run, "Wave desktops").apply { isDaemon = true; start() }
        }

        fun unfollow(window: Window) {
            windows -= window
        }

        private fun run() {
            Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED)
            val manager = runCatching { DesktopManager.create() }.getOrNull() ?: return
            while (windows.isNotEmpty()) {
                runCatching {
                    for (w in windows) {
                        if (!w.isShowing) continue
                        val hwnd = WinDef.HWND(Native.getComponentPointer(w))
                        if (manager.isOnCurrentDesktop(hwnd)) continue
                        // The window you're looking at is on the current desktop: go where it is.
                        val front = User32.INSTANCE.GetForegroundWindow() ?: continue
                        val desktop = manager.desktopOf(front) ?: continue
                        manager.moveTo(hwnd, desktop)
                    }
                }
                Thread.sleep(500)
            }
            thread = null
        }
    }

    /** IVirtualDesktopManager (documented, shobjidl_core.h). */
    private class DesktopManager(pointer: Pointer) : Unknown(pointer) {
        companion object {
            private val CLSID = Guid.CLSID("{aa509086-5ca9-4c25-8f95-589d3c07b48a}")
            private val IID = Guid.IID("{a5cd92ff-29be-454c-8d04-d82879fb3f1b}")

            fun create(): DesktopManager? {
                val out = PointerByReference()
                val hr = Ole32.INSTANCE.CoCreateInstance(CLSID, null, WTypes.CLSCTX_INPROC_SERVER or WTypes.CLSCTX_LOCAL_SERVER, IID, out)
                return if (hr.toInt() == 0 && out.value != null) DesktopManager(out.value) else null
            }
        }

        // vtable: 0-2 IUnknown, 3 IsWindowOnCurrentVirtualDesktop, 4 GetWindowDesktopId, 5 MoveWindowToDesktop
        fun isOnCurrentDesktop(hwnd: WinDef.HWND): Boolean {
            val on = IntByReference()
            val hr = _invokeNativeInt(3, arrayOf(pointer, hwnd, on))
            return hr != 0 || on.value != 0
        }

        fun desktopOf(hwnd: WinDef.HWND): Guid.GUID? {
            val id = Guid.GUID.ByReference()
            val hr = _invokeNativeInt(4, arrayOf(pointer, hwnd, id))
            return if (hr == 0 && id != Guid.GUID()) id else null
        }

        fun moveTo(hwnd: WinDef.HWND, desktop: Guid.GUID) {
            _invokeNativeInt(5, arrayOf(pointer, hwnd, Guid.GUID.ByReference(desktop)))
        }
    }
}
