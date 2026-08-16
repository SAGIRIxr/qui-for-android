/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Compose equivalents of the shadcn/ui primitives qui leans on: Badge, Progress and
 * the logo mark.
 */

package dev.qui.android.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.qui.android.R
import dev.qui.android.data.iconFor
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
        contentDescription = stringResource(R.string.app_name),
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

/**
 * Favicons fetched from qui's tracker-icon cache, keyed by host. Empty until the
 * first fetch lands, which is why TrackerIcon always has a letter fallback.
 */
val LocalTrackerIcons = staticCompositionLocalOf<Map<String, ImageBitmap>> { emptyMap() }

/**
 * qui's TrackerIcon: the cached favicon when there is one, otherwise the host's
 * first letter in a muted square, so the row keeps its alignment either way.
 */
@Composable
fun TrackerIcon(
    host: String,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp,
) {
    if (host.isEmpty()) return
    val palette = QuiTheme.palette
    val icon = LocalTrackerIcons.current.iconFor(host)
    val shape = RoundedCornerShape(3.dp)

    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(shape),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(palette.muted),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = host.first().uppercase(),
                color = palette.mutedForeground,
                fontSize = (size.value * 0.62f).sp,
                lineHeight = (size.value * 0.62f).sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * A labelled figure, used across the dashboard cards and the list header. Matches the
 * "big number over a muted caption" blocks in qui's InstanceCard.
 */
@Composable
fun Metric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    val palette = QuiTheme.palette
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color ?: palette.foreground,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.mutedForeground,
            maxLines = 1,
        )
    }
}

/** A label/value line, the layout qui uses for the smaller stats under the counts. */
@Composable
fun StatLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    valueColor: Color? = null,
) {
    val palette = QuiTheme.palette
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = valueColor ?: palette.mutedForeground,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = palette.mutedForeground,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = valueColor ?: palette.foreground,
            maxLines = 1,
        )
    }
}

/**
 * The card surface qui uses everywhere: `bg-card` with a hairline border and a
 * generous radius. Material's own Card draws a tonal elevation that fights the
 * ported palette, so this keeps the two UIs looking the same.
 */
@Composable
fun QuiCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = QuiTheme.palette
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.card)
            .border(1.dp, palette.border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        content = content,
    )
}
