/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Direct ports of qui's formatters (web/src/lib/utils.ts and web/src/lib/speedUnits.ts)
 * so the numbers on screen read identically to the web UI.
 */

package dev.qui.android.ui.format

import dev.qui.android.data.SpeedUnit
import java.util.Locale
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong

private val BYTE_SIZES = listOf("B", "KiB", "MiB", "GiB", "TiB", "PiB")

/** Matches qui's formatBytes: base-1024, up to 2 decimals, trailing zeros stripped. */
fun formatBytes(bytes: Long): String {
    if (bytes == 0L) return "0 B"
    if (bytes < 0) return "0 B"

    val k = 1024.0
    val index = minOf(floor(ln(bytes.toDouble()) / ln(k)).toInt(), BYTE_SIZES.lastIndex)
        .coerceAtLeast(0)
    val value = bytes / k.pow(index)
    return "${trimNumber(value, 2)} ${BYTE_SIZES[index]}"
}

private val SPEED_BYTE_UNITS = listOf("B/s", "KiB/s", "MiB/s", "GiB/s", "TiB/s")
private val SPEED_BYTE_UNITS_COMPACT = listOf("B", "KiB", "MiB", "GiB", "TiB")
private val SPEED_BIT_UNITS = listOf("bps", "Kbps", "Mbps", "Gbps", "Tbps")

/**
 * Matches qui's formatSpeedWithUnit. Bits use base-1000 (networking convention),
 * bytes use base-1024; precision drops as the value grows.
 */
fun formatSpeed(bytesPerSecond: Long, unit: SpeedUnit, compact: Boolean = false): String {
    if (bytesPerSecond <= 0) {
        if (compact) return "0"
        return if (unit == SpeedUnit.Bits) "0 bps" else "0 B/s"
    }

    return if (unit == SpeedUnit.Bits) {
        val bits = bytesPerSecond * 8.0
        val k = 1000.0
        val index = floor(ln(bits) / ln(k)).toInt().coerceIn(0, SPEED_BIT_UNITS.lastIndex)
        val value = bits / k.pow(index)
        val text = trimNumber(value, decimalsFor(value))
        if (text == "0") {
            if (compact) "0" else "0 bps"
        } else {
            "$text ${SPEED_BIT_UNITS[index]}"
        }
    } else {
        val k = 1024.0
        val units = if (compact) SPEED_BYTE_UNITS_COMPACT else SPEED_BYTE_UNITS
        val index = floor(ln(bytesPerSecond.toDouble()) / ln(k)).toInt()
            .coerceIn(0, units.lastIndex)
        val value = bytesPerSecond / k.pow(index)
        val text = trimNumber(value, decimalsFor(value))
        if (text == "0") {
            if (compact) "0" else "0 B/s"
        } else {
            if (compact) "$text${units[index]}" else "$text ${units[index]}"
        }
    }
}

private fun decimalsFor(value: Double): Int = when {
    value >= 100 -> 0
    value >= 10 -> 1
    else -> 2
}

/** JS `Number(x.toFixed(n))` semantics: round to n places, then drop trailing zeros. */
private fun trimNumber(value: Double, decimals: Int): String {
    val text = String.format(Locale.US, "%.${decimals}f", value)
    if (!text.contains('.')) return text
    return text.trimEnd('0').trimEnd('.')
}

/** qBittorrent uses 8640000 as its "infinite" ETA sentinel; qui renders it as ∞. */
const val ETA_INFINITE = 8_640_000L

/** Matches qui's mobile-card formatEta. */
fun formatEta(seconds: Long): String {
    if (seconds == ETA_INFINITE) return "∞"
    if (seconds < 0) return ""

    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60

    return when {
        hours > 24 -> "${hours / 24}d"
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

/** Matches qui's formatDuration: "2d 3h 4m 5s", omitting zero components. */
fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "0s"
    val days = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return buildList {
        if (days > 0) add("${days}d")
        if (hours > 0) add("${hours}h")
        if (minutes > 0) add("${minutes}m")
        if (secs > 0) add("${secs}s")
    }.joinToString(" ").ifEmpty { "0s" }
}

fun formatDurationCompact(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m"
    seconds < 86400 -> "${seconds / 3600}h"
    else -> "${seconds / 86400}d"
}

/** qui shows -1 (no limit reached yet) as ∞ and otherwise two decimals. */
fun formatRatio(ratio: Double): String =
    if (ratio < 0) "∞" else String.format(Locale.US, "%.2f", ratio)

/**
 * qui avoids rounding 99.x% up to a misleading 100%: between 99% and 100% it shows
 * one decimal, truncated rather than rounded.
 */
fun formatProgress(progress: Double): String {
    val pct = progress * 100
    return if (progress >= 0.99 && progress < 1.0) {
        String.format(Locale.US, "%.1f", floor(progress * 1000) / 10)
    } else {
        pct.roundToLong().toString()
    }
}

fun formatUnixTime(seconds: Long): String {
    if (seconds <= 0) return "—"
    val date = java.util.Date(seconds * 1000)
    return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(date)
}
