package com.loypos.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LoyLightColorScheme = lightColorScheme(
    primary = LoyGreen,
    onPrimary = Color.White,
    primaryContainer = LoyGreenLight,
    onPrimaryContainer = LoyGreenDarkText,
    secondary = LoyGreenAccent,
    onSecondary = Color.White,
    secondaryContainer = LoyGreenLight,
    onSecondaryContainer = LoyGreenDarkText,
    background = LoyBackground,
    onBackground = LoyTextPrimary,
    surface = LoySurface,
    onSurface = LoyTextPrimary,
    surfaceVariant = LoySurfaceVariant,
    onSurfaceVariant = LoyTextSecondary,
    outline = LoyBorder,
    outlineVariant = Color(0xFFEEEEEE),
    error = LoyError,
    onError = Color.White,
    errorContainer = LoyErrorLight,
    onErrorContainer = Color(0xFFB71C1C),
)

private val LoyShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

@Composable
fun LoyPosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LoyLightColorScheme,
        typography = LoyPosTypography,
        shapes = LoyShapes,
        content = content,
    )
}
