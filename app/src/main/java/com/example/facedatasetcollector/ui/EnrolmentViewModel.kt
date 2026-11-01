package com.example.facedatasetcollector.ui

import android.app.Application
import android.os.SystemClock
import android.util.Log
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
import com.example.facedatasetcollector.data.JobResult
import com.example.facedatasetcollector.data.JobStatus
import com.example.facedatasetcollector.data.Notice
import com.example.facedatasetcollector.data.Person
import com.example.facedatasetcollector.data.Pose
import com.example.facedatasetcollector.data.PoseProblem
import com.example.facedatasetcollector.data.ReEnrolReason
import com.example.facedatasetcollector.data.ServerSetup
import com.example.facedatasetcollector.data.ServerStore
import com.example.facedatasetcollector.data.StoredServer
import com.example.facedatasetcollector.data.normalizeBaseUrl
import com.example.facedatasetcollector.data.parseSetupCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface Screen {
    data object Connect : Screen
    data object ScanCode : Screen
    data object SignIn : Screen
    data object ClientChanged : Screen
    data object SelectPerson : Screen
    data object Consent : Screen

    data object Capture : Screen
    data object Review : Screen
    data object Upload : Screen
}

sealed interface UploadState {
    data object Sending : UploadState
    data object Checking : UploadState
    data object Accepted : UploadState

    /** The bounded wait ended first. The result is safe on the server and appears on the roster. */
    data object StillChecking : UploadState
    data class Rejected(val problems: List<PoseProblem>) : UploadState
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

    /** Set only while re-enrolling. It travels with the upload, and the job then replaces the old enrolment if accepted. */
    var reEnrolReason: ReEnrolReason? = null
        private set

    /** JPEG bytes, in memory only (D-002). Never written to disk, and lost if the process dies. */
    val photos = mutableStateMapOf<Pose, ByteArray>()
    var poseSet by mutableStateOf<List<Pose>>(emptyList())
        private set
    var capturePose by mutableStateOf<Pose?>(null)
        private set

    var uploadState by mutableStateOf<UploadState?>(null)
        private set

    /** One per attempt at this exact set. A retry after an unknown outcome must reuse it, and a changed set must not. */
    private var uploadKey: String? = null
    private var retaking = false
    private var poseStartedAt = 0L
    private var backgroundedAt: Long? = null
    private var nextCursor: String? = null
    private var lastQuery = ""
    private var searchJob: Job? = null
    private var runJob: Job? = null
    private var timeoutJob: Job? = null

    init {
        api = server?.let { EnrolmentApi(it.baseUrl) }
    }

    /** First-time setup from a typed address. */
    fun connect(address: String) = run {
        connectTo(ServerSetup(normalizeBaseUrl(address, allowLoopbackHttp = BuildConfig.DEBUG), null))
    }

    fun startScan() {
        error = null
        screen = Screen.ScanCode
    }

    fun cancelScan() {
        screen = Screen.Connect
    }

    fun scanFailed() {
        screen = Screen.Connect
        error = "The camera could not start. Type the server address instead."
    }

    /** First-time setup from a scanned code. Any failure lands on the connect screen, where the typed fallback is. */
    fun connectWithCode(raw: String) {
        screen = Screen.Connect
        run { connectTo(parseSetupCode(raw, allowLoopbackHttp = BuildConfig.DEBUG)) }
    }

    /** Trusts the server's identity only after it answers. */
    private suspend fun connectTo(setup: ServerSetup) {
        val client = EnrolmentApi(setup.baseUrl)
        val info = client.serverInfo()
        setup.requireClient(info.clientId)
        val connected = StoredServer(setup.baseUrl, info.clientId, info.clientName)
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
        // The old cursor belongs to the old query, so paging waits for this answer.
        nextCursor = null
        hasMorePersons = false
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
        val query = lastQuery
        val page = client.persons(query, cursor)
        onMain {
            // A search started meanwhile owns the list now.
            if (query != lastQuery) return@onMain
            persons = persons + page.persons
            nextCursor = page.nextCursor
            hasMorePersons = page.nextCursor != null
        }
    }

    /** @param reason set for a re-enrolment, which still shows the notice and records a new consent. */
    fun selectPerson(person: Person, reason: ReEnrolReason? = null) = run {
        val client = api ?: return@run
        val expected = server ?: return@run
        val info = client.serverInfo()
        if (!isSameClient(expected.clientId, info.clientId)) {
            onMain { screen = Screen.ClientChanged }
            return@run
        }
        val current = client.notice(info.noticeUrl)
        onMain {
            selectedPerson = person
            reEnrolReason = reason
            notice = current
            screen = Screen.Consent
        }
    }

    /** Nothing is recorded when a person declines. */
    fun personDeclined() {
        // WHY: a consent already on its way cannot be recalled, so declining waits for its answer.
        if (busy) return
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
        uploadKey = null
        uploadState = null
        retaking = false
        capturePose = poseSet.firstOrNull()
        poseStartedAt = SystemClock.elapsedRealtime()
        screen = Screen.Capture
    }

    /** After a rejection. The server already deleted the set, and the consent stays valid, so only the photos are redone. */
    fun retakeAll() = startCapture()

    /** Ignores a stale photo: one that arrives after cancel or for a pose no longer being captured. */
    fun onPhoto(pose: Pose, jpeg: ByteArray) {
        if (screen != Screen.Capture || pose != capturePose) return
        // Timing and size only, with no person in the line.
        val now = SystemClock.elapsedRealtime()
        if (isMock) Log.d(TAG, "pose=${pose.wireName} poseMs=${now - poseStartedAt} photoBytes=${jpeg.size}")
        poseStartedAt = now
        photos[pose] = jpeg
        uploadKey = null
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
        poseStartedAt = SystemClock.elapsedRealtime()
        screen = Screen.Capture
    }

    /**
     * Sends the set, then waits for the checks. Photos clear only once the server answers 202, which is durable. Until then a failure returns to review with every photo intact.
     */
    fun upload() = run {
        val client = api ?: return@run
        val person = selectedPerson ?: return@run
        val consent = consentId ?: return@run
        val key = uploadKey ?: UUID.randomUUID().toString()
        var set: Map<Pose, ByteArray> = emptyMap()
        onMain {
            uploadKey = key
            set = photos.toMap()
            uploadState = UploadState.Sending
            screen = Screen.Upload
        }

        val started = SystemClock.elapsedRealtime()
        val jobId = try {
            client.upload(person.personId, consent, set, key, reEnrolReason)
        } catch (e: ApiException) {
            onMain {
                uploadState = null
                screen = Screen.Review
            }
            throw e
        }
        // Aggregate timing only, with no person or job in the line.
        Log.d(TAG, "uploadMs=${SystemClock.elapsedRealtime() - started} photos=${set.size}")
        onMain {
            photos.clear()
            uploadKey = null
            uploadState = UploadState.Checking
        }

        val checkStarted = SystemClock.elapsedRealtime()
        val result = try {
            client.awaitJob(jobId)
        } catch (e: ApiException) {
            // The photos are safely with the server, so a lost connection here is not a failed enrolment.
            if (e.failure == ApiFailure.UNAUTHORIZED) throw e
            JobResult(JobStatus.PENDING, emptyList())
        }
        Log.d(TAG, "checkMs=${SystemClock.elapsedRealtime() - checkStarted} status=${result.status}")
        onMain {
            uploadState = when (result.status) {
                JobStatus.ACCEPTED -> UploadState.Accepted
                JobStatus.REJECTED -> UploadState.Rejected(result.problems)
                JobStatus.PENDING -> UploadState.StillChecking
            }
        }
    }

    /** Clears the photos. The consent stays on record, so returning to this person needs no new notice flow beyond the roster. */
    fun cancelCapture() {
        finishEnrolment()
    }

    /** Protects a device left unattended: the photos and the session go after [QualityPolicy.BACKGROUND_LIMIT_MS], without waiting for staff to come back. */
    fun onBackgrounded(nowMs: Long) {
        backgroundedAt = nowMs
        timeoutJob = viewModelScope.launch {
            delay(QualityPolicy.BACKGROUND_LIMIT_MS)
            expireSession()
        }
    }

    /** WHY the elapsed check as well: a frozen process never runs the timer. */
    fun onForegrounded(nowMs: Long) {
        timeoutJob?.cancel()
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (nowMs - since > QualityPolicy.BACKGROUND_LIMIT_MS) expireSession()
    }

    private fun expireSession() {
        // WHY server != null: with no server set up there is no session to end, and signing out would strand setup on a blank sign-in screen.
        if (screen == Screen.SignIn || server == null) return
        signOut()
        error = "Signed out after time away. Any photos were deleted."
    }

    /** Leaves the current enrolment and returns to a fresh roster. */
    fun finishEnrolment() {
        clearEnrolment()
        screen = Screen.SelectPerson
    }

    fun signOut() {
        api?.signOut()
        searchJob?.cancel()
        clearEnrolment()
        persons = emptyList()
        hasMorePersons = false
        nextCursor = null
        error = null
        screen = Screen.SignIn
    }

    /** Forgets this device's server, so that setup starts again. */
    fun resetServer() {
        // WHY: a sign-in on its way would otherwise finish against a server that is gone.
        if (busy) return
        signOut()
        api = null
        store.clear()
        server = null
        error = null
        screen = Screen.Connect
    }

    val serverHost: String? get() = server?.baseUrl?.toUri()?.host

    /** What staff read: the whole address, because the same host on another port can be another server. */
    val serverAddress: String? get() = server?.baseUrl?.substringAfter("://")

    val isMock: Boolean get() = BuildConfig.DEBUG && (serverHost == "127.0.0.1" || serverHost == "localhost")

    private fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        runJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: InvalidServerAddress) {
                ensureActive()
                error = e.message
            } catch (e: ApiException) {
                ensureActive()
                onFailure(e)
            } finally {
                busy = false
            }
        }
    }

    private fun onFailure(e: ApiException) {
        val sessionEnded = e.failure == ApiFailure.UNAUTHORIZED && screen != Screen.SignIn
        if (sessionEnded) signOut()
        error = if (sessionEnded) "Your session ended. Sign in again." else e.code?.let(::messageForCode) ?: messageFor(e.failure)
    }

    /** Also stops whatever is running: work started for this enrolment must not touch state once it has ended. */
    private fun clearEnrolment() {
        runJob?.cancel()
        selectedPerson = null
        notice = null
        consentId = null
        wearsGlasses = false
        reEnrolReason = null
        photos.clear()
        poseSet = emptyList()
        capturePose = null
        retaking = false
        uploadKey = null
        uploadState = null
    }

    private suspend fun onMain(block: () -> Unit) = withContext(Dispatchers.Main) { block() }

    private companion object {
        const val TAG = "Enrolment"
    }
}
