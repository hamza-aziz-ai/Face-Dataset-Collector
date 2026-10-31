package com.example.facedatasetcollector.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Surface,
    secondary = Accent,
    onSecondary = Surface,
    tertiary = Accent,
    background = Ground,
    onBackground = Ink,
    surface = Surface,
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

/** WHY one fixed light scheme: staff must see the same screens and contrast on every device, so dynamic color and dark mode are off. */
@Composable
fun FaceDatasetCollectorTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
