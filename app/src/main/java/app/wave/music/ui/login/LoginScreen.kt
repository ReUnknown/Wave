package app.wave.music.ui.login

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.wave.music.data.Session
import app.wave.music.data.SessionRepository
import coil3.compose.AsyncImage
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeAccount
import com.metrolist.innertube.utils.parseCookieString
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val SIGN_IN_URL =
    "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"

private data class PageAuth(val cookie: String, val visitorData: String, val dataSyncId: String, val authUser: String)

private enum class Stage { SignIn, Reading, PickChannel, Switching, Finishing }

/** Receives values read out of music.youtube.com's page config. */
private class PageBridge {
    var onValue: ((String, String?) -> Unit)? = null

    @JavascriptInterface
    fun report(key: String, value: String?) {
        onValue?.invoke(key, value)
    }
}

/**
 * Google sign-in. Google's own login page runs in a WebView; once it lands on
 * music.youtube.com we read the session cookie and page config, then fetch the account.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(onDone: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bridge = remember { PageBridge() }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var stage by remember { mutableStateOf(Stage.SignIn) }
    var error by remember { mutableStateOf<String?>(null) }
    var channels by remember { mutableStateOf<List<YouTubeAccount>>(emptyList()) }
    var pendingAuth by remember { mutableStateOf<PageAuth?>(null) }
    var reading by remember { mutableStateOf(false) }
    var chosenChannel by remember { mutableStateOf<YouTubeAccount?>(null) }

    BackHandler {
        val view = webView
        if (stage == Stage.SignIn && view != null && view.canGoBack()) view.goBack() else onCancel()
    }

    suspend fun readPage(view: WebView): PageAuth? {
        repeat(20) {
            val cookie = CookieManager.getInstance().getCookie("https://music.youtube.com").orEmpty()
            val hasAuth = "SAPISID" in runCatching { parseCookieString(cookie) }.getOrDefault(emptyMap())
            val values = mutableMapOf<String, String?>()
            val done = CompletableDeferred<Unit>()
            bridge.onValue = { key, value ->
                values[key] = value
                if (values.size == 3 && !done.isCompleted) done.complete(Unit)
            }
            view.evaluateJavascript(
                """
                (function(){
                  var c = (window.yt && window.yt.config_) || {};
                  WaveBridge.report('visitorData', c.VISITOR_DATA || null);
                  WaveBridge.report('dataSyncId', c.DATASYNC_ID || null);
                  WaveBridge.report('authUser', String(c.SESSION_INDEX || 0));
                })();
                """.trimIndent(),
                null,
            )
            withTimeoutOrNull(1_000) { done.await() }
            val visitorData = values["visitorData"]
            if (hasAuth && !visitorData.isNullOrBlank()) {
                return PageAuth(
                    cookie = cookie,
                    visitorData = visitorData,
                    dataSyncId = values["dataSyncId"].orEmpty().substringBefore("||"),
                    authUser = values["authUser"].orEmpty().filter(Char::isDigit).ifBlank { "0" },
                )
            }
            delay(500)
        }
        return null
    }

    suspend fun finish(auth: PageAuth) {
        stage = Stage.Finishing
        SessionRepository.applyToClient(auth.cookie, auth.visitorData, auth.dataSyncId, auth.authUser)
        val account = YouTube.accountInfo().getOrNull()
        if (account == null) {
            error = "Signed in, but couldn't load your YouTube Music account. Try again."
            stage = Stage.SignIn
            return
        }
        SessionRepository.save(
            context,
            Session(
                cookie = auth.cookie,
                visitorData = auth.visitorData,
                dataSyncId = auth.dataSyncId,
                authUser = auth.authUser,
                name = account.name,
                email = account.email,
                photoUrl = account.thumbnailUrl,
            ),
        )
        webView?.apply {
            stopLoading()
            clearHistory()
            clearCache(true)
        }
        CookieManager.getInstance().flush()
        onDone()
    }

    fun onMusicPageLoaded(view: WebView) {
        if (stage != Stage.SignIn && stage != Stage.Switching && stage != Stage.Reading) return
        if (reading) return
        reading = true
        stage = Stage.Reading
        scope.launch {
            val auth = readPage(view)
            reading = false
            if (auth == null) {
                stage = Stage.SignIn
                error = "Couldn't finish connecting to YouTube Music. Try again."
                return@launch
            }
            val chosen = chosenChannel
            if (chosen != null) {
                // Came back from switching to a brand/secondary channel.
                finish(auth.copy(dataSyncId = chosen.dataSyncId?.substringBefore("||")?.ifBlank { null } ?: auth.dataSyncId))
                return@launch
            }
            SessionRepository.applyToClient(auth.cookie, auth.visitorData, auth.dataSyncId, auth.authUser)
            val list = YouTube.accountsList().getOrNull().orEmpty()
            if (list.size > 1) {
                pendingAuth = auth
                channels = list
                stage = Stage.PickChannel
            } else {
                finish(auth)
            }
        }
    }

    val connecting = stage != Stage.SignIn && stage != Stage.PickChannel
    val bg = MaterialTheme.colorScheme.background
    Column(
        Modifier
            .fillMaxSize()
            .background(bg)
            .safeDrawingPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, contentDescription = "Cancel sign-in") }
            Column(Modifier.padding(start = 4.dp)) {
                Text("Sign in with Google", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Google handles your password. Wave never sees it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (connecting) 0f else 1f),
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(bg.toArgb())
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true)
                        }
                        addJavascriptInterface(bridge, "WaveBridge")
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                                // Never show the YouTube Music website itself: cover it the moment Google hands off.
                                if (url?.let(Uri::parse)?.host == "music.youtube.com" && stage == Stage.SignIn) {
                                    stage = Stage.Reading
                                }
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                val uri = url?.let(Uri::parse) ?: return
                                if (uri.scheme == "https" && uri.host == "music.youtube.com") onMusicPageLoaded(view)
                            }
                        }
                        loadUrl(SIGN_IN_URL)
                        webView = this
                    }
                },
            )
        }
    }

    if (connecting) {
        Column(
            Modifier
                .fillMaxSize()
                .background(bg),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LoadingIndicator(Modifier.size(80.dp))
            Text(
                "Connecting your YouTube Music…",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
    }

    if (stage == Stage.PickChannel) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Which channel?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    channels.forEach { account ->
                        val selected = account == (chosenChannel ?: channels.firstOrNull { it.isSelected })
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                                )
                                .clickable { chosenChannel = account }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AsyncImage(
                                model = account.thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape),
                            )
                            Column {
                                Text(account.name, style = MaterialTheme.typography.titleMedium)
                                account.channelHandle?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val auth = pendingAuth ?: return@TextButton
                    val pick = chosenChannel ?: channels.firstOrNull { it.isSelected } ?: channels.first()
                    if (pick.isSelected || pick.signinUrl == null) {
                        chosenChannel = null
                        scope.launch { finish(auth) }
                    } else {
                        // Switch the web session to that channel, then read the page again.
                        chosenChannel = pick
                        stage = Stage.Switching
                        webView?.loadUrl(pick.signinUrl!!)
                    }
                }) { Text("Continue") }
            },
        )
    }

    error?.let { message ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("Sign-in problem") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    error = null
                    webView?.loadUrl(SIGN_IN_URL)
                }) { Text("Try again") }
            },
        )
    }
}
