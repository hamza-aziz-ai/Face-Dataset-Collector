package com.example.facedatasetcollector

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.facedatasetcollector.data.Pose
import com.example.facedatasetcollector.data.PoseProblem
import com.example.facedatasetcollector.ui.RejectedScreen
import com.example.facedatasetcollector.ui.ReviewScreen
import com.example.facedatasetcollector.ui.UploadScreen
import com.example.facedatasetcollector.ui.UploadState
import com.example.facedatasetcollector.ui.theme.FaceDatasetCollectorTheme
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * Shows one screen with synthetic data for a person to look at, or for `adb screencap`. The real app blocks screenshots on review, and real photos must never be used to check a layout.
 *
 * Pick the screen with `-e screen review|sending|checking|accepted|rejected`.
 */
@RunWith(AndroidJUnit4::class)
class ScreenGalleryTest {
    private fun placeholder(seed: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(432, 540, Bitmap.Config.ARGB_8888)
        val paint = Paint()
        Canvas(bitmap).apply {
            for (y in 0 until 540 step 6) {
                paint.color = Color.rgb(60 + (y / 6 + seed * 12) % 120, 90, 130 + seed * 6)
                drawRect(0f, y.toFloat(), 432f, y + 6f, paint)
            }
        }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }

    @Test
    fun show() {
        val arguments = InstrumentationRegistry.getArguments()
        val screen = arguments.getString("screen") ?: "review"
        val photos = Pose.entries.withIndex().associate { (index, pose) -> pose to placeholder(index) }
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    FaceDatasetCollectorTheme {
                        val modifier = Modifier.fillMaxSize()
                        when (screen) {
                            "review" -> ReviewScreen("Sample Person A", photos, Pose.entries, true, false, null, {}, {}, {}, modifier)
                            "sending" -> UploadScreen("Sample Person A", 10, "0.1-mock", UploadState.Sending, true, {}, modifier)
                            "checking" -> UploadScreen("Sample Person A", 10, "0.1-mock", UploadState.Checking, true, {}, modifier)
                            "accepted" -> UploadScreen("Sample Person A", 10, "0.1-mock", UploadState.Accepted, true, {}, modifier)
                            else -> RejectedScreen(
                                "Sample Person A",
                                10,
                                listOf(PoseProblem("left_30", "face_too_small"), PoseProblem("chin_up_glasses", "too_blurry")),
                                {},
                                {},
                                modifier,
                            )
                        }
                    }
                }
            }
            Thread.sleep(SHOW_MS)
        }
    }

    private companion object {
        const val SHOW_MS = 7_000L
    }
}
