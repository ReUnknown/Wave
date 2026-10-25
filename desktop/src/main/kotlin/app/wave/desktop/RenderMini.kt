package app.wave.desktop

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.wave.music.platform.WavePlatform
import app.wave.music.playback.NowPlaying
import app.wave.music.playback.PlayRequest
import app.wave.music.playback.PlayerBackend
import app.wave.music.playback.PlayerConnection
import app.wave.music.playback.PlayerUiState
import app.wave.music.ui.player.PopOutPlayer
import app.wave.music.ui.player.PopOutSize
import app.wave.music.ui.theme.WaveTheme
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/** Development check: renders the mini player's sizes to PNGs. */
fun main(args: Array<String>) {
    WavePlatform.current = DesktopPlatform
    val song = runBlocking { YouTube.search(args.firstOrNull() ?: "blinding lights", YouTube.SearchFilter.FILTER_SONG).getOrThrow().items.filterIsInstance<SongItem>().first() }
    val playing = args.getOrNull(1) != "paused"
    PlayerConnection.backend = object : PlayerBackend {
        override val state = MutableStateFlow(PlayerUiState(current = NowPlaying(song.id, song.title, song.artists.joinToString { it.name }, song.thumbnail), isPlaying = playing, durationMs = 204_000, liked = !playing))
        override val position = 112_000L
        override fun play(request: PlayRequest, startPositionMs: Long) {}
        override fun playNext(request: PlayRequest) {}
        override fun addToQueue(request: PlayRequest) {}
        override fun play() {}
        override fun togglePlay() {}
        override fun next() {}
        override fun previous() {}
        override fun nextSong() {}
        override fun previousSong() {}
        override fun seekTo(ms: Long) {}
        override fun skipTo(index: Int) {}
        override fun removeAt(index: Int) {}
        override fun move(from: Int, to: Int) {}
        override fun setSpeed(speed: Float) {}
        override fun toggleShuffle() {}
        override fun cycleRepeat() {}
        override fun reset() {}
    }
    val out = File(args.getOrNull(2) ?: "build/mini").apply { mkdirs() }
    // Each size, Bigger with its tray open, and tucked away.
    val shots = PopOutSize.entries.map { Triple(it.name, it, false) } + Triple("Bigger-tray", PopOutSize.Bigger, true) + Triple("Tucked", PopOutSize.Compact, false)
    shots.forEach { (name, size, tray) ->
        val tucked = name == "Tucked"
        val w = if (tucked) 56 else size.widthDp
        val h = if (tucked) 56 else size.heightDp + if (tray) app.wave.music.ui.player.POP_OUT_TRAY_DP else 0
        val scene = ImageComposeScene(w * 2, h * 2, Density(2f)) {
            WaveTheme { PopOutPlayer(size, {}, {}, {}, Modifier.size(w.dp, h.dp), tray = tray, tucked = tucked) }
        }
        var image = scene.render(0)
        for (i in 1..40) { Thread.sleep(100); image = scene.render(i * 100_000_000L) }
        File(out, "$name-${if (playing) "playing" else "paused"}.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
    }
    println("done")
    kotlin.system.exitProcess(0)
}

/** Development check: renders the whole app (as a guest) to a PNG. */
object RenderApp {
    @JvmStatic
    fun main(args: Array<String>) {
        WavePlatform.current = DesktopPlatform
        app.wave.music.ui.components.BlurBudget.light = true
        kotlinx.coroutines.runBlocking { app.wave.music.data.WaveSettings.load() }
        PlayerConnection.backend = DesktopPlayer
        val out = File(args.getOrNull(0) ?: "build/mini").apply { mkdirs() }
        val scene = ImageComposeScene(1120, 740, Density(1f)) {
            app.wave.music.ui.WaveApp(computer = true, preview = true)
        }
        var image = scene.render(0)
        for (i in 1..150) { Thread.sleep(100); image = scene.render(i * 100_000_000L) }
        File(out, "app-home.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        println("done")
        kotlin.system.exitProcess(0)
    }
}
