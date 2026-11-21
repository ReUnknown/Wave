package app.wave.desktop

import androidx.compose.foundation.layout.Box
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
import app.wave.music.ui.theme.WaveTheme
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/** Development check: renders the mini player's sizes to PNGs. */
/** Nothing playing. */
private fun idleBackend(): PlayerBackend = object : PlayerBackend {
    override val state = MutableStateFlow(PlayerUiState())
    override val position = 0L
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

private fun fakeBackend(): PlayerBackend {
    val songs = runBlocking { YouTube.search("blinding lights", YouTube.SearchFilter.FILTER_SONG).getOrThrow().items.filterIsInstance<SongItem>().take(8) }
    return object : PlayerBackend {
        override val state = MutableStateFlow(run {
            val q = songs.map { NowPlaying(it.id, it.title, it.artists.joinToString { a -> a.name }, it.thumbnail) }
            PlayerUiState(current = q.first(), isPlaying = true, durationMs = 204_000, liked = false, queue = q, upcoming = q.indices.drop(1), index = 0)
        })
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
        override fun reset() { state.value = PlayerUiState() }
    }
}

object RenderApp {
    @JvmStatic
    fun main(args: Array<String>) {
        WavePlatform.current = DesktopPlatform
        app.wave.music.ui.components.BlurBudget.light = true
        kotlinx.coroutines.runBlocking { app.wave.music.data.WaveSettings.load() }
        val mode = args.getOrNull(3) ?: "home"
        val w = args.getOrNull(1)?.toIntOrNull() ?: 1120
        val h = args.getOrNull(2)?.toIntOrNull() ?: 740
        PlayerConnection.backend = if (mode == "home") idleBackend() else fakeBackend()
        when (mode) {
            "topsearch" -> app.wave.music.data.WaveSettings.preview { it.copy(topSearch = true) }
            "split" -> app.wave.music.data.WaveSettings.preview { it.copy(separateMiniPlayer = true) }
            "legacy" -> app.wave.music.data.WaveSettings.preview { it.copy(legacyUi = true, topSearch = true) }
            "search" -> app.wave.music.data.WaveSettings.preview { it.copy(startTab = 2) }
            "lightplayer" -> app.wave.music.data.WaveSettings.preview { it.copy(theme = 1) }
            "legacylib" -> app.wave.music.data.WaveSettings.preview { it.copy(legacyUi = true, topSearch = true, startTab = 1) }
            "light" -> app.wave.music.data.WaveSettings.preview { it.copy(theme = 1) }
            "lighttop" -> app.wave.music.data.WaveSettings.preview { it.copy(theme = 1, topSearch = true, denseHome = true) }
            "legacybar" -> app.wave.music.data.WaveSettings.preview { it.copy(legacyUi = true, topSearch = false) }
        }
        if (mode.startsWith("opening")) app.wave.music.ui.home.RenderStart.openAfterMs = 6000
        if (mode.startsWith("intro")) {
            app.wave.music.ui.home.RenderStart.intro = true
            app.wave.music.data.WaveSettings.preview { it.copy(setupDone = true, seenV2 = false) }
        }
        if (mode == "tour") app.wave.music.ui.home.Tour.requested = true
        if (mode == "openingflat") app.wave.music.data.WaveSettings.preview { it.copy(blurLevel = 0) }
        if (mode == "openinglegacy") app.wave.music.data.WaveSettings.preview { it.copy(legacyUi = true, topSearch = true) }
        app.wave.music.ui.home.RenderStart.player = mode in setOf("player", "queue", "lyrics", "menu", "lightplayer")
        app.wave.music.ui.home.RenderStart.openTo = mode.takeIf { it == "queue" || it == "lyrics" }
        if (mode == "settings") app.wave.music.ui.home.RenderStart.page = app.wave.music.ui.detail.Destination.Settings
        if (mode.startsWith("page-")) app.wave.music.ui.home.RenderStart.page = app.wave.music.ui.detail.Destination.SettingsPage(mode.removePrefix("page-"))
        if (mode == "artist") app.wave.music.ui.home.RenderStart.page = app.wave.music.ui.detail.Destination.Artist("UClYV6hHlupm_S_ObS1W-DYw")
        if (mode == "album") app.wave.music.ui.home.RenderStart.page = app.wave.music.ui.detail.Destination.Album("MPREb_rzLvLCYSi3J")
        if (mode == "menu") {
            val now = PlayerConnection.state.value.current!!
            app.wave.music.ui.home.RenderStart.menu = app.wave.music.ui.menu.MenuTarget.Song(now.videoId, now.title, now.artist, now.artworkUrl, nowPlaying = true)
            // The player's ⋮, top right.
            app.wave.music.ui.menu.MenuAnchor.pressed = app.wave.music.ui.menu.MenuAnchor.Spot(androidx.compose.ui.geometry.Rect(w - 64f, 38f, w - 16f, 86f), androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.32f), androidx.compose.ui.graphics.Color.White)
            app.wave.music.ui.menu.MenuAnchor.at = Long.MAX_VALUE / 2
        }
        val out = File(args.getOrNull(0) ?: "build/mini").apply { mkdirs() }
        val scene = ImageComposeScene(w, h, Density(1f)) {
            androidx.compose.runtime.CompositionLocalProvider(app.wave.music.platform.LocalTitleBarInset provides 30.dp) {
                if (mode.startsWith("setup")) app.wave.music.ui.theme.WaveTheme {
                    app.wave.music.ui.welcome.SetupScreen(null, {}, {}, initialStep = mode.removePrefix("setup").toIntOrNull() ?: 0)
                } else app.wave.music.ui.WaveApp(computer = true, preview = true)
            }
        }
        var image = scene.render(0)
        if (mode == "dismiss") {
            Thread {
                Thread.sleep(6000)
                PlayerConnection.reset()
            }.start()
        }
        if (mode.startsWith("opening")) {
            // Open the player a few seconds in, then grab a frame part-way through the animation.
            var t = 0L
            while (app.wave.music.ui.home.RenderStart.openedAtNanos == 0L && t < 20_000_000_000L) { Thread.sleep(50); t += 50_000_000L; image = scene.render(t) }
            val at = app.wave.music.ui.home.RenderStart.openedAtNanos
            for (step in 1..4) { image = scene.render(at + step * 50_000_000L) }
            File(out, "app-$w-$mode.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
            println("done"); kotlin.system.exitProcess(0)
        }
        val frames = if (mode == "intromid") 21 else 150
        for (i in 1..frames) { Thread.sleep(100); image = scene.render(i * 100_000_000L) }
        File(out, "app-$w-$mode.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        println("done")
        kotlin.system.exitProcess(0)
    }
}
