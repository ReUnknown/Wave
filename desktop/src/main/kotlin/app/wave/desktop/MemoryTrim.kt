package app.wave.desktop

import coil3.SingletonImageLoader
import coil3.PlatformContext
import com.sun.jna.Native
import com.sun.jna.platform.win32.Kernel32
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * When Wave is out of sight (minimized, the mini player alone, or in the background for a
 * minute) it hands memory back: decoded covers are dropped (they reload from disk in a blink),
 * the heap is compacted and shrunk, and on Windows the pages it isn't touching leave its working
 * set (what Task Manager shows), the way browsers do with background tabs. Music keeps playing.
 */
object MemoryTrim {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pending: Job? = null
    private var sleeping: Job? = null

    /**
     * Minimized for a couple of minutes: the interface itself is let go (every screen and cover;
     * only the music keeps going) and rebuilt the moment Wave is brought back.
     */
    val dormant = kotlinx.coroutines.flow.MutableStateFlow(false)

    fun minimized() {
        soon()
        sleeping?.cancel()
        sleeping = scope.launch {
            delay(120_000)
            dormant.value = true
            delay(2_000)
            trim()
        }
    }

    fun restored() {
        sleeping?.cancel()
        dormant.value = false
    }

    /** Shortly (once the window is gone from view). */
    fun soon() = schedule(1_500)

    /** In the background for a while. */
    fun later() = schedule(60_000)

    fun cancel() {
        pending?.cancel()
        pending = null
    }

    private fun schedule(ms: Long) {
        pending?.cancel()
        pending = scope.launch {
            delay(ms)
            trim()
        }
    }

    private fun trim() {
        runCatching { SingletonImageLoader.get(PlatformContext.INSTANCE).memoryCache?.clear() }
        // Twice: the first frees, the second compacts what's left so the heap can shrink.
        System.gc()
        System.gc()
        if (DesktopPlatform.isWindows) runCatching {
            val k32 = Native.load("kernel32", Psapi::class.java)
            k32.SetProcessWorkingSetSize(Kernel32.INSTANCE.GetCurrentProcess(), -1, -1)
        }
    }

    private interface Psapi : com.sun.jna.Library {
        fun SetProcessWorkingSetSize(process: com.sun.jna.platform.win32.WinNT.HANDLE, min: Long, max: Long): Boolean
    }
}
