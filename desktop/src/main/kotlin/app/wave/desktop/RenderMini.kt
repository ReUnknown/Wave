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
    PopOutSize.entries.forEach { size ->
        val scene = ImageComposeScene(size.widthDp * 2, size.heightDp * 2, Density(2f)) {
            WaveTheme { PopOutPlayer(size, {}, {}, {}, Modifier.size(size.widthDp.dp, size.heightDp.dp)) }
        }
        var image = scene.render(0)
        for (i in 1..40) { Thread.sleep(100); image = scene.render(i * 100_000_000L) }
        File(out, "${size.name}-${if (playing) "playing" else "paused"}.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
    }
    println("done")
    kotlin.system.exitProcess(0)
}
