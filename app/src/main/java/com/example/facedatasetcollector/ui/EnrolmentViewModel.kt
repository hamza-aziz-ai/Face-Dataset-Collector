package com.example.facedatasetcollector.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.facedatasetcollector.BuildConfig
import com.example.facedatasetcollector.data.ApiException
import com.example.facedatasetcollector.data.ApiFailure
import com.example.facedatasetcollector.data.EnrolmentApi
import com.example.facedatasetcollector.data.InvalidServerAddress
import com.example.facedatasetcollector.data.Notice
import com.example.facedatasetcollector.data.Person
import com.example.facedatasetcollector.data.Pose
import com.example.facedatasetcollector.data.ServerStore
import com.example.facedatasetcollector.data.StoredServer
import com.example.facedatasetcollector.data.normalizeBaseUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Screen {
    data object Connect : Screen
    data object SignIn : Screen
    data object ClientChanged : Screen
    data object SelectPerson : Screen
    data object Consent : Screen

    data object Capture : Screen
    data object Review : Screen
}

/** Owns navigation and the session. The session lives only here, so losing the process signs staff out (docs/DATA.md). */
class EnrolmentViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ServerStore(application)
    private var api: EnrolmentApi? = null

    var server by mutableStateOf(store.load())
        private set
    var screen by mutableStateOf<Screen>(if (server == null) Screen.Connect else Screen.SignIn)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    var persons by mutableStateOf<List<Person>>(emptyList())
        private set
    var hasMorePersons by mutableStateOf(false)
        private set
    var selectedPerson by mutableStateOf<Person?>(null)
        private set
    var notice by mutableStateOf<Notice?>(null)
        private set

    /** The active enrolment. Consent stays valid across retakes, so a rejected set reuses it. */
    var consentId: String? = null
        private set
    var wearsGlasses = false
        private set

    /** JPEG bytes, in memory only (D-002). Never written to disk, and lost if the process dies. */
    val photos = mutableStateMapOf<Pose, ByteArray>()
    var poseSet by mutableStateOf<List<Pose>>(emptyList())
        private set
    var capturePose by mutableStateOf<Pose?>(null)
        private set

    private var retaking = false
    private var backgroundedAt: Long? = null
    private var nextCursor: String? = null
    private var lastQuery = ""
    private var searchJob: Job? = null

    init {
        api = server?.let { EnrolmentApi(it.baseUrl) }
    }

    /** First-time setup. Trusts the server's identity only after it answers. */
    fun connect(address: String) = run {
        val baseUrl = normalizeBaseUrl(address, allowLoopbackHttp = BuildConfig.DEBUG)
        val client = EnrolmentApi(baseUrl)
        val info = client.serverInfo()
        val connected = StoredServer(baseUrl, info.clientId, info.clientName)
        store.save(connected)
        onMain {
            api = client
            server = connected
            screen = Screen.SignIn
        }
    }

    fun signIn(username: String, password: String) = run {
        val client = api ?: return@run
        val expected = server ?: return@run
        // WHY before the password is sent: a changed server must never receive staff credentials.
        val info = client.serverInfo()
        if (!isSameClient(expected.clientId, info.clientId)) {
            onMain { screen = Screen.ClientChanged }
            return@run
        }
        client.login(username, password)
        onMain { screen = Screen.SelectPerson }
    }

    /** Replaces any running search, so an older answer can never overwrite a newer one. */
    fun search(query: String) {
        val client = api ?: return
        searchJob?.cancel()
        lastQuery = query
        searchJob = viewModelScope.launch {
            try {
                val page = withContext(Dispatchers.IO) { client.persons(query) }
                persons = page.persons
                nextCursor = page.nextCursor
                hasMorePersons = page.nextCursor != null
                error = null
            } catch (e: ApiException) {
                onFailure(e)
            }
        }
    }

    fun loadMorePersons() = run {
        val client = api ?: return@run
        val cursor = nextCursor ?: return@run
        val page = client.persons(lastQuery, cursor)
        onMain {
            persons = persons + page.persons
            nextCursor = page.nextCursor
            hasMorePersons = page.nextCursor != null
        }
    }

    fun selectPerson(person: Person) = run {
        val client = api ?: return@run
        val info = client.serverInfo()
        val current = client.notice(info.noticeUrl)
        onMain {
            selectedPerson = person
            notice = current
            screen = Screen.Consent
        }
    }

    /** Nothing is recorded when a person declines. */
    fun personDeclined() {
        clearEnrolment()
        screen = Screen.SelectPerson
    }

    fun recordConsent(glasses: Boolean) = run {
        val client = api ?: return@run
        val person = selectedPerson ?: return@run
        val version = notice?.version ?: return@run
        val id = client.createConsent(person.personId, version, utcTimestamp(System.currentTimeMillis()))
        onMain {
            consentId = id
            wearsGlasses = glasses
            poseSet = Pose.setFor(glasses)
            startCapture()
        }
    }

    private fun startCapture() {
        photos.clear()
        retaking = false
        capturePose = poseSet.firstOrNull()
        screen = Screen.Capture
    }

    /** Ignores a stale photo: one that arrives after cancel or for a pose no longer being captured. */
    fun onPhoto(pose: Pose, jpeg: ByteArray) {
        if (screen != Screen.Capture || pose != capturePose) return
        photos[pose] = jpeg
        val next = if (retaking) null else poseSet.getOrNull(poseSet.indexOf(pose) + 1)
        capturePose = next
        if (next == null) {
            retaking = false
            screen = Screen.Review
        }
    }

    fun retake(pose: Pose) {
        retaking = true
        capturePose = pose
        screen = Screen.Capture
    }

    /** Clears the photos. The consent stays on record, so returning to this person needs no new notice flow beyond the roster. */
    fun cancelCapture() {
        finishEnrolment()
    }

    fun onBackgrounded(nowMs: Long) {
        backgroundedAt = nowMs
    }

    /** Protects a device left unattended: the photos and the session go after [QualityPolicy.BACKGROUND_LIMIT_MS]. */
    fun onForegrounded(nowMs: Long) {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (nowMs - since > QualityPolicy.BACKGROUND_LIMIT_MS && screen != Screen.SignIn && screen != Screen.Connect) {
            signOut()
            error = "Signed out after time away. Any photos were deleted."
        }
    }

    /** Leaves the current enrolment and returns to a fresh roster. */
    fun finishEnrolment() {
        clearEnrolment()
        screen = Screen.SelectPerson
    }

    fun signOut() {
        api?.signOut()
        clearEnrolment()
        persons = emptyList()
        hasMorePersons = false
        nextCursor = null
        error = null
        screen = Screen.SignIn
    }

    /** Forgets this device's server, so that setup starts again. */
    fun resetServer() {
        signOut()
        api = null
        store.clear()
        server = null
        error = null
        screen = Screen.Connect
    }

    val serverHost: String? get() = server?.baseUrl?.toUri()?.host

    val isMock: Boolean get() = BuildConfig.DEBUG && (serverHost == "127.0.0.1" || serverHost == "localhost")

    private fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: InvalidServerAddress) {
                error = e.message
            } catch (e: ApiException) {
                onFailure(e)
            } finally {
                busy = false
            }
        }
    }

    private fun onFailure(e: ApiException) {
        val sessionEnded = e.failure == ApiFailure.UNAUTHORIZED && screen != Screen.SignIn
        if (sessionEnded) signOut()
        error = if (sessionEnded) "Your session ended. Sign in again." else messageFor(e.failure)
    }

    private fun clearEnrolment() {
        selectedPerson = null
        notice = null
        consentId = null
        wearsGlasses = false
        photos.clear()
        poseSet = emptyList()
        capturePose = null
        retaking = false
    }

    private suspend fun onMain(block: () -> Unit) = withContext(Dispatchers.Main) { block() }
}
