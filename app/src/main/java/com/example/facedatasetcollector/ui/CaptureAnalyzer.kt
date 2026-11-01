package com.example.facedatasetcollector.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.facedatasetcollector.data.Pose
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceLandmark
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Judges each camera frame, and keeps the exact frame that passed (D-003). Must run on a background executor: [analyze] blocks on detection, and Tasks.await throws on the main thread.
 *
 * WHY blocking: no callback outlives [analyze], so the frame closes in one place and nothing runs after the owner shuts the executor down.
 *
 * Callbacks run on the analyzer thread. The caller must post to the main thread before touching UI state.
 */
class CaptureAnalyzer(
    private val detector: FaceDetector,
    private val onGuidance: (Guidance) -> Unit,
    private val onPhoto: (Pose, ByteArray) -> Unit,
    private val onFailure: () -> Unit,
    /** Set only for tuning against the mock. Also turns on the per-pose tally. */
    private val onReading: ((FrameReading) -> Unit)? = null,
) : ImageAnalysis.Analyzer {

    /** The pose being captured. Null means do not capture: frames are then dropped, so no callback can arrive after cancel. */
    @Volatile
    var target: Pose? = null

    private var guide: PoseGuide? = null
    private var guidePose: Pose? = null

    // Used only on the single analyzer thread, so no synchronization is needed.
    private var frames = 0
    private var totalNanos = 0L
    private var maxNanos = 0L
    private val fixes = HashMap<String, Int>()
    private var poseFrames = 0

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val start = System.nanoTime()
        try {
            val mediaImage = imageProxy.image ?: return
            val pose = target ?: return
            val rotation = imageProxy.imageInfo.rotationDegrees
            val faces = Tasks.await(detector.process(InputImage.fromMediaImage(mediaImage, rotation)))
            // The target may have changed during detection. This frame was not judged for the new one.
            if (target != pose) return

            if (guidePose != pose) {
                guide = PoseGuide(pose)
                guidePose = pose
            }
            val reading = reading(faces, imageProxy, rotation)
            onReading?.invoke(reading)
            val guidance = guide?.onFrame(reading) ?: return
            if (onReading != null) tally(pose, guidance)
            when (guidance) {
                Guidance.TakePhoto -> keep(pose, faces.single(), imageProxy, rotation)
                else -> onGuidance(guidance)
            }
        } catch (e: Exception) {
            // A failed check must never read as a good frame or as "no face".
            Log.w(TAG, "Frame check failed", e)
            onFailure()
        } finally {
            imageProxy.close()
            recordTiming(System.nanoTime() - start)
        }
    }

    private fun reading(faces: List<Face>, imageProxy: ImageProxy, rotation: Int): FrameReading {
        val now = SystemClock.elapsedRealtime()
        val face = faces.singleOrNull() ?: return FrameReading(now, faces.size)

        val turned = rotation == 90 || rotation == 270
        val frameWidth = if (turned) imageProxy.height else imageProxy.width
        val frameHeight = if (turned) imageProxy.width else imageProxy.height
        // The widest crop this frame allows, so that the framing checks mean the same in portrait and landscape.
        val usableWidth = minOf(frameWidth, frameHeight * 4 / 5).toFloat()

        val bounds = face.boundingBox
        val box = Box(bounds.left, bounds.top, bounds.right, bounds.bottom)
        val inBuffer = uprightToBuffer(box, rotation, imageProxy.width, imageProxy.height)
        val plane = imageProxy.planes[0]
        return FrameReading(
            timeMs = now,
            faceCount = 1,
            yaw = face.headEulerAngleY,
            pitch = face.headEulerAngleX,
            roll = face.headEulerAngleZ,
            faceLuma = averageLuma(plane.buffer, plane.rowStride, plane.pixelStride, imageProxy.width, imageProxy.height, inBuffer),
            blurScore = blurScore(plane.buffer, plane.rowStride, plane.pixelStride, imageProxy.width, imageProxy.height, inBuffer),
            faceWidthFraction = (box.right - box.left) / usableWidth,
            faceCentreOffset = abs((box.left + box.right) / 2f - frameWidth / 2f) / usableWidth,
            pupilDistance = pupilDistance(face),
            faceInFrame = box.left >= 0 && box.top >= 0 && box.right <= frameWidth && box.bottom <= frameHeight,
        )
    }

    private fun pupilDistance(face: Face): Float? {
        // Either eye can be missing at a side pose, so this stays optional.
        val left = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val right = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
        return hypot(left.x - right.x, left.y - right.y)
    }

    private fun keep(pose: Pose, face: Face, imageProxy: ImageProxy, rotation: Int) {
        val jpeg = encode(face, imageProxy, rotation)
        if (jpeg == null) {
            // Too large to send. Forget this attempt, so the person is not left waiting on a pose that already fired.
            guidePose = null
            onGuidance(Guidance.Fix("Hold still"))
        } else {
            onPhoto(pose, jpeg)
        }
    }

    /** Analysis frames are never mirrored, so the kept JPEG is not either, unlike the preview. No EXIF: the pixels are already upright. */
    private fun encode(face: Face, imageProxy: ImageProxy, rotation: Int): ByteArray? {
        val raw = imageProxy.toBitmap()
        val upright = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        val bounds = face.boundingBox
        val crop = portraitCrop(Box(bounds.left, bounds.top, bounds.right, bounds.bottom), upright.width, upright.height)
        val kept = Bitmap.createBitmap(upright, crop.left, crop.top, crop.right - crop.left, crop.bottom - crop.top)
        try {
            for (quality in QualityPolicy.JPEG_QUALITIES) {
                val out = ByteArrayOutputStream()
                kept.compress(Bitmap.CompressFormat.JPEG, quality, out)
                if (out.size() <= QualityPolicy.MAX_PHOTO_BYTES) return out.toByteArray()
            }
            return null
        } finally {
            if (kept !== upright) kept.recycle()
            if (upright !== raw) upright.recycle()
            raw.recycle()
        }
    }

    /** Counts which checks held a pose back. Logs fixed prompt texts and counts only, so no measurement of the person leaves the screen. */
    private fun tally(pose: Pose, guidance: Guidance) {
        poseFrames++
        if (guidance is Guidance.Fix) fixes[guidance.text] = (fixes[guidance.text] ?: 0) + 1
        if (guidance == Guidance.TakePhoto) {
            Log.d(TAG, "pose=${pose.wireName} frames=$poseFrames fixes=$fixes")
            poseFrames = 0
            fixes.clear()
        }
    }

    /** Logs aggregate timing only, never anything about the person in frame. */
    private fun recordTiming(nanos: Long) {
        frames++
        totalNanos += nanos
        maxNanos = maxOf(maxNanos, nanos)
        if (frames == TIMING_WINDOW) {
            Log.d(TAG, "frames=$frames avgMs=${totalNanos / frames / 1_000_000} maxMs=${maxNanos / 1_000_000}")
            frames = 0
            totalNanos = 0
            maxNanos = 0
        }
    }

    private companion object {
        const val TAG = "CaptureAnalyzer"
        const val TIMING_WINDOW = 100
    }
}
