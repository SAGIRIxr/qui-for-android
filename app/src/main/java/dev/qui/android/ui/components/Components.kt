/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Compose equivalents of the shadcn/ui primitives qui leans on: Badge, Progress and
 * the logo mark.
 */

package dev.qui.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import dev.qui.android.R
import dev.qui.android.ui.theme.QuiTheme

/**
 * qui's badge variants. `Outline` additionally carries an explicit colour when the
 * tracker-health states override it (yellow / orange / destructive).
 */
enum class BadgeVariant { Default, Secondary, Destructive, Outline }

@Composable
fun QuiBadge(
    text: String,
    variant: BadgeVariant = BadgeVariant.Default,
    modifier: Modifier = Modifier,
    overrideColor: Color? = null,
    compact: Boolean = false,
) {
    val palette = QuiTheme.palette

    val background: Color
    val foreground: Color
    val borderColor: Color

    if (overrideColor != null) {
        background = overrideColor.copy(alpha = 0.10f)
        foreground = overrideColor
        borderColor = overrideColor.copy(alpha = 0.40f)
    } else {
        when (variant) {
            BadgeVariant.Default -> {
                background = palette.primary
                foreground = palette.primaryForeground
                borderColor = Color.Transparent
            }
            BadgeVariant.Secondary -> {
                background = palette.secondary
                foreground = palette.secondaryForeground
                borderColor = Color.Transparent
            }
            BadgeVariant.Destructive -> {
                background = palette.destructive
                foreground = palette.destructiveForeground
                borderColor = Color.Transparent
            }
            BadgeVariant.Outline -> {
                background = Color.Transparent
                foreground = palette.foreground
                borderColor = palette.border
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .then(
                if (borderColor != Color.Transparent) {
                    Modifier.border(1.dp, borderColor, RoundedCornerShape(6.dp))
                } else {
                    Modifier
                }
            )
            .padding(
                horizontal = if (compact) 4.dp else 6.dp,
                vertical = if (compact) 1.dp else 2.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = if (compact) {
                MaterialTheme.typography.labelSmall
            } else {
                MaterialTheme.typography.labelMedium
            },
        )
    }
}

/**
 * Flat progress bar matching qui's `<Progress className="h-2" />` — a rounded track
 * with a primary-coloured fill and no animation.
 */
@Composable
fun QuiProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 8.dp,
    color: Color? = null,
) {
    val palette = QuiTheme.palette
    val shape = RoundedCornerShape(percent = 50)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(palette.secondary),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .clip(shape)
                .background(color ?: palette.primary),
        )
    }
}

/**
 * Tinting preserves each path's alpha, so the logo's translucent body fill survives
 * while the mark takes on the active theme's primary colour.
 */
@Composable
fun QuiLogo(modifier: Modifier = Modifier, tint: Color? = null) {
    Image(
        painter = painterResource(R.drawable.ic_qui_logo),
        contentDescription = "qui",
        modifier = modifier.size(24.dp),
        colorFilter = ColorFilter.tint(tint ?: QuiTheme.palette.primary),
    )
}

/** Small circular status dot; qui uses green for connected, red for disconnected. */
@Composable
fun StatusDot(connected: Boolean, modifier: Modifier = Modifier) {
    val palette = QuiTheme.palette
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(if (connected) palette.chart3 else palette.destructive),
    )
}
