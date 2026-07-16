package com.crmapplication.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val CrmLightColorScheme = lightColorScheme(
    primary              = Orange500,
    onPrimary            = Color(0xFFFFFFFF),
    primaryContainer     = Orange300,
    onPrimaryContainer   = Orange700,
    secondary            = Orange400,
    onSecondary          = Color(0xFFFFFFFF),
    secondaryContainer   = Color(0xFFFFEDD5),
    onSecondaryContainer = Orange700,
    tertiary             = Blue500,
    background           = Cream,
    onBackground         = Slate900,
    surface              = CreamSurface,
    onSurface            = Slate900,
    surfaceVariant       = CreamSurfaceVar,
    onSurfaceVariant     = Slate500,
    outline              = CreamOutline,
    outlineVariant       = CreamOutline,
    error                = Red600,
    onError              = Color(0xFFFFFFFF),
)

private val CrmDarkColorScheme = darkColorScheme(
    primary              = Orange500,
    onPrimary            = Color(0xFF1A0E00),
    primaryContainer     = Orange700,
    onPrimaryContainer   = Orange300,
    secondary            = Orange400,
    onSecondary          = Color(0xFF1A0E00),
    secondaryContainer   = Orange700,
    onSecondaryContainer = Orange300,
    tertiary             = Blue400,
    background           = NavyBg,
    onBackground         = NavyOnSurface,
    surface              = NavySurface,
    onSurface            = NavyOnSurface,
    surfaceVariant       = NavySurfaceVar,
    onSurfaceVariant     = NavyOnSurfaceVar,
    outline              = NavyOutline,
    outlineVariant       = NavyOutline,
    error                = Red500,
    onError              = Color(0xFF1A0000),
)

private val CrmShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(6.dp),
    medium     = RoundedCornerShape(8.dp),
    large      = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun CRMApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) CrmDarkColorScheme else CrmLightColorScheme,
        typography  = Typography,
        shapes      = CrmShapes,
        content     = content,
    )
}
