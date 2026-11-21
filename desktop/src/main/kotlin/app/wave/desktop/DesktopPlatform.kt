package app.wave.desktop

import app.wave.music.platform.Platform
import java.awt.Desktop
import java.io.File
import java.net.URI

/**
 * Just enough of a platform to render Wave offscreen on a computer (the preview pictures).
 * The computer app itself was retired at Wave 1.2.1; Wave Prisma is Android only.
 */
object DesktopPlatform : Platform {
    private val home = System.getProperty("user.home")
    override val dataDir: File = File(home, "Library/Application Support/Wave").let { mac ->
        if (System.getProperty("os.name").lowercase().contains("mac")) mac else File(home, ".wave-preview")
    }.apply { mkdirs() }
    override val isComputer = true
    override val hasKeyboard = true
    override val appVersion: String = "preview"
    override val debug: Boolean = true
    override val updateAssetSuffix = ".apk"

    override fun share(title: String, url: String) {}
    override fun openUrl(url: String) {
        runCatching { Desktop.getDesktop().browse(URI(url)) }
    }
    override fun forgetAccount() {}
    override fun installUpdate(file: File) {}
}
