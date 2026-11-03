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
    data object Register : Screen
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

    /** Kept apart from [error], because refreshing the roster clears that, and staff must see this. */
    var withdrawalMessage by mutableStateOf<String?>(null)
        private set

    /** The active enrolment. Consent stays valid across retakes, so a rejected set reuses it. */
    var consentId: String? = null
        private set
    var wearsGlasses = false
        private set

    /** The glasses state staff last confirmed. Null means ask, so a wrong set cannot start by accident. */
    private var glassesConfirmed by mutableStateOf<Boolean?>(null)

    /** A 10-photo set stops the camera and asks before each switch, because a small label is easy to miss. */
    val needsGlassesStep: Boolean get() = wearsGlasses && capturePose?.let { it.withGlasses != glassesConfirmed } == true

    /** Set only while re-enrolling. It travels with the upload, and the job then replaces the old enrolment if accepted. */
    var reEnrolReason: ReEnrolReason? = null
        private set

    /** JPEG bytes, in memory only (D-002). Never written to disk, and lost if the process dies. */
    val photos = mutableStateMapOf<Pose, ByteArray>()

    /** The numbers behind each kept photo, for tuning against the mock. Memory only, shown on review and never logged (D-016). */
    val tuningNotes = mutableStateMapOf<Pose, String>()
    private var lastReading: FrameReading? = null

    /** Nose-bridge edge score per kept photo. Memory only, never logged or sent: it is a measurement of the person (D-021). */
    private val bridgeScores = mutableStateMapOf<Pose, Float>()

    /** Halves of the set that look wrong for glasses, for a warning on review that never blocks upload. */
    val glassesWarnings: List<Boolean> get() = glassesMismatches(poseSet, bridgeScores)
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

    /** First-time setup from a typed address. Debug builds only: a typed address has no signature to check (D-020). */
    fun connect(address: String) {
        if (!BuildConfig.DEBUG) return
        run { connectTo(ServerSetup(normalizeBaseUrl(address, allowLoopbackHttp = true), null)) }
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
        error = if (BuildConfig.DEBUG) "The camera could not start. Type the server address instead." else "The camera could not start. Ask your administrator for help."
    }

    /** First-time setup from a scanned code. Any failure lands on the connect screen, where the typed fallback is. */
    fun connectWithCode(raw: String) {
        screen = Screen.Connect
        run { connectTo(parseSetupCode(raw, allowLoopbackHttp = BuildConfig.DEBUG)) }
    }

    /** Trusts the server's identity only after it answers. A signed name wins, because a genuine code for one client must not let its server pose as another. */
    private suspend fun connectTo(setup: ServerSetup) {
        val client = EnrolmentApi(setup.baseUrl)
        val info = client.serverInfo()
        setup.requireClient(info.clientId)
        val connected = StoredServer(setup.baseUrl, info.clientId, setup.clientName ?: info.clientName)
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
                ensureActive()
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
        withdrawalMessage = null
        openConsent(person, reason)
    }

    fun startRegistration() {
        if (busy) return
        withdrawalMessage = null
        error = null
        screen = Screen.Register
    }

    fun cancelRegistration() {
        if (busy) return
        error = null
        screen = Screen.SelectPerson
    }

    /** Staff already confirmed the ID. A registered person goes straight to the notice, as if picked from the list. */
    fun registerPerson(personId: String, name: String, department: String?) = run {
        val client = api ?: return@run
        val person = client.registerPerson(personId, name, department)
        // Registered now, so if the notice fails the person waits on the roster rather than being registered twice.
        onMain { screen = Screen.SelectPerson }
        openConsent(person, null)
    }

    private suspend fun openConsent(person: Person, reason: ReEnrolReason?) {
        val client = api ?: return
        val expected = server ?: return
        val info = client.serverInfo()
        if (!isSameClient(expected.clientId, info.clientId)) {
            onMain { screen = Screen.ClientChanged }
            return
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

    fun confirmGlasses() {
        glassesConfirmed = capturePose?.withGlasses
        poseStartedAt = SystemClock.elapsedRealtime()
    }

    private fun startCapture() {
        glassesConfirmed = null
        photos.clear()
        tuningNotes.clear()
        bridgeScores.clear()
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
    fun onPhoto(pose: Pose, jpeg: ByteArray, bridge: Float? = null) {
        if (screen != Screen.Capture || pose != capturePose) return
        // Timing and size only, with no person in the line.
        val now = SystemClock.elapsedRealtime()
        if (isMock) Log.d(TAG, "pose=${pose.wireName} poseMs=${now - poseStartedAt} photoBytes=${jpeg.size}")
        poseStartedAt = now
        if (isMock) lastReading?.let { tuningNotes[pose] = tuningNote(it) }
        photos[pose] = jpeg
        if (bridge == null) bridgeScores.remove(pose) else bridgeScores[pose] = bridge
        uploadKey = null
        val next = if (retaking) null else poseSet.getOrNull(poseSet.indexOf(pose) + 1)
        capturePose = next
        if (next == null) {
            retaking = false
            screen = Screen.Review
        }
    }

    /** Arrives before the photo from the same frame, because both are posted in order to the main thread. */
    fun onReading(frame: FrameReading) {
        lastReading = frame
    }

    fun retake(pose: Pose) {
        retaking = true
        glassesConfirmed = null
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

    /** The person changed their mind. Photos go first, so a failed call cannot keep them. */
    fun withdrawConsent() {
        if (busy) return
        val client = api
        val id = consentId
        val reEnrolling = reEnrolReason != null
        finishEnrolment()
        if (client == null || id == null) return
        run {
            try {
                client.withdrawConsent(id)
                // WHY: staff could otherwise believe the person has left, while the old enrolment keeps matching.
                if (reEnrolling) onMain { withdrawalMessage = RE_ENROL_STAYS }
            } catch (e: ApiException) {
                val unconfirmed = "The photos were deleted, but the app could not confirm the consent was withdrawn. Tell your administrator."
                onMain {
                    if (e.failure == ApiFailure.UNAUTHORIZED) {
                        signOut()
                        error = unconfirmed
                    } else {
                        withdrawalMessage = unconfirmed
                    }
                }
            }
        }
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
        error = "Signed out after time away. Any photos on this device were deleted."
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
        withdrawalMessage = null
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
        glassesConfirmed = null
        reEnrolReason = null
        photos.clear()
        tuningNotes.clear()
        bridgeScores.clear()
        lastReading = null
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
