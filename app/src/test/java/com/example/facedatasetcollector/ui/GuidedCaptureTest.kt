package com.example.facedatasetcollector.ui

import com.example.facedatasetcollector.data.Pose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

/** All values and pixels are synthetic. Thresholds are not calibrated, so tests read them from the policy. */
class GuidedCaptureTest {
    private val policy = QualityPolicy

    private fun good(timeMs: Long, yaw: Float = 0f, pitch: Float = 0f) = FrameReading(
        timeMs = timeMs,
        faceCount = 1,
        yaw = yaw,
        pitch = pitch,
        faceLuma = (policy.MIN_FACE_LUMA + policy.MAX_FACE_LUMA) / 2,
        blurScore = policy.MIN_BLUR_SCORE * 2,
        faceWidthFraction = (policy.MIN_FACE_WIDTH + policy.MAX_FACE_WIDTH) / 2,
    )

    private fun heldUntilPhoto(guide: PoseGuide, frame: (Long) -> FrameReading): Guidance {
        var time = 0L
        var last = guide.onFrame(frame(time))
        while (last !is Guidance.TakePhoto && time < 10_000) {
            time += 50
            last = guide.onFrame(frame(time))
        }
        return last
    }

    private fun fix(frame: FrameReading, pose: Pose = Pose.FRONT): String =
        (PoseGuide(pose).onFrame(frame) as Guidance.Fix).text

    @Test
    fun takesThePhotoOnlyAfterHoldingStill() {
        val guide = PoseGuide(Pose.FRONT)
        assertEquals(Guidance.Hold(0f), guide.onFrame(good(0)))
        assertTrue(guide.onFrame(good(policy.STEADY_MS / 2)) is Guidance.Hold)
        assertEquals(Guidance.TakePhoto, guide.onFrame(good(policy.STEADY_MS)))
    }

    @Test
    fun firesExactlyOncePerPose() {
        val guide = PoseGuide(Pose.FRONT)
        assertEquals(Guidance.TakePhoto, heldUntilPhoto(guide) { good(it) })
        repeat(5) { assertEquals(Guidance.Hold(1f), guide.onFrame(good(20_000L + it))) }
    }

    @Test
    fun movingRestartsTheWait() {
        val guide = PoseGuide(Pose.FRONT)
        guide.onFrame(good(0))
        guide.onFrame(good(100))
        val moved = policy.STEADY_DEGREES + 1
        assertEquals(Guidance.Hold(0f), guide.onFrame(good(200, yaw = moved)))
        assertTrue(guide.onFrame(good(policy.STEADY_MS)) is Guidance.Hold)
    }

    @Test
    fun aFailedCheckRestartsTheWait() {
        val guide = PoseGuide(Pose.FRONT)
        guide.onFrame(good(0))
        assertTrue(guide.onFrame(FrameReading(100, faceCount = 0)) is Guidance.Fix)
        assertEquals(Guidance.Hold(0f), guide.onFrame(good(200)))
        assertTrue(guide.onFrame(good(policy.STEADY_MS)) is Guidance.Hold)
    }

    @Test
    fun aLongFrameGapRestartsTheWait() {
        val guide = PoseGuide(Pose.FRONT)
        guide.onFrame(good(0))
        assertEquals(Guidance.Hold(0f), guide.onFrame(good(policy.MAX_FRAME_GAP_MS + 1)))
    }

    @Test
    fun eachPoseNeedsItsOwnAngle() {
        val turn = (policy.TURN_MIN + policy.TURN_MAX) / 2 * policy.YAW_SIGN
        val chin = (policy.CHIN_MIN + policy.CHIN_MAX) / 2
        assertEquals(Guidance.TakePhoto, heldUntilPhoto(PoseGuide(Pose.LEFT_30)) { good(it, yaw = turn) })
        assertEquals(Guidance.TakePhoto, heldUntilPhoto(PoseGuide(Pose.RIGHT_30_GLASSES)) { good(it, yaw = -turn) })
        assertEquals(Guidance.TakePhoto, heldUntilPhoto(PoseGuide(Pose.CHIN_UP)) { good(it, pitch = chin) })
        assertEquals(Guidance.TakePhoto, heldUntilPhoto(PoseGuide(Pose.CHIN_DOWN)) { good(it, pitch = -chin) })
        assertEquals("Turn your head to the left", fix(good(0, yaw = -turn), Pose.LEFT_30))
        assertEquals("Turn your head to the right", fix(good(0, yaw = turn), Pose.RIGHT_30))
        assertEquals("Lift your chin", fix(good(0), Pose.CHIN_UP))
        assertEquals("Lower your chin", fix(good(0), Pose.CHIN_DOWN))
    }

    @Test
    fun overturningAsksToComeBack() {
        val tooFar = (policy.TURN_MAX + 5) * policy.YAW_SIGN
        assertEquals("Turn a little to your right", fix(good(0, yaw = tooFar), Pose.LEFT_30))
        assertEquals("Turn a little to your left", fix(good(0, yaw = -tooFar), Pose.RIGHT_30))
        assertEquals("Lower your chin a little", fix(good(0, pitch = policy.CHIN_MAX + 5), Pose.CHIN_UP))
        assertEquals("Lift your chin a little", fix(good(0, pitch = -policy.CHIN_MAX - 5), Pose.CHIN_DOWN))
    }

    @Test
    fun pupilDistanceAppliesOnlyToFrontPoses() {
        val small = policy.MIN_PUPIL_PIXELS - 1
        assertEquals("Move closer", fix(good(0).copy(pupilDistance = small)))
        val turn = (policy.TURN_MIN + policy.TURN_MAX) / 2 * policy.YAW_SIGN
        assertTrue(PoseGuide(Pose.LEFT_30).onFrame(good(0, yaw = turn).copy(pupilDistance = small)) is Guidance.Hold)
        assertTrue(PoseGuide(Pose.FRONT).onFrame(good(0).copy(pupilDistance = null)) is Guidance.Hold)
    }

    @Test
    fun explainsEachQualityProblem() {
        val base = good(0)
        assertEquals("Place your face inside the oval", fix(base.copy(faceCount = 0)))
        assertEquals("Only one person should be in view", fix(base.copy(faceCount = 2)))
        assertEquals("Move closer", fix(base.copy(faceWidthFraction = policy.MIN_FACE_WIDTH - 0.01f)))
        assertEquals("Move back a little", fix(base.copy(faceWidthFraction = policy.MAX_FACE_WIDTH + 0.01f)))
        assertEquals("Centre your face in the oval", fix(base.copy(faceCentreOffset = policy.MAX_CENTRE_OFFSET + 0.01f)))
        assertEquals("Keep your head level", fix(base.copy(roll = policy.MAX_ROLL + 1)))
        assertEquals("Too dark. Find more light", fix(base.copy(faceLuma = policy.MIN_FACE_LUMA - 1)))
        assertEquals("Too bright. Move out of direct light", fix(base.copy(faceLuma = policy.MAX_FACE_LUMA + 1)))
        assertEquals("Hold still", fix(base.copy(blurScore = policy.MIN_BLUR_SCORE - 1)))
    }

    private fun plane(width: Int, height: Int, pixel: (Int, Int) -> Int): ByteBuffer {
        val bytes = ByteArray(width * height)
        for (y in 0 until height) for (x in 0 until width) bytes[y * width + x] = pixel(x, y).toByte()
        return ByteBuffer.wrap(bytes)
    }

    @Test
    fun blurScoreRanksSharpAboveSoftAboveFlat() {
        val size = 256
        val box = Box(0, 0, size, size)
        val flat = plane(size, size) { _, _ -> 128 }
        val soft = plane(size, size) { x, _ -> x }
        val sharp = plane(size, size) { x, y -> if ((x / 7 + y / 7) % 2 == 0) 30 else 220 }

        val flatScore = blurScore(flat, size, 1, size, size, box)!!
        val softScore = blurScore(soft, size, 1, size, size, box)!!
        val sharpScore = blurScore(sharp, size, 1, size, size, box)!!
        assertEquals(0.0, flatScore, 0.0)
        assertTrue(softScore < sharpScore)
    }

    @Test
    fun blurScoreDoesNotDependOnFaceSize() {
        val pattern = { x: Int, y: Int -> if ((x / 8 + y / 8) % 2 == 0) 30 else 220 }
        val small = plane(320, 320, pattern)
        val large = plane(640, 640) { x, y -> pattern(x / 2, y / 2) }
        val smallScore = blurScore(small, 320, 1, 320, 320, Box(0, 0, 320, 320))!!
        val largeScore = blurScore(large, 640, 1, 640, 640, Box(0, 0, 640, 640))!!
        assertEquals(smallScore, largeScore, smallScore * 0.2)
    }

    @Test
    fun blurScoreNeedsEnoughPixels() {
        val buffer = plane(64, 64) { _, _ -> 100 }
        assertNull(blurScore(buffer, 64, 1, 64, 64, Box(0, 0, 10, 10)))
        assertNull(blurScore(buffer, 64, 1, 64, 64, Box(100, 100, 200, 200)))
        assertNotNull(blurScore(plane(200, 200) { _, _ -> 100 }, 200, 1, 200, 200, Box(0, 0, 200, 200)))
    }

    @Test
    fun cropIsFourByFiveAroundTheFace() {
        val crop = portraitCrop(Box(400, 500, 700, 860), 1080, 1920)
        assertEquals(crop.right - crop.left, (crop.bottom - crop.top) * 4 / 5)
        assertTrue(crop.left <= 400 && crop.right >= 700)
        assertTrue(crop.top < 500 && crop.bottom > 860)
    }

    @Test
    fun cropStaysInsideTheFrame() {
        listOf(Box(0, 0, 300, 360), Box(780, 1560, 1080, 1920), Box(100, 100, 1000, 1100)).forEach { face ->
            val crop = portraitCrop(face, 1080, 1920)
            assertTrue(face.toString(), crop.left >= 0 && crop.top >= 0 && crop.right <= 1080 && crop.bottom <= 1920)
        }
    }

    @Test
    fun cropFitsALandscapeFrame() {
        val crop = portraitCrop(Box(600, 100, 1200, 900), 1920, 1080)
        assertEquals(1080, crop.bottom - crop.top)
        assertEquals(864, crop.right - crop.left)
    }

    @Test
    fun aHeadRockingSideToSideNeverCounts() {
        val rock = policy.STEADY_DEGREES * 2
        val guide = PoseGuide(Pose.FRONT)
        var time = 0L
        repeat(100) {
            val roll = if (it % 2 == 0) rock else -rock
            assertTrue(guide.onFrame(good(time).copy(roll = roll)) is Guidance.Hold)
            time += 50
        }
    }

    @Test
    fun aFaceRunningPastTheFrameIsNotKept() {
        assertEquals("Centre your face in the oval", fix(good(0).copy(faceInFrame = false)))
    }

    @Test
    fun aDarkBarAcrossTheBridgeScoresHigherThanSmoothSkin() {
        val size = 40
        val skin = ByteBuffer.wrap(ByteArray(size * size) { 150.toByte() })
        val frame = ByteBuffer.wrap(ByteArray(size * size) { if (it / size in 18..21) 20 else 150.toByte() })
        val box = Box(10, 10, 30, 30)
        assertEquals(0f, bridgeEdgeScore(skin, size, 1, size, size, box))
        assertTrue(bridgeEdgeScore(frame, size, 1, size, size, box)!! > 5f)
        assertNull(bridgeEdgeScore(skin, size, 1, size, size, Box(39, 39, 60, 60)))
    }

    @Test
    fun readoutShowsEveryNumberAndMarksWhatIsMissing() {
        val text = readoutText(FrameReading(0, 1, yaw = 12.34f, pitch = -2f, roll = 1f, faceLuma = 130, blurScore = 88.4, faceWidthFraction = 0.42f, faceCentreOffset = 0.03f))
        assertTrue(text, text.contains("faces 1"))
        assertTrue(text, text.contains("yaw 12.3"))
        assertTrue(text, text.contains("pitch -2.0"))
        assertTrue(text, text.contains("width 0.42"))
        assertTrue(text, text.contains("luma 130  blur 88  pupils -"))
    }
}
