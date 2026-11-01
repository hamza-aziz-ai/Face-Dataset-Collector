package com.example.facedatasetcollector.ui

import android.Manifest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.facedatasetcollector.data.decodeQr
import com.example.facedatasetcollector.ui.theme.GuideMint
import com.example.facedatasetcollector.ui.theme.Night
import com.example.facedatasetcollector.ui.theme.NightMuted
import com.example.facedatasetcollector.ui.theme.NightPanel
import com.example.facedatasetcollector.ui.theme.NightText
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.concurrent.Executors

/** Reads brightness only, and keeps nothing from a frame: a person in view leaves no trace. Reports the first code once. */
private class QrAnalyzer(private val onCode: (String) -> Unit) : ImageAnalysis.Analyzer {
    // Used only on the single analyzer thread.
    private var found = false

    override fun analyze(image: ImageProxy) {
        try {
            if (found) return
            val plane = image.planes[0]
            val luma = ByteArray(plane.buffer.remaining()).also { plane.buffer.get(it) }
            val text = decodeQr(luma, plane.rowStride, image.width, image.height) ?: return
            found = true
            onCode(text)
        } finally {
            image.close()
        }
    }
}

@Composable
private fun QrCamera(onCode: (String) -> Unit, onFailure: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val currentCode by rememberUpdatedState(onCode)
    // Main thread only. Drops a code that was posted just as staff cancelled.
    val onScreen = remember { mutableStateOf(true) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val analysis = remember {
        val main = ContextCompat.getMainExecutor(context)
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(executor, QrAnalyzer { code -> main.execute { if (onScreen.value) currentCode(code) } }) }
    }
    DisposableEffect(Unit) {
        onDispose {
            onScreen.value = false
            analysis.clearAnalyzer()
            executor.shutdown()
        }
    }
    BoundCamera(CameraSelector.DEFAULT_BACK_CAMERA, analysis, onFailure, modifier)
}

/** Decorative: shows where to hold the code, which can be read anywhere in view. */
@Composable
private fun ScanFrame(modifier: Modifier) {
    Canvas(modifier) {
        val side = minOf(size.width, size.height) * 0.62f
        drawRoundRect(
            GuideMint,
            Offset((size.width - side) / 2, (size.height - side) / 2),
            Size(side, side),
            CornerRadius(16.dp.toPx()),
            style = Stroke(3.dp.toPx()),
        )
    }
}

/** Always dark, like capture. Splits from [QrScanScreen] so a layout can be checked with no camera open. */
@Composable
fun QrScanLayout(onCancel: () -> Unit, modifier: Modifier = Modifier, camera: @Composable (Modifier) -> Unit) {
    val panel = @Composable { panelModifier: Modifier ->
        Box(panelModifier.clip(RoundedCornerShape(16.dp)).background(NightPanel)) {
            camera(Modifier.fillMaxSize())
        }
    }
    val words = @Composable {
        Text("Scan setup code", color = NightText, style = MaterialTheme.typography.headlineMedium)
        Text(
            "Point the back camera at the code from your administrator. It connects by itself.",
            color = NightMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    TabletScaledText {
        BoxWithConstraints(modifier.fillMaxSize().background(Night).padding(16.dp)) {
            // Any sideways screen: a phone in landscape has too little height to stack these.
            if (maxWidth > maxHeight) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    panel(Modifier.weight(1.5f).fillMaxSize())
                    Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CancelButton("Cancel", onCancel)
                        words()
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CancelButton("Cancel", onCancel)
                    words()
                    panel(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QrScanScreen(onCode: (String) -> Unit, onFailure: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
    }
    QrScanLayout(onCancel, modifier) { cameraModifier ->
        if (cameraPermission.status.isGranted) {
            Box(cameraModifier) {
                QrCamera(onCode, onFailure, Modifier.fillMaxSize())
                ScanFrame(Modifier.fillMaxSize())
            }
        } else {
            CameraPermissionNeeded(
                "Turn on camera access in Settings, then come back. Or cancel and type the server address.",
                cameraModifier,
            )
        }
    }
}
