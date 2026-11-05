package com.example.facedatasetcollector

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.facedatasetcollector.data.ApiException
import com.example.facedatasetcollector.data.EnrolmentApi
import com.example.facedatasetcollector.data.EnrolmentStatus
import com.example.facedatasetcollector.data.Pose
import com.example.facedatasetcollector.data.ReEnrolReason
import com.example.facedatasetcollector.data.RemovalReason
import com.example.facedatasetcollector.ui.EnrolmentViewModel
import com.example.facedatasetcollector.ui.FrameReading
import com.example.facedatasetcollector.ui.RE_ENROL_STAYS
import com.example.facedatasetcollector.ui.Screen
import com.example.facedatasetcollector.ui.UploadState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Runs the whole enrolment against the local mock with synthetic photos. Nothing here composes a screen, so the camera never opens.
 *
 * Needs `tools/mock_server/mock_server.py` running and `adb reverse tcp:8081 tcp:8081`.
 */
@RunWith(AndroidJUnit4::class)
class EnrolmentFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var model: EnrolmentViewModel

    // WHY: OnePlus devices freeze a process with nothing on screen after about 27 s, which hangs the run with no failure. The activity stays empty, so the camera still never opens.
    private lateinit var keepAwake: ActivityScenario<ComponentActivity>

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun <T> read(block: () -> T): T {
        var value: T? = null
        onMain { value = block() }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    private fun awaitUntil(seconds: Int = 40, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + seconds * 1000
        while (System.currentTimeMillis() < end) {
            if (read(condition)) return
            Thread.sleep(50)
        }
        throw AssertionError("Timed out. Screen=${read { model.screen }} error=${read { model.error }}")
    }

    private fun post(path: String, body: String) {
        val connection = URL("$MOCK$path").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.outputStream.use { it.write(body.toByteArray()) }
        assertEquals(200, connection.responseCode)
        connection.disconnect()
    }

    private fun scenario(name: String) = post("/mock/scenario", """{"scenario":"$name"}""")

    /** A plain gradient. Not an image of anything, and never a real photo. */
    private fun syntheticJpeg(seed: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(1080, 1350, Bitmap.Config.ARGB_8888)
        val paint = Paint()
        Canvas(bitmap).apply {
            for (y in 0 until 1350 step 6) {
                paint.color = Color.rgb((y + seed * 20) % 256, (y / 2) % 256, (y / 3 + seed) % 256)
                drawRect(0f, y.toFloat(), 1080f, y + 6f, paint)
            }
        }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    private fun signInAndCapture(personIndex: Int, glasses: Boolean) {
        awaitUntil { !model.busy }
        val person = read { model.persons[personIndex] }
        onMain { model.selectPerson(person) }
        awaitUntil { model.screen == Screen.Consent && !model.busy }
        onMain { model.recordConsent(glasses) }
        awaitUntil { model.screen == Screen.Capture && !model.busy }
        takePhotos()
    }

    private fun takePhotosFromHere() {
        Pose.entries.filter { it.withGlasses }.forEachIndexed { index, pose -> onMain { model.onPhoto(pose, syntheticJpeg(index)) } }
        awaitUntil { model.screen == Screen.Review }
    }

    private fun takePhotos() {
        val set = read { model.poseSet }
        set.forEachIndexed { index, pose -> onMain { model.onPhoto(pose, syntheticJpeg(index)) } }
        awaitUntil { model.screen == Screen.Review }
    }

    @Before
    fun signIn() {
        keepAwake = ActivityScenario.launch(ComponentActivity::class.java)
        post("/mock/reset", "{}")
        onMain {
            model = EnrolmentViewModel(instrumentation.targetContext.applicationContext as Application)
            model.resetServer()
            model.connect(MOCK)
        }
        awaitUntil { model.screen == Screen.SignIn && !model.busy }
        onMain { model.signIn("enrol.operator", "mock-only-password") }
        awaitUntil { model.screen == Screen.SelectPerson && !model.busy }
        onMain { model.search("") }
        awaitUntil { model.persons.isNotEmpty() }
    }

    @After
    fun tidy() {
        scenario("normal")
        onMain { model.resetServer() }
        keepAwake.close()
    }

    @Test
    fun acceptedEnrolment_clearsPhotosFromTheDevice() {
        signInAndCapture(personIndex = 0, glasses = true)
        assertEquals(10, read { model.photos.size })

        val start = System.currentTimeMillis()
        onMain { model.upload() }
        awaitUntil { model.uploadState == UploadState.Checking }
        Log.i(TAG, "upload10Ms=${System.currentTimeMillis() - start}")
        assertTrue(read { model.photos.isEmpty() })
        awaitUntil { model.uploadState == UploadState.Accepted }
        Log.i(TAG, "uploadAndCheckMs=${System.currentTimeMillis() - start}")
    }

    @Test
    fun registeredPerson_goesStraightToTheNoticeAndATakenIdIsRefused() {
        onMain { model.startRegistration() }
        assertEquals(Screen.Register, read { model.screen })
        onMain { model.registerPerson("INST-0477", "Sample Person D", null) }
        awaitUntil { model.screen == Screen.Consent && !model.busy }
        assertEquals("INST-0477", read { model.selectedPerson?.personId })
        onMain { model.recordConsent(false) }
        awaitUntil { model.screen == Screen.Capture && !model.busy }
        takePhotos()
        onMain { model.upload() }
        awaitUntil { model.uploadState == UploadState.Accepted }

        onMain {
            model.finishEnrolment()
            model.startRegistration()
            model.registerPerson("inst-0477", "Someone Else", null)
        }
        awaitUntil { !model.busy }
        assertEquals(Screen.Register, read { model.screen })
        assertEquals("This ID is already registered. Find the person in the list.", read { model.error })
        onMain { model.cancelRegistration() }
        assertEquals(Screen.SelectPerson, read { model.screen })
    }

    @Test
    fun rejectedEnrolment_retakesEverythingUnderTheSameConsent() {
        scenario("reject")
        signInAndCapture(personIndex = 2, glasses = false)
        val consent = read { model.consentId }
        onMain { model.upload() }
        awaitUntil { model.uploadState is UploadState.Rejected && !model.busy }
        assertTrue((read { model.uploadState } as UploadState.Rejected).problems.isNotEmpty())
        assertTrue(read { model.photos.isEmpty() })

        scenario("normal")
        onMain { model.retakeAll() }
        awaitUntil { model.screen == Screen.Capture }
        assertEquals(consent, read { model.consentId })
        assertEquals(Pose.FRONT, read { model.capturePose })
        takePhotos()
        onMain { model.upload() }
        awaitUntil { model.uploadState == UploadState.Accepted }
    }

    @Test
    fun serverFailure_keepsPhotosAndRetrySucceeds() {
        scenario("server_error")
        signInAndCapture(personIndex = 2, glasses = false)
        onMain { model.upload() }
        awaitUntil { model.screen == Screen.Review && !model.busy }
        assertEquals("The server had a problem. Try again shortly.", read { model.error })
        assertEquals(5, read { model.photos.size })

        scenario("normal")
        onMain { model.upload() }
        awaitUntil { model.uploadState == UploadState.Accepted }
    }

    @Test
    fun droppedConnection_keepsPhotosAndRetrySucceeds() {
        scenario("drop")
        signInAndCapture(personIndex = 2, glasses = false)
        onMain { model.upload() }
        awaitUntil { model.screen == Screen.Review && !model.busy }
        assertEquals(5, read { model.photos.size })

        scenario("normal")
        onMain { model.upload() }
        awaitUntil { model.uploadState == UploadState.Accepted }
    }

    @Test
    fun cancel_clearsPhotos() {
        signInAndCapture(personIndex = 2, glasses = false)
        onMain { model.cancelCapture() }
        assertTrue(read { model.photos.isEmpty() })
        assertEquals(Screen.SelectPerson, read { model.screen })
    }

    @Test
    fun longTimeAway_clearsPhotosAndSession() {
        signInAndCapture(personIndex = 2, glasses = false)
        onMain { model.onBackgrounded(0) }
        onMain { model.onForegrounded(10 * 60 * 1000L) }
        assertTrue(read { model.photos.isEmpty() })
        assertEquals(Screen.SignIn, read { model.screen })
        assertFalse(read { model.error }.isNullOrEmpty())
    }

    @Test
    fun glassesSet_stopsBeforeEachSwitchAndOnRetake() {
        val person = read { model.persons[0] }
        onMain { model.selectPerson(person) }
        awaitUntil { model.screen == Screen.Consent && !model.busy }
        onMain { model.recordConsent(true) }
        awaitUntil { model.screen == Screen.Capture && !model.busy }
        assertTrue(read { model.needsGlassesStep })

        onMain { model.confirmGlasses() }
        Pose.setFor(false).forEachIndexed { index, pose ->
            assertFalse(read { model.needsGlassesStep })
            onMain { model.onPhoto(pose, syntheticJpeg(index)) }
        }
        assertEquals(Pose.FRONT_GLASSES, read { model.capturePose })
        assertTrue(read { model.needsGlassesStep })

        onMain { model.confirmGlasses() }
        takePhotosFromHere()
        onMain { model.retake(Pose.LEFT_30) }
        assertTrue(read { model.needsGlassesStep })
    }

    @Test
    fun tuningNotes_followThePhotoAndClearWithIt() {
        val person = read { model.persons[0] }
        onMain { model.selectPerson(person) }
        awaitUntil { model.screen == Screen.Consent && !model.busy }
        onMain { model.recordConsent(false) }
        awaitUntil { model.screen == Screen.Capture && !model.busy }
        onMain {
            model.onReading(FrameReading(0, 1, pitch = 2f, bridgeEdge = 5f))
            model.onPhoto(Pose.FRONT, syntheticJpeg(0))
        }
        assertTrue(read { model.tuningNotes[Pose.FRONT] }!!.contains("bridge 5.0"))
        onMain { model.cancelCapture() }
        assertTrue(read { model.tuningNotes.isEmpty() })
    }

    @Test
    fun glassesWarning_followsTheScoresAndClearsWithThePhotos() {
        val person = read { model.persons[0] }
        onMain { model.selectPerson(person) }
        awaitUntil { model.screen == Screen.Consent && !model.busy }
        onMain { model.recordConsent(false) }
        awaitUntil { model.screen == Screen.Capture && !model.busy }
        val set = read { model.poseSet }
        // Glasses on for 3 of 5 no-glasses photos: warn.
        set.forEachIndexed { index, pose -> onMain { model.onPhoto(pose, syntheticJpeg(index), if (index < 3) 4f else 1.5f) } }
        assertEquals(listOf(false), read { model.glassesWarnings })
        // A retake that scores as no glasses leaves 2 of 5: the warning goes.
        onMain { model.retake(set[0]) }
        onMain { model.onPhoto(set[0], syntheticJpeg(9), 1.5f) }
        assertTrue(read { model.glassesWarnings.isEmpty() })
        onMain { model.retake(set[0]) }
        onMain { model.onPhoto(set[0], syntheticJpeg(9), 4f) }
        assertEquals(listOf(false), read { model.glassesWarnings })
        onMain { model.cancelCapture() }
        assertTrue(read { model.glassesWarnings.isEmpty() })
    }

    @Test
    fun noGlasses_neverStops() {
        signInAndCapture(personIndex = 0, glasses = false)
        assertFalse(read { model.needsGlassesStep })
    }

    @Test
    fun withdrawingConsent_clearsPhotosAndReachesTheServer() {
        signInAndCapture(personIndex = 0, glasses = false)
        val person = read { model.selectedPerson }!!
        val consent = read { model.consentId }!!
        val set = read { model.photos.toMap() }
        onMain { model.withdrawConsent() }
        assertTrue(read { model.photos.isEmpty() })
        assertEquals(Screen.SelectPerson, read { model.screen })
        awaitUntil { !model.busy }
        assertNull(read { model.withdrawalMessage })

        // Only the server can prove it: a withdrawn consent must refuse an upload.
        val check = EnrolmentApi(MOCK).apply { login("enrol.operator", "mock-only-password") }
        val refused = assertThrows(ApiException::class.java) { check.upload(person.personId, consent, set) }
        assertEquals("no_active_consent", refused.code)
    }

    @Test
    fun removedPerson_leavesTheRosterWithTheServersPromise() {
        val person = read { model.persons[1] }
        onMain { model.removePerson(person, RemovalReason.WITHDRAWN) }
        awaitUntil { model.withdrawalMessage != null && !model.busy }
        assertEquals("Removal requested. Their photos and face data are deleted within 90 days.", read { model.withdrawalMessage })
        awaitUntil { model.persons.none { it.personId == person.personId } }
    }

    @Test
    fun withdrawingDuringReEnrolment_saysTheOldEnrolmentStays() {
        val person = read { model.persons[1] }
        onMain { model.selectPerson(person, ReEnrolReason.OTHER) }
        awaitUntil { model.screen == Screen.Consent && !model.busy }
        onMain { model.recordConsent(false) }
        awaitUntil { model.screen == Screen.Capture && !model.busy }
        onMain { model.withdrawConsent() }
        awaitUntil { model.withdrawalMessage != null && !model.busy }
        assertEquals(RE_ENROL_STAYS, read { model.withdrawalMessage })
    }

    @Test
    fun declineWhileConsentIsOnItsWay_isIgnored() {
        val person = read { model.persons[0] }
        onMain { model.selectPerson(person) }
        awaitUntil { model.screen == Screen.Consent && !model.busy }
        onMain {
            model.recordConsent(false)
            model.personDeclined()
        }
        awaitUntil { model.screen == Screen.Capture && !model.busy }
        assertEquals(person, read { model.selectedPerson })
    }

    @Test
    fun signOutDuringUpload_staysSignedOutWithNoPhotos() {
        scenario("slow")
        signInAndCapture(personIndex = 0, glasses = false)
        onMain { model.upload() }
        awaitUntil { model.uploadState == UploadState.Sending }
        onMain { model.signOut() }
        awaitUntil(seconds = 60) { !model.busy }
        assertEquals(Screen.SignIn, read { model.screen })
        assertTrue(read { model.photos.isEmpty() })
        assertNull(read { model.uploadState })
    }

    @Test
    fun lostConnectionAfterAcceptance_isNotAFailedEnrolment() {
        signInAndCapture(personIndex = 0, glasses = false)
        scenario("poll_drop")
        onMain { model.upload() }
        awaitUntil { model.uploadState == UploadState.StillChecking }
        assertTrue(read { model.photos.isEmpty() })
    }

    @Test
    fun slowServer_keepsPhotosUntilItAnswers() {
        scenario("slow")
        signInAndCapture(personIndex = 0, glasses = false)
        val start = System.currentTimeMillis()
        onMain { model.upload() }
        awaitUntil { model.uploadState == UploadState.Sending }
        Thread.sleep(5_000)
        assertEquals(UploadState.Sending, read { model.uploadState })
        assertEquals(5, read { model.photos.size })
        awaitUntil(seconds = 60) { model.uploadState == UploadState.Accepted }
        Log.i(TAG, "slowUploadMs=${System.currentTimeMillis() - start}")
    }

    @Test
    fun lateAnswer_retriesWithTheSameKeyAndCreatesOneJob() {
        scenario("timeout")
        signInAndCapture(personIndex = 0, glasses = false)
        val start = System.currentTimeMillis()
        onMain { model.upload() }
        awaitUntil(seconds = 60) { model.uploadState == UploadState.Accepted }
        assertTrue(read { model.photos.isEmpty() })
        Log.i(TAG, "lateAnswerMs=${System.currentTimeMillis() - start}")
    }

    @Test
    fun reEnrolment_keepsItsReasonAcrossARejectionAndClearsAfterwards() {
        scenario("reject")
        val person = read { model.persons[1] }
        assertEquals(EnrolmentStatus.ENROLLED, person.status)
        onMain { model.selectPerson(person, ReEnrolReason.POOR_PHOTOS) }
        awaitUntil { model.screen == Screen.Consent && !model.busy }
        onMain { model.recordConsent(false) }
        awaitUntil { model.screen == Screen.Capture && !model.busy }
        takePhotos()
        onMain { model.upload() }
        awaitUntil { model.uploadState is UploadState.Rejected && !model.busy }

        // The mock refuses an enrolled person with no reason, so this passes only if the reason survived the retake.
        scenario("normal")
        onMain { model.retakeAll() }
        awaitUntil { model.screen == Screen.Capture }
        takePhotos()
        onMain { model.upload() }
        awaitUntil { model.uploadState == UploadState.Accepted }
        onMain { model.finishEnrolment() }
        assertNull(read { model.reEnrolReason })
    }

    @Test
    fun enrolledPersonWithNoReason_isRefusedAndKeepsPhotos() {
        signInAndCapture(personIndex = 1, glasses = false)
        onMain { model.upload() }
        awaitUntil { model.screen == Screen.Review && !model.busy }
        assertEquals("This person is already enrolled. Cancel and use Re-enrol on the roster.", read { model.error })
        assertEquals(5, read { model.photos.size })
    }

    @Test
    fun setupCode_refusesAMismatchAndBindsTheNamedClient() {
        onMain {
            model.resetServer()
            model.connectWithCode("""{"base_url":"$MOCK","client_id":"someone-else"}""")
        }
        awaitUntil { model.error != null && !model.busy }
        assertEquals(Screen.Connect, read { model.screen })
        assertNull(read { model.server })

        onMain { model.connectWithCode("""{"base_url":"$MOCK","client_id":"mock-dedicated"}""") }
        awaitUntil { model.screen == Screen.SignIn && !model.busy }
        assertEquals("mock-dedicated", read { model.server }?.clientId)
    }

    @Test
    fun longTimeAway_duringSetup_staysInSetup() {
        onMain {
            model.resetServer()
            model.onBackgrounded(0)
            model.onForegrounded(10 * 60 * 1000L)
        }
        assertEquals(Screen.Connect, read { model.screen })
    }

    private companion object {
        const val TAG = "EnrolmentFlowTest"
        const val MOCK = "http://127.0.0.1:8081"
    }
}
