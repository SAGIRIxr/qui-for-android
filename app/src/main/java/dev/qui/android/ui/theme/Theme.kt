/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Maps qui's CSS custom properties onto Compose. Material3's ColorScheme has no slot
 * for several tokens qui relies on (card, muted, chart-1..5, sidebar), so those ride
 * alongside it in a CompositionLocal and are read through [QuiTheme].
 */

package dev.qui.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.qui.android.data.ThemeMode

val LocalQuiPalette = staticCompositionLocalOf { QuiThemes.first().light }

object QuiTheme {
    val palette: QuiPalette
        @Composable @ReadOnlyComposable get() = LocalQuiPalette.current

    /**
     * qui colours ratios on a five-step scale (web/src/lib/utils.ts getRatioColor),
     * worst to best mapped onto chart-5 .. chart-1.
     */
    @Composable
    @ReadOnlyComposable
    fun ratioColor(ratio: Double): Color {
        val p = palette
        return when {
            ratio < 0 -> p.mutedForeground
            ratio < 0.5 -> p.chart5
            ratio < 1.0 -> p.chart4
            ratio < 2.0 -> p.chart3
            ratio < 5.0 -> p.chart2
            else -> p.chart1
        }
    }

    /** Download accent, matching qui's `text-chart-2` on speeds. */
    val downloadColor: Color
        @Composable @ReadOnlyComposable get() = palette.chart2

    /** Upload accent, matching qui's `text-chart-3`. */
    val uploadColor: Color
        @Composable @ReadOnlyComposable get() = palette.chart3
}

private fun QuiPalette.toColorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = primaryForeground,
        primaryContainer = primary,
        onPrimaryContainer = primaryForeground,
        secondary = secondary,
        onSecondary = secondaryForeground,
        secondaryContainer = secondary,
        onSecondaryContainer = secondaryForeground,
        tertiary = accent,
        onTertiary = accentForeground,
        background = background,
        onBackground = foreground,
        surface = background,
        onSurface = foreground,
        surfaceVariant = muted,
        onSurfaceVariant = mutedForeground,
        surfaceContainer = card,
        surfaceContainerHigh = popover,
        surfaceContainerHighest = accent,
        surfaceContainerLow = card,
        surfaceContainerLowest = background,
        error = destructive,
        onError = destructiveForeground,
        errorContainer = destructive,
        onErrorContainer = destructiveForeground,
        outline = border,
        outlineVariant = input,
        inverseSurface = foreground,
        inverseOnSurface = background,
        scrim = Color.Black,
    )
}

/**
 * qui's `--radius` is 0.625rem (10px) in the default theme; card corners use it and
 * smaller controls step down from it, the same relationship shadcn/ui uses.
 */
private val QuiShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(14.dp),
)

private val QuiTypography = Typography().let { base ->
    base.copy(
        // qui's mobile cards run small: text-sm for names, text-xs for metadata.
        bodyLarge = base.bodyLarge.copy(fontFamily = FontFamily.SansSerif, fontSize = 15.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = FontFamily.SansSerif, fontSize = 13.sp),
        bodySmall = base.bodySmall.copy(fontFamily = FontFamily.SansSerif, fontSize = 11.sp),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp),
        labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 10.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp),
    )
}

@Composable
fun QuiAppTheme(
    themeId: String = "default",
    variationId: String? = null,
    themeMode: ThemeMode = ThemeMode.Auto,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.Auto -> isSystemInDarkTheme()
    }

    val spec = quiThemeById(themeId)
    val variation = variationId?.let { id -> spec.variations.firstOrNull { it.id == id } }
    val palette = when {
        variation != null && dark -> variation.dark
        variation != null -> variation.light
        dark -> spec.dark
        else -> spec.light
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> palette.toColorScheme(dark)
    }

    CompositionLocalProvider(LocalQuiPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = QuiTypography,
            shapes = QuiShapes,
            content = content,
        )
    }
}
