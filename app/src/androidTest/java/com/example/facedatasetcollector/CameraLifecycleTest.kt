package com.example.facedatasetcollector

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.facedatasetcollector.data.Pose
import com.example.facedatasetcollector.ui.CaptureCamera
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the camera teardown path. A frame reaching the shut-down executor, or a closed detector being used, crashes the process and fails this test.
 *
 * Assumes the camera permission is already granted on the device. Without it the camera never starts and this test proves nothing. Keeps no frame: no photo callback stores anything.
 */
@RunWith(AndroidJUnit4::class)
class CameraLifecycleTest {
    private fun show(scenario: ActivityScenario<ComponentActivity>) {
        scenario.onActivity { activity ->
            activity.setContent { CaptureCamera(Pose.FRONT, {}, { _, _ -> }, {}, Modifier.fillMaxSize()) }
        }
    }

    @Test
    fun recreatingAndClosingTheCamera_doesNotCrash() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            show(scenario)
            Thread.sleep(FRAMES_FLOWING_MS)
            scenario.recreate()
            show(scenario)
            Thread.sleep(FRAMES_FLOWING_MS)
        }
        // Lets any late callback fail inside this test rather than after it.
        Thread.sleep(1_000)
    }

    private companion object {
        // Longer than the 100-frame timing window, so logcat shows the new analyzer thread running.
        const val FRAMES_FLOWING_MS = 10_000L
    }
}
