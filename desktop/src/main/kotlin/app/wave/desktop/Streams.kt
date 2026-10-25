package app.wave.desktop

import com.metrolist.innertube.YouTube
import com.metrolist.innertubex.InnerTubeLogLevel
import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.PoTokenResult
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.TokenProviderCapabilities
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/** A playable audio link for a song. */
class Stream(val url: String, val headers: Map<String, String>, val loudnessDb: Double?)

/**
 * Turns a song id into a playable link, the same way the Android app does, minus Android's
 * WebView: with no proof-of-origin token, InnerTubeX sticks to YouTube clients that don't need one.
 */
object Streams {
    private val mutex = Mutex()
    private var extractor: InnerTubeExtractor? = null
    private var generation = -1L

    private val logger = InnerTubeLogger { event ->
        if (event.level >= InnerTubeLogLevel.WARN) System.err.println("[${event.tag}] ${event.message}")
    }

    /** The player config cache lives in Wave's data folder, like Android's preferences. */
    private val configRepository = object : PlayerConfigRepository {
        private val file = File(AppDirs.data, "player_config.json")
        override val enabled = true
        override val sourceUrl = CONFIG_URL
        override val defaultSourceUrl = CONFIG_URL
        override var cachedJson: String
            get() = runCatching { file.readText() }.getOrDefault("")
            set(value) { runCatching { file.writeText(value) } }
        override var cachedAtMs = 0L
        override var cachedSourceUrl = ""
        override var cachedEtag = ""
    }

    private val noTokens = object : TokenProvider {
        override val capabilities = TokenProviderCapabilities(emptySet(), usesWebView = false)
        override suspend fun getPoToken(videoId: String, visitorData: String, cookie: String?): PoTokenResult? = null
        override suspend fun close() = Unit
    }

    private suspend fun extractor(): InnerTubeExtractor = mutex.withLock {
        val transport = YouTube.extractionTransport()
        extractor?.takeIf { generation == transport.generation }?.let { return it }
        val store = RemotePlayerConfigStore(transport.httpClient, configRepository, logger)
        val cipher = YouTubeCipherService(transport.httpClient, store, logger)
        InnerTubeExtractor(
            configParser = YtConfigParserImpl(transport.httpClient, transport.innerTube, store, logger),
            cipherService = cipher,
            innerTube = transport.innerTube,
            tokenProvider = noTokens,
            logger = logger,
        ).also {
            extractor = it
            generation = transport.generation
        }
    }

    suspend fun resolve(videoId: String): Stream {
        val stream = requireNotNull(
            extractor().extract(
                videoId = videoId,
                hints = ContentHints().withStreamCapabilities(allowHls = false, allowSabr = false, allowBoundedRange = true),
                excludedClients = emptySet(),
                audioQuality = AudioQuality.HIGH,
                clientPlaybackNonce = generateClientPlaybackNonce(),
            ),
        ) { "No playable stream" }
        return Stream(stream.audioUrl, stream.headers, stream.loudnessDb)
    }

    private const val CONFIG_URL =
        "https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json"
}

/** Where Wave keeps its files on this computer. */
object AppDirs {
    val data: File by lazy {
        val home = System.getProperty("user.home")
        val os = System.getProperty("os.name").lowercase()
        val dir = when {
            os.contains("mac") -> File(home, "Library/Application Support/Wave")
            os.contains("win") -> File(System.getenv("APPDATA") ?: home, "Wave")
            else -> File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "wave")
        }
        dir.apply { mkdirs() }
    }
}
