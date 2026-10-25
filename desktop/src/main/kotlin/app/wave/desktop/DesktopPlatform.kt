package app.wave.desktop

import app.wave.music.platform.Platform
import app.wave.music.ui.components.Notices
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI

/** Wave on Windows, macOS and Linux. */
object DesktopPlatform : Platform {
    private val os = System.getProperty("os.name").lowercase()
    val isMac = os.contains("mac")
    val isWindows = os.contains("win")

    override val dataDir: File get() = AppDirs.data
    override val isComputer = true
    override val hasKeyboard = true
    override val scalesInterface = true
    override val appVersion: String = System.getProperty("wave.version") ?: WaveVersion.NAME
    override val debug: Boolean = System.getProperty("wave.debug") == "true"
    /** Running as a Flatpak (Linux). */
    val isFlatpak = System.getenv("FLATPAK_ID") != null

    override val updateAssetSuffix = when {
        isMac -> ".dmg"
        isWindows -> ".exe"
        else -> ".flatpak"
    }

    /** Computers have no share sheet: the link is copied, ready to paste anywhere. */
    override fun share(title: String, url: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(url), null)
        Notices.show("Link copied")
    }

    override fun openUrl(url: String) {
        runCatching { Desktop.getDesktop().browse(URI(url)) }
    }

    /** Windows for now (and development builds, to work on it anywhere). */
    override val hasMiniPlayer get() = isWindows || debug
    override fun openMiniPlayer() {
        // Always opens as the player, even if it was tucked away last time.
        MiniPlayer.tucked.value = false
        MiniPlayer.open.value = true
    }

    override fun forgetAccount() = DesktopLoginScreen.forgetCookies()

    /** Opens the downloaded installer; the system takes it from there. */
    override fun installUpdate(file: File) {
        runCatching {
            when {
                // Windows: installs over this copy with just a progress bar, then reopens Wave. It's
                // started on its own (not as Wave's child process), so closing Wave can't take it along.
                isWindows -> {
                    ProcessBuilder("cmd.exe", "/c", "start", "", file.absolutePath, "/SILENT", "/SUPPRESSMSGBOXES", "/NORESTART", "/RELAUNCH=1").start()
                    quit()
                }
                // Inside Flatpak the host's flatpak installs the new bundle over this one.
                isFlatpak -> ProcessBuilder("flatpak-spawn", "--host", "flatpak", "install", "--user", "--noninteractive", "--reinstall", file.absolutePath).start()
                else -> Desktop.getDesktop().open(file)
            }
        }
    }
}
