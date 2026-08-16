/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Expected values were computed from qui's own formatters so a divergence between the
 * Kotlin port and the web UI shows up here rather than on screen.
 */

package dev.qui.android

import dev.qui.android.data.SpeedUnit
import dev.qui.android.ui.format.ETA_INFINITE
import dev.qui.android.ui.format.formatBytes
import dev.qui.android.ui.format.formatDuration
import dev.qui.android.ui.format.formatEta
import dev.qui.android.ui.format.formatProgress
import dev.qui.android.ui.format.formatRatio
import dev.qui.android.ui.format.formatSpeed
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun `formatBytes matches qui base-1024 output`() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("512 B", formatBytes(512))
        assertEquals("1 KiB", formatBytes(1024))
        assertEquals("1.5 KiB", formatBytes(1536))
        assertEquals("1 MiB", formatBytes(1024L * 1024))
        assertEquals("1 GiB", formatBytes(1024L * 1024 * 1024))
        // Trailing zeros are stripped, matching JS Number(x.toFixed(2)).
        assertEquals("2.25 GiB", formatBytes((2.25 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun `formatSpeed in bytes drops precision as the value grows`() {
        assertEquals("0 B/s", formatSpeed(0, SpeedUnit.Bytes))
        // < 10 keeps two decimals, >= 10 one, >= 100 none.
        assertEquals("1.5 KiB/s", formatSpeed(1536, SpeedUnit.Bytes))
        assertEquals("10.5 KiB/s", formatSpeed(10752, SpeedUnit.Bytes))
        assertEquals("500 KiB/s", formatSpeed(512000, SpeedUnit.Bytes))
    }

    @Test
    fun `formatSpeed in bits uses base-1000 networking units`() {
        assertEquals("0 bps", formatSpeed(0, SpeedUnit.Bits))
        // 1000 B/s = 8000 bps = 8 Kbps
        assertEquals("8 Kbps", formatSpeed(1000, SpeedUnit.Bits))
        assertEquals("8 Mbps", formatSpeed(1_000_000, SpeedUnit.Bits))
    }

    @Test
    fun `formatSpeed compact omits the per-second suffix and the space`() {
        assertEquals("0", formatSpeed(0, SpeedUnit.Bytes, compact = true))
        assertEquals("1.5KiB", formatSpeed(1536, SpeedUnit.Bytes, compact = true))
    }

    @Test
    fun `formatEta uses qBittorrent's infinity sentinel`() {
        assertEquals("∞", formatEta(ETA_INFINITE))
        assertEquals("", formatEta(-1))
        assertEquals("5m", formatEta(300))
        assertEquals("2h 5m", formatEta(7500))
        assertEquals("2d", formatEta(180_000))
    }

    @Test
    fun `formatProgress never rounds a partial download up to 100`() {
        assertEquals("0", formatProgress(0.0))
        assertEquals("50", formatProgress(0.5))
        assertEquals("100", formatProgress(1.0))
        // 99.95% would round to 100 and read as complete; qui truncates to one decimal.
        assertEquals("99.9", formatProgress(0.9995))
        assertEquals("99.0", formatProgress(0.99))
    }

    @Test
    fun `formatRatio renders the no-limit sentinel as infinity`() {
        assertEquals("∞", formatRatio(-1.0))
        assertEquals("0.00", formatRatio(0.0))
        assertEquals("1.23", formatRatio(1.234))
    }

    @Test
    fun `formatDuration omits zero components`() {
        assertEquals("0s", formatDuration(0))
        assertEquals("45s", formatDuration(45))
        assertEquals("1m 30s", formatDuration(90))
        assertEquals("1d 1h", formatDuration(90_000))
    }
}
