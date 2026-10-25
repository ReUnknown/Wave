package app.wave.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.wave.music.data.Session
import app.wave.music.data.SessionRepository
import coil3.compose.AsyncImage
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

internal data class PageAuth(val cookie: String, val visitorData: String, val dataSyncId: String, val authUser: String)

/**
 * Google sign-in on computers, on the web engine the computer already has (Safari's WebKit on
 * macOS, Edge WebView2 on Windows, WebKitGTK on Linux): Wave opens a small sign-in window (a
 * tiny helper program shipped with Wave), you sign in to Google there, and the helper hands
 * back the YouTube Music session. Nothing is downloaded and your password never touches Wave.
 */
internal object NativeSignIn {
    /** The helper for this computer, shipped in Wave's app resources. */
    private fun helper(): List<String>? {
        val dir = System.getProperty("compose.application.resources.dir")?.let(::File) ?: return null
        return when {
            DesktopPlatform.isMac -> File(dir, "WaveSignIn").takeIf { it.exists() }?.let { listOf(it.absolutePath) }
            DesktopPlatform.isWindows -> File(dir, "WaveSignIn.exe").takeIf { it.exists() }?.let { listOf(it.absolutePath) }
            else -> File(dir, "wave-signin.py").takeIf { it.exists() }?.let { listOf("python3", it.absolutePath) }
        }
    }

    val available: Boolean get() = helper() != null

    /** Runs the sign-in window and waits for it: the session, or null if it was closed. */
    suspend fun signIn(url: String? = null): PageAuth? = withContext(Dispatchers.IO) {
        val command = helper() ?: return@withContext null
        val process = ProcessBuilder(command + listOfNotNull(url)).redirectErrorStream(false).start()
        val line = process.inputStream.bufferedReader().readLine()
        process.waitFor()
        val values = line?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() } ?: return@withContext null
        fun get(key: String) = values[key]?.jsonPrimitive?.content.orEmpty()
        PageAuth(get("cookie"), get("visitorData"), get("dataSyncId").substringBefore("||"), get("authUser").filter(Char::isDigit).ifBlank { "0" })
            .takeIf { it.cookie.isNotBlank() && it.visitorData.isNotBlank() }
    }

    /** Sign-out: the sign-in window forgets the web session too. */
    fun signOut() {
        val command = helper() ?: return
        runCatching { ProcessBuilder(command + "--sign-out").start().waitFor() }
    }
}

object DesktopLoginScreen {
    fun forgetCookies() = NativeSignIn.signOut()
}

private enum class Stage { Waiting, PickChannel, Finishing }

/** While the sign-in window is open: what's happening, and the channel picker afterwards. */
@Composable
fun DesktopLoginScreen(onDone: () -> Unit, onCancel: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var stage by remember { mutableStateOf(Stage.Waiting) }
    var error by remember { mutableStateOf<String?>(null) }
    var channels by remember { mutableStateOf<List<YouTubeAccount>>(emptyList()) }
    var pendingAuth by remember { mutableStateOf<PageAuth?>(null) }
    var chosen by remember { mutableStateOf<YouTubeAccount?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    suspend fun finish(auth: PageAuth) {
        stage = Stage.Finishing
        SessionRepository.applyToClient(auth.cookie, auth.visitorData, auth.dataSyncId, auth.authUser)
        val account = withContext(Dispatchers.IO) { YouTube.accountInfo().getOrNull() }
        if (account == null) {
            error = "Signed in, but couldn't load your YouTube Music account. Try again."
            return
        }
        SessionRepository.save(Session(auth.cookie, auth.visitorData, auth.dataSyncId, auth.authUser, account.name, account.email, account.thumbnailUrl))
        onDone()
    }

    LaunchedEffect(attempt) {
        stage = Stage.Waiting
        if (!NativeSignIn.available) {
            error = "Wave's sign-in window is missing from this install. Reinstalling Wave fixes it."
            return@LaunchedEffect
        }
        val auth = NativeSignIn.signIn() ?: return@LaunchedEffect onCancel()
        SessionRepository.applyToClient(auth.cookie, auth.visitorData, auth.dataSyncId, auth.authUser)
        val list = withContext(Dispatchers.IO) { YouTube.accountsList().getOrNull().orEmpty() }
        if (list.size > 1) {
            pendingAuth = auth
            channels = list
            stage = Stage.PickChannel
        } else {
            finish(auth)
        }
    }

    Column(
        Modifier.fillMaxSize().background(c.background).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LoadingIndicator(Modifier.size(80.dp))
        Text(
            if (stage == Stage.Finishing) "Connecting your YouTube Music…" else "Sign in to Google in the window that opened",
            style = MaterialTheme.typography.titleMedium,
            color = c.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            "Google handles your password. Wave never sees it.",
            color = c.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (stage == Stage.Waiting) {
            TextButton(onClick = onCancel, modifier = Modifier.padding(top = 16.dp)) { Text("Cancel") }
        }
    }

    if (stage == Stage.PickChannel) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Which channel?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    channels.forEach { account ->
                        val selected = account == (chosen ?: channels.firstOrNull { it.isSelected })
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (selected) c.primaryContainer else c.surfaceContainerHigh)
                                .clickable { chosen = account }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AsyncImage(account.thumbnailUrl, null, Modifier.size(40.dp).clip(CircleShape))
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
                    val pick = chosen ?: channels.firstOrNull { it.isSelected } ?: channels.first()
                    scope.launch {
                        if (pick.isSelected || pick.signinUrl == null) {
                            finish(auth)
                        } else {
                            // Switch the web session to that channel, then read it again.
                            stage = Stage.Waiting
                            val switched = NativeSignIn.signIn(pick.signinUrl)
                            if (switched == null) onCancel()
                            else finish(switched.copy(dataSyncId = pick.dataSyncId?.substringBefore("||")?.ifBlank { null } ?: switched.dataSyncId))
                        }
                    }
                }) { Text("Continue") }
            },
        )
    }

    error?.let { message ->
        AlertDialog(
            onDismissRequest = { error = null; onCancel() },
            title = { Text("Sign-in problem") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    error = null
                    attempt++
                }) { Text("Try again") }
            },
            dismissButton = { TextButton(onClick = { error = null; onCancel() }) { Text("Cancel") } },
        )
    }
}
