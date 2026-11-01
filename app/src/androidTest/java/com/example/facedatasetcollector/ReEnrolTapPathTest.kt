package com.example.facedatasetcollector

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.facedatasetcollector.data.ServerStore
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/**
 * Taps the real screens from connect to the notice, as staff would. Stops at the notice by declining, so capture never starts and the camera never opens.
 *
 * Needs `tools/mock_server/mock_server.py` running and `adb reverse tcp:8081 tcp:8081`.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ReEnrolTapPathTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var app: ActivityScenario<MainActivity>

    @Before
    fun start() {
        val reset = URL("$MOCK/mock/reset").openConnection() as HttpURLConnection
        reset.requestMethod = "POST"
        reset.doOutput = true
        reset.outputStream.use { it.write("{}".toByteArray()) }
        check(reset.responseCode == 200)
        reset.disconnect()
        ServerStore(context).clear()
        app = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun finish() {
        app.close()
        ServerStore(context).clear()
    }

    @Test
    fun enrolledPerson_reachesTheNoticeOnlyAfterAReason() {
        compose.waitUntilAtLeastOneExists(hasText("Connect to your server"), WAIT_MS)
        compose.onNode(hasSetTextAction()).performTextInput(MOCK)
        compose.onNodeWithText("Connect").performClick()

        compose.waitUntilAtLeastOneExists(hasText("Sign in"), WAIT_MS)
        compose.onNode(hasText("Username") and hasSetTextAction()).performTextInput("enrol.operator")
        compose.onNode(hasText("Password") and hasSetTextAction()).performTextInput("mock-only-password")
        compose.onNodeWithText("Sign in").performClick()

        compose.waitUntilAtLeastOneExists(hasContentDescription("Re-enrol Sample Person B"), WAIT_MS)
        compose.onNode(hasContentDescription("Re-enrol Sample Person B")).performClick()

        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.onNodeWithText("Poor photos").performClick()
        compose.onNodeWithText("Continue").assertIsEnabled().performClick()

        compose.waitUntilAtLeastOneExists(hasText("Notice and consent"), WAIT_MS)
        compose.onNodeWithText("Google").assertExists()
        compose.onNodeWithText("Person declined").performClick()

        compose.waitUntilAtLeastOneExists(hasText("Select person"), WAIT_MS)
    }

    private companion object {
        const val MOCK = "http://127.0.0.1:8081"
        const val WAIT_MS = 15_000L
    }
}
