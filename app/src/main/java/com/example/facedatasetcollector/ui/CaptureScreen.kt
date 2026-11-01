package com.example.facedatasetcollector.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as CanvasSize
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.facedatasetcollector.data.Pose
import com.example.facedatasetcollector.ui.theme.GuideMint
import com.example.facedatasetcollector.ui.theme.GuideTeal
import com.example.facedatasetcollector.ui.theme.Night
import com.example.facedatasetcollector.ui.theme.NightControl
import com.example.facedatasetcollector.ui.theme.NightMuted
import com.example.facedatasetcollector.ui.theme.NightPanel
import com.example.facedatasetcollector.ui.theme.NightText
import com.example.facedatasetcollector.ui.theme.NightTrack
import com.example.facedatasetcollector.ui.theme.OnGuideMint
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors

private val AnalysisSize = Size(1920, 1080)

/**
 * The camera, and nothing else. Owns the detector, thread, and analyzer, so that leaving the screen always tears them down in the right order.
 *
 * @param target the pose to capture, or null to judge nothing.
 */
@Composable
fun CaptureCamera(
    target: Pose?,
    onGuidance: (Guidance) -> Unit,
    onPhoto: (Pose, ByteArray) -> Unit,
    onFailure: () -> Unit,
    modifier: Modifier = Modifier,
    onReading: ((FrameReading) -> Unit)? = null,
) {
    val context = LocalContext.current
    val currentGuidance by rememberUpdatedState(onGuidance)
    val currentPhoto by rememberUpdatedState(onPhoto)
    val currentFailure by rememberUpdatedState(onFailure)
    val currentReading by rememberUpdatedState(onReading)
    // Main thread only. WHY: a photo encoded just as this screen closed must not reach the next person's capture.
    val onScreen = remember { mutableStateOf(true) }

    // Configure fast, on-device face detection
    val faceDetector = remember {
        val faceOptions = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
        FaceDetection.getClient(faceOptions)
    }
    // One thread, so frames are judged in order and never on the main thread
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val analyzer = remember {
        val main = ContextCompat.getMainExecutor(context)
        CaptureAnalyzer(
            faceDetector,
            onGuidance = { guidance -> main.execute { currentGuidance(guidance) } },
            onPhoto = { pose, bytes -> main.execute { if (onScreen.value) currentPhoto(pose, bytes) } },
            onFailure = { main.execute { currentFailure() } },
            onReading = if (onReading == null) null else { frame -> main.execute { currentReading?.invoke(frame) } },
        )
    }

    // Set up Analysis
    val analysisUseCase = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy(AnalysisSize, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build()
            )
            .build()
            .also { analysis -> analysis.setAnalyzer(analysisExecutor, analyzer) }
    }

    SideEffect { analyzer.target = target }

    // Order matters: stop new frames, let the running frame finish, then close the detector on the same thread
    DisposableEffect(Unit) {
        onDispose {
            onScreen.value = false
            analyzer.target = null
            analysisUseCase.clearAnalyzer()
            analysisExecutor.execute { faceDetector.close() }
            analysisExecutor.shutdown()
        }
    }

    BoundCamera(CameraSelector.DEFAULT_FRONT_CAMERA, analysisUseCase, currentFailure, modifier)
}

/**
 * Shows the preview and feeds [analysis] from [selector] while on screen.
 *
 * WHY unbind on leaving: a camera stays open for as long as its lifecycle lives, so without this it would run behind later screens.
 */
@Composable
fun BoundCamera(selector: CameraSelector, analysis: ImageAnalysis, onFailure: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val currentFailure by rememberUpdatedState(onFailure)
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    // Main thread only. Stops a bind that would otherwise land after the screen has gone.
    val onScreen = remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        onDispose {
            onScreen.value = false
            // A provider that failed to start has nothing to unbind.
            if (cameraProviderFuture.isDone) runCatching { cameraProviderFuture.get().unbindAll() }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).also { previewView ->
                cameraProviderFuture.addListener({
                    if (!onScreen.value) return@addListener
                    try {
                        val cameraProvider = cameraProviderFuture.get()

                        // Set up Preview
                        val previewUseCase = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(ctx as LifecycleOwner, selector, previewUseCase, analysis)
                    } catch (exc: Exception) {
                        Log.e("BoundCamera", "Binding failed", exc)
                        currentFailure()
                    }
                }, ContextCompat.getMainExecutor(ctx))
            }
        }
    )
}

private enum class TurnCue { NONE, LEFT, RIGHT, UP, DOWN }

private fun cueFor(pose: Pose) = when (pose) {
    Pose.LEFT_30, Pose.LEFT_30_GLASSES -> TurnCue.LEFT
    Pose.RIGHT_30, Pose.RIGHT_30_GLASSES -> TurnCue.RIGHT
    Pose.CHIN_UP, Pose.CHIN_UP_GLASSES -> TurnCue.UP
    Pose.CHIN_DOWN, Pose.CHIN_DOWN_GLASSES -> TurnCue.DOWN
    Pose.FRONT, Pose.FRONT_GLASSES -> TurnCue.NONE
}

/** The oval guide. The ring around it fills while the person holds still. Decorative: the same message is in text. */
@Composable
private fun GuideOverlay(cue: TurnCue, passing: Boolean, progress: Float, modifier: Modifier) {
    Canvas(modifier) {
        val ovalHeight = size.height * 0.68f
        val ovalWidth = minOf(ovalHeight * 0.76f, size.width * 0.78f)
        val topLeft = Offset((size.width - ovalWidth) / 2, (size.height - ovalHeight) / 2)
        val ovalSize = CanvasSize(ovalWidth, ovalHeight)
        drawOval(if (passing) GuideTeal else NightText, topLeft, ovalSize, style = Stroke(3.dp.toPx()))
        if (progress > 0f) {
            drawArc(GuideMint, -90f, 360f * progress, false, topLeft, ovalSize, style = Stroke(6.dp.toPx()))
        }
        drawCue(cue, topLeft, ovalSize)
    }
}

private fun DrawScope.drawCue(cue: TurnCue, oval: Offset, ovalSize: CanvasSize) {
    if (cue == TurnCue.NONE) return
    val gap = 22.dp.toPx()
    val reach = 18.dp.toPx()
    val width = 3.dp.toPx()
    val middle = Offset(oval.x + ovalSize.width / 2, oval.y + ovalSize.height / 2)
    val (tip, along, across) = when (cue) {
        // The preview is mirrored, so the person's own left is on the left of the screen.
        TurnCue.LEFT -> Triple(Offset((oval.x - gap).coerceAtLeast(reach), middle.y), Offset(1f, 0f), Offset(0f, 1f))
        TurnCue.RIGHT -> Triple(Offset((oval.x + ovalSize.width + gap).coerceAtMost(size.width - reach), middle.y), Offset(-1f, 0f), Offset(0f, 1f))
        TurnCue.UP -> Triple(Offset(middle.x, (oval.y - gap).coerceAtLeast(reach)), Offset(0f, 1f), Offset(1f, 0f))
        else -> Triple(Offset(middle.x, (oval.y + ovalSize.height + gap).coerceAtMost(size.height - reach)), Offset(0f, -1f), Offset(1f, 0f))
    }
    drawLine(NightText, tip, tip + along * reach * 1.6f, width)
    drawLine(NightText, tip, tip + along * reach * 0.7f + across * reach * 0.7f, width)
    drawLine(NightText, tip, tip + along * reach * 0.7f - across * reach * 0.7f, width)
}

@Composable
private fun PoseProgress(done: Int, total: Int, photoNumber: Int, photoTotal: Int, withGlasses: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (withGlasses) "With glasses" else "Without glasses", color = NightMuted, style = MaterialTheme.typography.bodySmall)
            Text("$done of $total done", color = NightMuted, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(total) { index ->
                val color = when {
                    index < done -> GuideTeal
                    index == done -> NightText
                    else -> NightTrack
                }
                Box(Modifier.weight(1f).height(6.dp).background(color, RoundedCornerShape(3.dp)))
            }
        }
        Text("Photo $photoNumber of $photoTotal", color = NightMuted, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun PosePrompt(pose: Pose, guidance: Guidance?, failed: Boolean) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            promptFor(pose),
            color = NightText,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        val holding = guidance is Guidance.Hold
        val message = when {
            failed -> "The face check stopped working. Cancel and start again."
            guidance is Guidance.Fix -> guidance.text
            holding -> "Hold still, taking the photo"
            else -> "Getting the camera ready"
        }
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .background(if (holding) GuideMint else NightControl, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                message,
                color = if (holding) OnGuideMint else NightText,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** Tuning aid. Sits on the camera panel, so `FLAG_SECURE` covers it. */
@Composable
fun ReadoutPanel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = NightText,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier.background(Night.copy(alpha = 0.75f), RoundedCornerShape(8.dp)).padding(8.dp),
    )
}

@Composable
fun CancelButton(text: String = "Cancel and delete", onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = NightControl, contentColor = NightText),
    ) { Text(text, style = MaterialTheme.typography.labelMedium) }
}

/** Always dark, whatever the device theme, so attention stays on the guide. */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CaptureScreen(
    pose: Pose,
    set: List<Pose>,
    uploadingTo: String,
    showReadout: Boolean,
    onPhoto: (Pose, ByteArray) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var reading by remember { mutableStateOf<FrameReading?>(null) }
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    var guidance by remember(pose) { mutableStateOf<Guidance?>(null) }
    var failed by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }

    // Request permission on first compose
    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
    }
    BackHandler { confirmCancel = true }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Delete these photos?") },
            text = { Text("Photos taken so far are deleted from this device. The person's consent stays on record.") },
            confirmButton = { TextButton(onClick = onCancel) { Text("Cancel and delete") } },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Keep going") } },
        )
    }

    val withGlasses = pose.withGlasses
    val group = set.filter { it.withGlasses == withGlasses }
    val done = group.indexOf(pose).coerceAtLeast(0)
    val photoNumber = set.indexOf(pose) + 1

    val camera = @Composable { cameraModifier: Modifier ->
        Box(cameraModifier.clip(RoundedCornerShape(16.dp)).background(NightPanel)) {
            if (cameraPermission.status.isGranted) {
                CaptureCamera(
                    target = if (failed || confirmCancel) null else pose,
                    onGuidance = { guidance = it },
                    onPhoto = onPhoto,
                    onFailure = { failed = true },
                    modifier = Modifier.fillMaxSize(),
                    onReading = if (showReadout) ({ reading = it }) else null,
                )
                GuideOverlay(
                    cueFor(pose),
                    passing = guidance is Guidance.Hold,
                    progress = (guidance as? Guidance.Hold)?.progress ?: 0f,
                    modifier = Modifier.fillMaxSize(),
                )
                reading?.let { ReadoutPanel(readoutText(it), Modifier.align(Alignment.BottomStart).padding(8.dp)) }
            } else {
                CameraPermissionNeeded(
                    "This does not replace the person's consent. Turn on camera access in Settings, then come back.",
                    Modifier.fillMaxSize(),
                )
            }
        }
    }
    val uploadLine = @Composable {
        Text("Uploading to: $uploadingTo", color = NightMuted, style = MaterialTheme.typography.bodySmall)
    }

    TabletScaledText {
        BoxWithConstraints(modifier.fillMaxSize().background(Night).padding(16.dp)) {
            if (isWideLayout(maxWidth, maxHeight)) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    camera(Modifier.weight(1.5f).fillMaxSize())
                    Column(
                        Modifier.weight(1f).fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CancelButton { confirmCancel = true }
                            uploadLine()
                            PoseProgress(done, group.size, photoNumber, set.size, withGlasses)
                        }
                        PosePrompt(pose, guidance, failed)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        CancelButton { confirmCancel = true }
                    }
                    uploadLine()
                    PoseProgress(done, group.size, photoNumber, set.size, withGlasses)
                    camera(Modifier.weight(1f).fillMaxWidth())
                    PosePrompt(pose, guidance, failed)
                }
            }
        }
    }
}

@Composable
fun CameraPermissionNeeded(message: String, modifier: Modifier) {
    val context = LocalContext.current
    Column(
        modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Camera access is needed", color = NightText, style = MaterialTheme.typography.titleMedium)
        Text(
            message,
            color = NightMuted,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            },
            colors = ButtonDefaults.buttonColors(containerColor = GuideMint, contentColor = OnGuideMint),
        ) { Text("Open Settings") }
    }
}
