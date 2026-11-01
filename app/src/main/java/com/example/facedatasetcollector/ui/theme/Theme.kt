package com.example.facedatasetcollector.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Surface,
    secondary = Accent,
    onSecondary = Surface,
    tertiary = Accent,
    background = Ground,
    onBackground = Ink,
    surface = Surface,
    // Dialogs use this slot. Unset, it falls back to Material's default lavender.
    surfaceContainerHigh = Surface,
    onSurface = Ink,
    onSurfaceVariant = InkMuted,
    outline = LineStrong,
    outlineVariant = Line,
    error = ErrorRed,
    onError = Surface

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkAccent,
    onPrimary = OnDarkAccent,
    secondary = DarkAccent,
    onSecondary = OnDarkAccent,
    tertiary = DarkAccent,
    background = Night,
    onBackground = NightText,
    surface = NightPanel,
    surfaceContainerHigh = NightPanel,
    onSurface = NightText,
    onSurfaceVariant = NightMuted,
    outline = DarkOutline,
    outlineVariant = NightTrack,
    error = DarkError,
    onError = OnDarkError
)

/** Colors Material 3 has no slot for. */
data class StatusColors(
    val warningFill: Color,
    val warningText: Color,
    val successFill: Color,
    val successText: Color,
)

private val LightStatus = StatusColors(WarningFill, WarningText, SuccessFill, SuccessText)
private val DarkStatus = StatusColors(DarkWarningFill, DarkWarningText, DarkSuccessFill, DarkSuccessText)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

/** Follows the device setting. Dynamic color stays off, so staff see the same designed contrast on every device. */
@Composable
fun FaceDatasetCollectorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            content = content
        )
    }
}
