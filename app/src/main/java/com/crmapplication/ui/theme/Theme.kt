package com.crmapplication.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
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

/** Duration of the light↔dark cross-fade. Long enough to read as a smooth transition, short
 *  enough not to feel sluggish when toggling the switch. */
private const val THEME_ANIM_MS = 400

/**
 * Cross-fades every color-scheme field toward [target]. Material 3 swaps [ColorScheme] instantly
 * (no built-in tween), so without this the whole app snaps between cream and navy in one frame.
 * Animating each role means surfaces, text (`onSurface`/`onBackground`) and icon tints glide
 * together. The brand orange (primary/secondary) is identical in both schemes, so it stays put.
 */
@Composable
private fun animatedColorScheme(target: ColorScheme): ColorScheme {
    val spec = tween<Color>(THEME_ANIM_MS)
    @Composable fun anim(color: Color) = animateColorAsState(color, spec, label = "themeColor").value
    return target.copy(
        primary              = anim(target.primary),
        onPrimary            = anim(target.onPrimary),
        primaryContainer     = anim(target.primaryContainer),
        onPrimaryContainer   = anim(target.onPrimaryContainer),
        secondary            = anim(target.secondary),
        onSecondary          = anim(target.onSecondary),
        secondaryContainer   = anim(target.secondaryContainer),
        onSecondaryContainer = anim(target.onSecondaryContainer),
        tertiary             = anim(target.tertiary),
        background           = anim(target.background),
        onBackground         = anim(target.onBackground),
        surface              = anim(target.surface),
        onSurface            = anim(target.onSurface),
        surfaceVariant       = anim(target.surfaceVariant),
        onSurfaceVariant     = anim(target.onSurfaceVariant),
        outline              = anim(target.outline),
        outlineVariant       = anim(target.outlineVariant),
        error                = anim(target.error),
        onError              = anim(target.onError),
    )
}

@Composable
fun CRMApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val target = if (darkTheme) CrmDarkColorScheme else CrmLightColorScheme
    MaterialTheme(
        colorScheme = animatedColorScheme(target),
        typography  = Typography,
        shapes      = CrmShapes,
        content     = content,
    )
}
