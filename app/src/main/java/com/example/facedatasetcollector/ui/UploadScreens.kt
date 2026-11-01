package com.example.facedatasetcollector.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.facedatasetcollector.data.PoseProblem
import com.example.facedatasetcollector.ui.theme.LocalStatusColors

private enum class StepState { DONE, RUNNING, WAITING }

@Composable
private fun StepMark(state: StepState) {
    val done = LocalStatusColors.current.successText
    val colors = MaterialTheme.colorScheme
    when (state) {
        StepState.RUNNING -> CircularProgressIndicator(Modifier.size(24.dp), color = colors.primary, strokeWidth = 2.dp)
        StepState.DONE, StepState.WAITING -> Canvas(Modifier.size(24.dp)) {
            val ring: Color = if (state == StepState.DONE) done else colors.outline
            drawCircle(ring, style = Stroke(2.dp.toPx()))
            if (state == StepState.DONE) {
                val w = size.width
                val stroke = 2.dp.toPx()
                drawLine(ring, Offset(w * 0.3f, w * 0.52f), Offset(w * 0.45f, w * 0.67f), stroke, StrokeCap.Round)
                drawLine(ring, Offset(w * 0.45f, w * 0.67f), Offset(w * 0.72f, w * 0.38f), stroke, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun StepRow(title: String, detail: String, state: StepState) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .background(colors.surface, RoundedCornerShape(10.dp))
            .border(
                if (state == StepState.RUNNING) 2.dp else 1.dp,
                if (state == StepState.RUNNING) colors.primary else colors.outlineVariant,
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StepMark(state)
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
fun UploadScreen(
    personName: String,
    photoCount: Int,
    noticeVersion: String,
    state: UploadState,
    isMock: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sending = state is UploadState.Sending
    val finished = state is UploadState.Accepted || state is UploadState.StillChecking
    StaffPage(modifier, maxContentWidth = 640.dp, centred = true) {
        MockChip(isMock)
        Column {
            Text(
                when (state) {
                    UploadState.Accepted -> "Enrolment accepted"
                    UploadState.StillChecking -> "Still checking"
                    else -> "Sending enrolment"
                },
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
            )
            Text(
                "$personName, $photoCount photos",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StepRow("Consent recorded", "Notice version $noticeVersion", StepState.DONE)
            StepRow(
                if (sending) "Sending photos" else "Photos sent",
                if (sending) "Keep the app open" else "Removed from this device",
                if (sending) StepState.RUNNING else StepState.DONE,
            )
            StepRow(
                when (state) {
                    UploadState.Accepted -> "Checks passed"
                    UploadState.StillChecking -> "The server is still checking"
                    else -> "Server checks"
                },
                when (state) {
                    UploadState.Accepted -> "Roster shows this person as enrolled"
                    UploadState.StillChecking -> "The result will appear on the roster"
                    UploadState.Sending -> "Starts after the photos arrive"
                    else -> "This usually takes a moment"
                },
                when (state) {
                    UploadState.Sending -> StepState.WAITING
                    UploadState.Accepted -> StepState.DONE
                    UploadState.StillChecking -> StepState.WAITING
                    else -> StepState.RUNNING
                },
            )
        }
        if (finished) PrimaryButton("Enrol next person", busy = false, onClick = onDone)
    }
}

@Composable
fun RejectedScreen(
    personName: String,
    photoCount: Int,
    problems: List<PoseProblem>,
    stillEnrolled: Boolean,
    onRetakeAll: () -> Unit,
    onBackToRoster: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StaffPage(modifier, maxContentWidth = 640.dp, centred = true) {
        Column {
            Text(
                "Photos were not accepted",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
            )
            Text(personName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("The server deleted this set. Take all $photoCount photos again and these should pass.", style = MaterialTheme.typography.bodyLarge)
        if (problems.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                problems.forEach { problem ->
                    Column {
                        Text(poseNameFor(problem.pose), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            adviceFor(problem.problem),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (stillEnrolled) Text("Their current enrolment stays in use.", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Consent is still on record, so the person does not need to agree again.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryButton("Take all $photoCount photos again", busy = false, onClick = onRetakeAll)
        TextButton(onClick = onBackToRoster, modifier = Modifier.fillMaxWidth()) { Text("Back to roster") }
    }
}
