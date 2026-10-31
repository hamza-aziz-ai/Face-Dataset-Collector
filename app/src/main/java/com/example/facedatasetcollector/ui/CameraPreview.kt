package com.example.facedatasetcollector.ui

import android.Manifest
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors


@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // Remember our quality state
    var quality by remember { mutableStateOf<QualityState>(QualityState.NoFaceDetected) }

    // Request permission on first compose
    LaunchedEffect(Unit) {
        cameraPermissionState.launchPermissionRequest()
    }

    // If not granted, show a message
    if (!cameraPermissionState.status.isGranted) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Camera permission required")
        }
        return
    }

    // Once granted, show the camera preview
    Box(modifier.fillMaxSize()) {
        val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
        // Configure fast, on-device face detection
        val faceDetector = remember {
            val faceOptions = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build()
            FaceDetection.getClient(faceOptions)
        }
        // One thread, so frames are judged in order and never on the main thread
        val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

        // Set up Analysis
        val analysisUseCase = remember {
            val mainExecutor = ContextCompat.getMainExecutor(context)
            ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(
                        analysisExecutor,
                        FaceQualityAnalyzer(faceDetector) { result ->
                            mainExecutor.execute { quality = result }
                        }
                    )
                }
        }

        // Order matters: stop new frames, let the running frame finish, then close the detector on the same thread
        DisposableEffect(Unit) {
            onDispose {
                analysisUseCase.clearAnalyzer()
                analysisExecutor.execute { faceDetector.close() }
                analysisExecutor.shutdown()
            }
        }

        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                PreviewView(ctx).also { previewView ->
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        // Set up Preview
                        val previewUseCase = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                ctx as LifecycleOwner,
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                previewUseCase,
                                analysisUseCase
                            )
                        } catch (exc: Exception) {
                            Log.e("CameraPreview", "Binding failed", exc)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            }
        )

        // Overlay a warning banner if needed
        when (quality) {
            is QualityState.TooDark -> QualityWarning("Too dark — raise brightness")
            is QualityState.TooBright -> QualityWarning("Too bright — lower brightness")
            is QualityState.NoFaceDetected -> QualityWarning("No face detected – please position your face in view")
            is QualityState.MultipleFaces -> QualityWarning("Multiple faces detected – use only one face at a time")
            is QualityState.DetectorFailed -> QualityWarning("Face check failed – please restart the app")
            else -> { /* nothing to show */ }
        }
    }
}

@Composable
fun QualityWarning(message: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(Color(0xAA000000), RoundedCornerShape(8.dp))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = Color.White)
    }
}
