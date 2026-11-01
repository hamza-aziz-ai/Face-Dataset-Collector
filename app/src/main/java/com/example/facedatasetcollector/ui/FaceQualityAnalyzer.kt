package com.example.facedatasetcollector.ui

import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetector

/**
 * Judges each camera frame. Must run on a background executor: [analyze] blocks on detection, and Tasks.await throws on the main thread.
 *
 * WHY blocking: no callback outlives [analyze], so the frame closes in one place and nothing runs after the owner shuts the executor down.
 *
 * @param onResult called on the analyzer thread. The caller must post to the main thread before touching UI state.
 */
class FaceQualityAnalyzer(
    private val detector: FaceDetector,
    private val onResult: (QualityState) -> Unit,
) : ImageAnalysis.Analyzer {

    // Used only on the single analyzer thread, so no synchronization is needed.
    private var frames = 0
    private var totalNanos = 0L
    private var maxNanos = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val start = System.nanoTime()
        try {
            val mediaImage = imageProxy.image ?: return
            val rotation = imageProxy.imageInfo.rotationDegrees
            val input = InputImage.fromMediaImage(mediaImage, rotation)
            val faces = Tasks.await(detector.process(input))
            val luma = faces.singleOrNull()?.let { faceLuma(it, imageProxy, rotation) }
            onResult(qualityFor(faces.size, luma))
        } catch (e: Exception) {
            // A failed check must never read as a good frame or as "no face".
            Log.w(TAG, "Face detection failed", e)
            onResult(QualityState.DetectorFailed)
        } finally {
            imageProxy.close()
            recordTiming(System.nanoTime() - start)
        }
    }

    private fun faceLuma(face: Face, imageProxy: ImageProxy, rotation: Int): Int? {
        val plane = imageProxy.planes[0]
        val bounds = face.boundingBox
        val box = uprightToBuffer(
            Box(bounds.left, bounds.top, bounds.right, bounds.bottom),
            rotation,
            imageProxy.width,
            imageProxy.height,
        )
        return averageLuma(plane.buffer, plane.rowStride, plane.pixelStride, imageProxy.width, imageProxy.height, box)
    }

    /** Logs aggregate timing only, never anything about the person in frame. */
    private fun recordTiming(nanos: Long) {
        frames++
        totalNanos += nanos
        maxNanos = maxOf(maxNanos, nanos)
        if (frames == TIMING_WINDOW) {
            Log.d(TAG, "frames=$frames avgMs=${totalNanos / frames / 1_000_000} maxMs=${maxNanos / 1_000_000} thread=${Thread.currentThread().name}")
            frames = 0
            totalNanos = 0
            maxNanos = 0
        }
    }

    private companion object {
        const val TAG = "FaceQualityAnalyzer"
        const val TIMING_WINDOW = 100
    }
}
