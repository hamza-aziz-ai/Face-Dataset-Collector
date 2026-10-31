package com.example.facedatasetcollector.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.facedatasetcollector.data.Pose

// A preview needs far fewer pixels than the photo. This caps decoded memory (D-002).
private const val PREVIEW_SAMPLE_SIZE = 3

private fun previewOf(jpeg: ByteArray): ImageBitmap? =
    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = PREVIEW_SAMPLE_SIZE })?.asImageBitmap()

@Composable
private fun PhotoTile(pose: Pose, jpeg: ByteArray, onRetake: () -> Unit, modifier: Modifier) {
    val preview = remember(jpeg) { previewOf(jpeg) }
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp)),
    ) {
        if (preview != null) {
            Image(
                preview,
                contentDescription = "Photo, ${pose.label}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.8f),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(pose.label, style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = onRetake) { Text("Retake") }
        }
    }
}

/**
 * Staff check every photo before anything leaves the device.
 *
 * @param photos in memory only. Nothing here writes them anywhere (D-002).
 */
@Composable
fun ReviewScreen(
    personName: String,
    photos: Map<Pose, ByteArray>,
    set: List<Pose>,
    isMock: Boolean,
    busy: Boolean,
    error: String?,
    onRetake: (Pose) -> Unit,
    onUpload: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmCancel by remember { mutableStateOf(false) }
    var showGlasses by remember { mutableStateOf(false) }
    BackHandler { confirmCancel = true }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Delete these photos?") },
            text = { Text("All photos are deleted from this device. The person's consent stays on record.") },
            confirmButton = { TextButton(onClick = onCancel) { Text("Cancel and delete all") } },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Keep reviewing") } },
        )
    }

    BoxWithConstraints(modifier) {
        val wide = isWideLayout(maxWidth, maxHeight)
        val columns = when {
            wide -> 5
            isTabletLayout(maxWidth) -> 3
            else -> 2
        }
        val hasGlasses = set.any { it.withGlasses }
        // Side by side on a large screen, the two sets show together, so there is no need to switch.
        val visible = when {
            wide -> set
            hasGlasses -> set.filter { it.withGlasses == showGlasses }
            else -> set
        }
        StaffPage(maxContentWidth = if (wide) 1160.dp else 1400.dp) {
            MockChip(isMock)
            val title = @Composable { titleModifier: Modifier ->
                Column(titleModifier) {
                    Text("Review photos", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    Text(
                        "$personName. Nothing is sent until you upload.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val actions = @Composable {
                ErrorMessage(error)
                PrimaryButton("Upload ${set.size} photos", busy, onClick = onUpload)
                TextButton(onClick = { confirmCancel = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel and delete all", color = MaterialTheme.colorScheme.error)
                }
            }
            if (wide) {
                // Beside the title, so the upload button is never below the fold.
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                    title(Modifier.weight(1f))
                    Column(Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
                }
            } else {
                title(Modifier)
            }
            if (hasGlasses && !wide) {
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChoiceButton("No glasses, 5", !showGlasses, { showGlasses = false }, Modifier.weight(1f))
                    ChoiceButton("With glasses, 5", showGlasses, { showGlasses = true }, Modifier.weight(1f))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                visible.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { pose ->
                            val jpeg = photos[pose]
                            if (jpeg != null) PhotoTile(pose, jpeg, { onRetake(pose) }, Modifier.weight(1f))
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            if (!wide) actions()
        }
    }
}
