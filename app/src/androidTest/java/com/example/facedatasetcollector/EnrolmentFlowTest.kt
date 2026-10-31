package com.example.facedatasetcollector

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.facedatasetcollector.data.Pose
import com.example.facedatasetcollector.ui.EnrolmentViewModel
import com.example.facedatasetcollector.ui.Screen
import com.example.facedatasetcollector.ui.UploadState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun scenario(name: String) {
        val connection = URL("$MOCK/mock/scenario").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.outputStream.use { it.write("""{"scenario":"$name"}""".toByteArray()) }
        assertEquals(200, connection.responseCode)
        connection.disconnect()
    }

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

    private fun takePhotos() {
        val set = read { model.poseSet }
        set.forEachIndexed { index, pose -> onMain { model.onPhoto(pose, syntheticJpeg(index)) } }
        awaitUntil { model.screen == Screen.Review }
    }

    @Before
    fun signIn() {
        scenario("normal")
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

    private companion object {
        const val TAG = "EnrolmentFlowTest"
        const val MOCK = "http://127.0.0.1:8081"
    }
}
