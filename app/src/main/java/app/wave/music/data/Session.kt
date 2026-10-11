package app.wave.music.data

import android.content.Context
import android.webkit.CookieManager
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.metrolist.innertube.YouTube
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/** What we keep after a successful Google sign-in. */
data class Session(
    val cookie: String,
    val visitorData: String,
    val dataSyncId: String,
    val authUser: String,
    val name: String,
    val email: String?,
    val photoUrl: String?,
)

private val Context.sessionStore by preferencesDataStore("session")

/**
 * Holds the signed-in YouTube Music session: persists it (cookie encrypted),
 * restores it at launch, and keeps the InnerTube client configured to match.
 */
object SessionRepository {
    private object Keys {
        val cookie = stringPreferencesKey("cookie_enc")
        val visitorData = stringPreferencesKey("visitor_data")
        val dataSyncId = stringPreferencesKey("data_sync_id")
        val authUser = stringPreferencesKey("auth_user")
        val name = stringPreferencesKey("name")
        val email = stringPreferencesKey("email")
        val photoUrl = stringPreferencesKey("photo_url")
    }

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    private val _restored = MutableStateFlow(false)
    val restored: StateFlow<Boolean> = _restored.asStateFlow()

    suspend fun restore(context: Context) {
        val prefs = context.sessionStore.data.first()
        val cookie = prefs[Keys.cookie]?.let(SecretBox::decrypt)
        val session = if (cookie != null) {
            Session(
                cookie = cookie,
                visitorData = prefs[Keys.visitorData].orEmpty(),
                dataSyncId = prefs[Keys.dataSyncId].orEmpty(),
                authUser = prefs[Keys.authUser] ?: "0",
                name = prefs[Keys.name].orEmpty(),
                email = prefs[Keys.email],
                photoUrl = prefs[Keys.photoUrl],
            )
        } else {
            null
        }
        apply(session)
        _restored.value = true
    }

    suspend fun save(context: Context, session: Session) {
        context.sessionStore.edit {
            it[Keys.cookie] = SecretBox.encrypt(session.cookie)
            it[Keys.visitorData] = session.visitorData
            it[Keys.dataSyncId] = session.dataSyncId
            it[Keys.authUser] = session.authUser
            it[Keys.name] = session.name
            session.email?.let { email -> it[Keys.email] = email }
            session.photoUrl?.let { url -> it[Keys.photoUrl] = url }
        }
        apply(session)
    }

    suspend fun signOut(context: Context) {
        context.sessionStore.edit { it.clear() }
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        apply(null)
    }

    /** Points the InnerTube client at the given account (or back to signed-out). */
    fun applyToClient(cookie: String?, visitorData: String?, dataSyncId: String?, authUser: String) {
        YouTube.cookie = cookie
        YouTube.visitorData = visitorData?.takeIf { it.isNotBlank() }
        YouTube.dataSyncId = dataSyncId?.takeIf { it.isNotBlank() }
        YouTube.authUser = authUser
        YouTube.useLoginForBrowse = cookie != null
    }

    private fun apply(session: Session?) {
        applyToClient(session?.cookie, session?.visitorData, session?.dataSyncId, session?.authUser ?: "0")
        _session.value = session
    }
}
