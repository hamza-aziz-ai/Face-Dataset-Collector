package com.example.facedatasetcollector.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.facedatasetcollector.ui.theme.LocalStatusColors

private val TabletWidth = 600.dp
private val WideWidth = 840.dp

/** True on a large screen held sideways, where two panes fit better than one long column. */
fun isWideLayout(width: Dp, height: Dp): Boolean = width >= WideWidth && width > height

fun isTabletLayout(width: Dp): Boolean = width >= TabletWidth

/** Larger text on bigger screens, because a tablet is held further away and is often shown to the person. Keeps the user's own font size setting. */
fun tabletFontScale(smallestWidth: Dp): Float = when {
    smallestWidth >= 800.dp -> 1.3f
    smallestWidth >= TabletWidth -> 1.15f
    else -> 1f
}

/**
 * Page frame for staff screens. Scrolls with the keyboard open, and keeps a readable width on a tablet.
 *
 * @param maxContentWidth the widest the content may grow.
 * @param centred on a tablet, sits the content in the middle of the screen. Short forms use this, so they do not leave the lower half empty.
 */
@Composable
fun StaffPage(
    modifier: Modifier = Modifier,
    maxContentWidth: Dp = 560.dp,
    centred: Boolean = false,
    content: @Composable () -> Unit,
) {
    TabletScaledText {
        // WHY: text with no colour of its own otherwise falls back to black, which is unreadable in the dark theme.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        BoxWithConstraints(
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val inMiddle = centred && isTabletLayout(maxWidth)
            Column(
                Modifier
                    .widthIn(max = maxContentWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = if (inMiddle) maxHeight else 0.dp)
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = if (inMiddle) {
                    Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
                } else {
                    Arrangement.spacedBy(20.dp)
                },
            ) { content() }
        }
        }
    }
}

/** Applies [tabletFontScale] to everything inside. */
@Composable
fun TabletScaledText(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val scale = tabletFontScale(LocalConfiguration.current.smallestScreenWidthDp.dp)
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * scale), content = content)
}

/** Keeps faces out of screenshots, screen recordings, and the recent-apps thumbnail while it is on screen. */
@Composable
fun SecureWindow() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as Activity).window
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

@Composable
fun PrimaryButton(text: String, busy: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = Modifier.fillMaxWidth().height(if (LocalConfiguration.current.smallestScreenWidthDp >= 600) 64.dp else 52.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Body of the cancel dialogs, which is also where a person's change of mind is recorded. */
@Composable
fun CancelDialogText(text: String, reEnrolling: Boolean, onWithdraw: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text)
        if (reEnrolling) Text(RE_ENROL_STAYS)
        TextButton(onClick = onWithdraw) { Text("Person withdrew consent") }
    }
}

/** Read out by screen readers as soon as it appears. */
@Composable
fun ErrorMessage(message: String?) {
    if (message == null) return
    Text(
        message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Marks any screen talking to the development mock, so test data is never mistaken for real. */
@Composable
fun MockChip(visible: Boolean) {
    if (!visible) return
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            "MOCK SERVER",
            color = LocalStatusColors.current.warningText,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .background(LocalStatusColors.current.warningFill, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}
