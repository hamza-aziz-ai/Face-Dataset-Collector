package com.example.facedatasetcollector.ui

import com.example.facedatasetcollector.data.Pose
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.roundToInt

/** Everything measured on one frame. Pure numbers, so the whole decision runs in JVM unit tests with synthetic values. */
data class FrameReading(
    val timeMs: Long,
    val faceCount: Int,
    val yaw: Float = 0f,
    val pitch: Float = 0f,
    val roll: Float = 0f,
    val faceLuma: Int? = null,
    val blurScore: Double? = null,
    val faceWidthFraction: Float = 0f,
    val faceCentreOffset: Float = 0f,
)

sealed interface Guidance {
    /** Something to fix. [text] is what the person sees. */
    data class Fix(val text: String) : Guidance

    /** Everything passes. Stay still. */
    data class Hold(val progress: Float) : Guidance

    /** Take this frame. Comes exactly once per pose. */
    data object TakePhoto : Guidance
}

fun promptFor(pose: Pose): String = when (pose) {
    Pose.FRONT, Pose.FRONT_GLASSES -> "Look straight at the camera"
    Pose.CHIN_UP, Pose.CHIN_UP_GLASSES -> "Lift your chin"
    Pose.CHIN_DOWN, Pose.CHIN_DOWN_GLASSES -> "Lower your chin"
    Pose.LEFT_30, Pose.LEFT_30_GLASSES -> "Turn your head to the left"
    Pose.RIGHT_30, Pose.RIGHT_30_GLASSES -> "Turn your head to the right"
}

private data class AngleRange(val yaw: ClosedFloatingPointRange<Float>, val pitch: ClosedFloatingPointRange<Float>)

private fun rangeFor(pose: Pose): AngleRange {
    val p = QualityPolicy
    val straightYaw = -p.STRAIGHT_LIMIT..p.STRAIGHT_LIMIT
    val straightPitch = -p.STRAIGHT_LIMIT..p.STRAIGHT_LIMIT
    return when (pose) {
        Pose.FRONT, Pose.FRONT_GLASSES -> AngleRange(straightYaw, straightPitch)
        Pose.CHIN_UP, Pose.CHIN_UP_GLASSES -> AngleRange(straightYaw, p.CHIN_MIN..p.CHIN_MAX)
        Pose.CHIN_DOWN, Pose.CHIN_DOWN_GLASSES -> AngleRange(straightYaw, -p.CHIN_MAX..-p.CHIN_MIN)
        Pose.LEFT_30, Pose.LEFT_30_GLASSES -> AngleRange(p.TURN_MIN..p.TURN_MAX, straightPitch)
        Pose.RIGHT_30, Pose.RIGHT_30_GLASSES -> AngleRange(-p.TURN_MAX..-p.TURN_MIN, straightPitch)
    }
}

/**
 * Decides, frame by frame, when one pose is good enough to keep. Call [onFrame] from one thread only.
 *
 * Deliberately no eyes-open check: lowered eyelids are normal in the chin-down pose.
 */
class PoseGuide(private val pose: Pose) {
    private var steadySince: Long? = null
    private var steadyYaw = 0f
    private var steadyPitch = 0f
    private var lastFrameMs: Long? = null
    private var taken = false

    fun onFrame(frame: FrameReading): Guidance {
        // Never fires twice, even if frames keep arriving after the photo is taken.
        if (taken) return Guidance.Hold(1f)

        // A long gap means nothing is known about what happened in it, so the wait starts over.
        val previous = lastFrameMs
        lastFrameMs = frame.timeMs
        if (previous != null && frame.timeMs - previous > QualityPolicy.MAX_FRAME_GAP_MS) steadySince = null

        val problem = problemWith(frame)
        if (problem != null) {
            steadySince = null
            return Guidance.Fix(problem)
        }

        // Yaw uses the same sign as the pose ranges, but only differences matter here.
        val moved = abs(frame.yaw - steadyYaw) > QualityPolicy.STEADY_DEGREES ||
            abs(frame.pitch - steadyPitch) > QualityPolicy.STEADY_DEGREES
        val start = steadySince
        if (start == null || moved) {
            steadySince = frame.timeMs
            steadyYaw = frame.yaw
            steadyPitch = frame.pitch
            return Guidance.Hold(0f)
        }
        val held = frame.timeMs - start
        if (held < QualityPolicy.STEADY_MS) return Guidance.Hold(held.toFloat() / QualityPolicy.STEADY_MS)
        taken = true
        return Guidance.TakePhoto
    }

    private fun problemWith(frame: FrameReading): String? {
        val policy = QualityPolicy
        if (frame.faceCount == 0) return "Place your face inside the oval"
        if (frame.faceCount > 1) return "Only one person should be in view"
        if (frame.faceWidthFraction < policy.MIN_FACE_WIDTH) return "Move closer"
        if (frame.faceWidthFraction > policy.MAX_FACE_WIDTH) return "Move back a little"
        if (frame.faceCentreOffset > policy.MAX_CENTRE_OFFSET) return "Centre your face in the oval"

        val range = rangeFor(pose)
        val yaw = frame.yaw * policy.YAW_SIGN
        if (abs(frame.roll) > policy.MAX_ROLL) return "Keep your head level"
        if (yaw !in range.yaw || frame.pitch !in range.pitch) return adjustment(yaw, frame.pitch, range)

        val luma = frame.faceLuma ?: return "Place your face inside the oval"
        if (luma < policy.MIN_FACE_LUMA) return "Too dark. Find more light"
        if (luma > policy.MAX_FACE_LUMA) return "Too bright. Move out of direct light"
        val blur = frame.blurScore ?: return "Hold still"
        if (blur < policy.MIN_BLUR_SCORE) return "Hold still"
        return null
    }

    private fun adjustment(yaw: Float, pitch: Float, range: AngleRange): String = when {
        // Positive yaw is the person's own left.
        yaw < range.yaw.start -> if (range.yaw.start > 0) promptFor(pose) else "Turn a little to your left"
        yaw > range.yaw.endInclusive -> if (range.yaw.endInclusive < 0) promptFor(pose) else "Turn a little to your right"
        pitch < range.pitch.start -> if (range.pitch.start > 0) promptFor(pose) else "Lift your chin a little"
        else -> if (range.pitch.endInclusive < 0) promptFor(pose) else "Lower your chin a little"
    }
}

/**
 * Sharpness of a face region: the variance of the Laplacian on a fixed-size sample of the Y plane. The sample size is fixed so the score does not depend on how close the face is.
 *
 * @return null when the box lies outside the frame or is too small to sample.
 */
fun blurScore(buffer: ByteBuffer, rowStride: Int, pixelStride: Int, bufferWidth: Int, bufferHeight: Int, box: Box): Double? {
    val left = box.left.coerceAtLeast(0)
    val top = box.top.coerceAtLeast(0)
    val right = box.right.coerceAtMost(bufferWidth)
    val bottom = box.bottom.coerceAtMost(bufferHeight)
    val size = QualityPolicy.BLUR_SAMPLE_SIZE
    if (right - left < size || bottom - top < size) return null

    val sample = IntArray(size * size)
    for (row in 0 until size) {
        val y = top + row * (bottom - top) / size
        for (column in 0 until size) {
            val x = left + column * (right - left) / size
            sample[row * size + column] = buffer.get(y * rowStride + x * pixelStride).toInt() and 0xFF
        }
    }

    var sum = 0.0
    var squares = 0.0
    var count = 0
    for (row in 1 until size - 1) {
        for (column in 1 until size - 1) {
            val at = row * size + column
            val laplacian = (sample[at - 1] + sample[at + 1] + sample[at - size] + sample[at + size] - 4 * sample[at]).toDouble()
            sum += laplacian
            squares += laplacian * laplacian
            count++
        }
    }
    val mean = sum / count
    return squares / count - mean * mean
}

/**
 * The 4:5 portrait crop to keep, in upright image coordinates: head and shoulders, matching the target photo in docs/features/FEATURE-001-enrolment.md. Stays inside the frame, so a face near an edge sits off-centre rather than being padded.
 */
fun portraitCrop(face: Box, frameWidth: Int, frameHeight: Int): Box {
    val faceWidth = face.right - face.left
    val idealWidth = (faceWidth / QualityPolicy.CROP_FACE_FRACTION).roundToInt()
    // A multiple of 4 keeps the ratio exactly 4:5.
    val width = minOf(idealWidth, frameWidth, frameHeight * 4 / 5) / 4 * 4
    val height = width * 5 / 4

    val left = ((face.left + face.right) / 2 - width / 2).coerceIn(0, frameWidth - width)
    val top = (face.top - (height * QualityPolicy.CROP_TOP_MARGIN).roundToInt()).coerceIn(0, frameHeight - height)
    return Box(left, top, left + width, top + height)
}
