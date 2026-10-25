package app.wave.music.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
) {
    /** Using Wave without an account: YouTube Music's public home, search and playback; likes stay on this device. */
    val guest: Boolean get() = cookie.isEmpty()
}


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
        val guest = androidx.datastore.preferences.core.booleanPreferencesKey("guest")
    }

    private val guestSession = Session(cookie = "", visitorData = "", dataSyncId = "", authUser = "0", name = "Guest", email = null, photoUrl = null)

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    private val _restored = MutableStateFlow(false)
    val restored: StateFlow<Boolean> = _restored.asStateFlow()

    /** Debug preview session in use: nothing may replace it with the (empty) saved one. */
    @Volatile private var previewing = false

    suspend fun restore() {
        if (previewing) return
        val prefs = app.wave.music.data.Stores.prefs("session").data.first()
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
        } else if (prefs[Keys.guest] == true) {
            guestSession
        } else {
            null
        }
        if (previewing) return
        apply(session)
        _restored.value = true
    }

    /**
     * Debug builds only, for screenshots on emulators: browse YouTube Music's public home as a
     * guest, without signing in. Nothing is saved.
     */
    fun startPreview() {
        previewing = true
        applyToClient(null, null, null, "0")
        _session.value = guestSession.copy(name = "Preview")
        _restored.value = true
    }

    /** Use Wave without an account (remembered until you sign in). */
    suspend fun startGuest() {
        app.wave.music.data.Stores.prefs("session").edit { it.clear(); it[Keys.guest] = true }
        apply(guestSession)
    }

    suspend fun save(session: Session) {
        app.wave.music.data.Stores.prefs("session").edit {
            it.remove(Keys.guest)
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

    suspend fun signOut() {
        app.wave.music.data.Stores.prefs("session").edit { it.clear() }
        app.wave.music.platform.WavePlatform.current.forgetAccount()
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

    private val _signInRequested = MutableStateFlow(false)
    /** Something asked to sign in (a guest tapping "Sign in with Google" anywhere in Wave). */
    val signInRequested: StateFlow<Boolean> = _signInRequested.asStateFlow()
    fun requestSignIn() { _signInRequested.value = true }
    fun signInHandled() { _signInRequested.value = false }

    /** Signed in to nothing (a guest, or the preview). */
    val isGuest: Boolean get() = _session.value?.guest != false

    private fun apply(session: Session?) {
        applyToClient(session?.cookie?.takeIf { it.isNotEmpty() }, session?.visitorData, session?.dataSyncId, session?.authUser ?: "0")
        _session.value = session
    }
}
