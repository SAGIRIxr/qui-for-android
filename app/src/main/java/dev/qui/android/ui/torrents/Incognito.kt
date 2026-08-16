/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Port of qui's incognito mode (web/src/lib/incognito.ts). Torrent names, categories,
 * tags, trackers and paths are swapped for a deterministic Linux-distro vocabulary so
 * screenshots and over-the-shoulder glances reveal nothing. The hash arithmetic matches
 * the web implementation, so the same torrent shows the same alias in both clients.
 */

package dev.qui.android.ui.torrents

import kotlin.math.min

private val LINUX_ISO_NAMES = listOf(
    "ubuntu-24.04.1-desktop-amd64.iso",
    "ubuntu-24.10-desktop-amd64.iso",
    "ubuntu-22.04.4-server-amd64.iso",
    "debian-12.7.0-amd64-DVD-1.iso",
    "debian-13-trixie-alpha-netinst.iso",
    "Fedora-Workstation-Live-x86_64-41.iso",
    "Fedora-Server-dvd-x86_64-42.iso",
    "archlinux-2024.12.01-x86_64.iso",
    "archlinux-2024.11.01-x86_64.iso",
    "Pop!_OS-24.04-amd64-intel.iso",
    "linuxmint-22-cinnamon-64bit.iso",
    "openSUSE-Tumbleweed-DVD-x86_64-Current.iso",
    "openSUSE-Leap-15.6-DVD-x86_64.iso",
    "manjaro-kde-24.0-240513-linux66.iso",
    "EndeavourOS-Galileo-11-2024.iso",
    "elementary-os-7.1-stable.20231129rc.iso",
    "zorin-os-17.1-core-64bit.iso",
    "MX-23.3_x64.iso",
    "kali-linux-2024.3-installer-amd64.iso",
    "parrot-security-6.0_amd64.iso",
    "rocky-9.4-x86_64-dvd.iso",
    "almalinux-9.4-x86_64-dvd.iso",
    "centos-stream-9-latest-x86_64-dvd1.iso",
    "garuda-dr460nized-linux-zen-240131.iso",
    "artix-base-openrc-20241201-x86_64.iso",
    "void-live-x86_64-20240314-xfce.iso",
    "solus-4.5-budgie.iso",
    "alpine-standard-3.19.1-x86_64.iso",
    "slackware64-15.0-install-dvd.iso",
    "gentoo-install-amd64-minimal-20241201.iso",
    "nixos-24.05-plasma6-x86_64.iso",
    "endeavouros-2024.09.22-x86_64.iso",
    "kubuntu-24.04.1-desktop-amd64.iso",
    "xubuntu-24.04-desktop-amd64.iso",
    "lubuntu-24.04-desktop-amd64.iso",
    "ubuntu-mate-24.04-desktop-amd64.iso",
    "ubuntu-budgie-24.04-desktop-amd64.iso",
    "deepin-desktop-community-23.0-amd64.iso",
    "kde-neon-user-20241205-1344.iso",
    "peppermint-2024-02-02-amd64.iso",
    "tails-amd64-6.8.1.iso",
    "qubes-r4.2.3-x86_64.iso",
    "proxmox-ve_8.2-2.iso",
    "truenas-scale-24.04.2.iso",
    "opnsense-24.7-dvd-amd64.iso",
    "pfsense-ce-2.7.2-amd64.iso",
)

private val LINUX_CATEGORIES = listOf(
    "distributions",
    "documentation",
    "source-code",
    "live-usb",
    "server-editions",
    "desktop-environments",
    "arm-builds",
)

private val LINUX_TAGS = listOf(
    "stable", "lts", "bleeding-edge", "minimal", "gnome", "kde", "xfce", "server",
    "desktop", "arm64", "x86_64", "enterprise", "community", "official", "beta",
    "rc", "nightly", "security-focused", "lightweight", "rolling-release",
)

private val LINUX_TRACKERS = listOf(
    "releases.ubuntu.com",
    "cdimage.debian.org",
    "download.fedoraproject.org",
    "mirror.archlinux.org",
    "distro.ibiblio.org",
    "ftp.osuosl.org",
    "mirrors.kernel.org",
    "linuxtracker.org",
    "academic-torrents.com",
    "fosshost.org",
)

private val LINUX_SAVE_PATHS = listOf(
    "/home/downloads/distributions",
    "/home/downloads/docs",
    "/home/downloads/source",
    "/home/downloads/live",
    "/home/downloads/server",
    "/home/downloads/desktop",
    "/home/downloads/arm",
)

/**
 * JS `charCodeAt` sums over UTF-16 units. Hashes are ASCII hex, so iterating over Kotlin
 * `Char` codes produces identical sums.
 */
private inline fun weightedSum(hash: String, limit: Int = hash.length, weight: (Int) -> Int): Int {
    var sum = 0
    for (i in 0 until min(limit, hash.length)) {
        sum += hash[i].code * weight(i)
    }
    return sum
}

private fun Int.indexIn(size: Int): Int = if (size == 0) 0 else ((this % size) + size) % size

fun incognitoName(hash: String): String {
    if (hash.isEmpty()) return LINUX_ISO_NAMES.first()
    val sum = weightedSum(hash) { 1 }
    return LINUX_ISO_NAMES[sum.indexIn(LINUX_ISO_NAMES.size)]
}

fun incognitoFileName(hash: String, index: Int): String {
    if (hash.isEmpty()) return LINUX_ISO_NAMES[index.indexIn(LINUX_ISO_NAMES.size)]
    val sum = weightedSum(hash) { it + 3 }
    val offset = sum.indexIn(LINUX_ISO_NAMES.size)
    return LINUX_ISO_NAMES[(offset + index).indexIn(LINUX_ISO_NAMES.size)]
}

/** Matches qui: a 30% slice of hashes deliberately map to "no category". */
fun incognitoCategory(hash: String): String {
    val sum = weightedSum(hash, limit = 10) { it + 1 }
    if (sum.indexIn(10) < 3) return ""
    return LINUX_CATEGORIES[sum.indexIn(LINUX_CATEGORIES.size)]
}

/** Matches qui: 20% map to no tags, otherwise one to three distinct tags. */
fun incognitoTags(hash: String): List<String> {
    val sum = weightedSum(hash, limit = 15) { it + 2 }
    if (sum.indexIn(10) < 2) return emptyList()

    val count = sum.indexIn(3) + 1
    val tags = LinkedHashSet<String>()
    for (i in 0 until count) {
        tags.add(LINUX_TAGS[(sum + i * 7).indexIn(LINUX_TAGS.size)])
    }
    return tags.toList()
}

fun incognitoTracker(hash: String): String {
    if (hash.isEmpty()) return LINUX_TRACKERS.first()
    val sum = weightedSum(hash) { it + 1 }
    return LINUX_TRACKERS[sum.indexIn(LINUX_TRACKERS.size)]
}

fun incognitoSavePath(hash: String): String {
    val sum = weightedSum(hash, limit = 8) { it + 3 }
    return LINUX_SAVE_PATHS[sum.indexIn(LINUX_SAVE_PATHS.size)]
}

/**
 * Ratios are disguised too, otherwise a distinctive value would still identify a
 * torrent. Derived from the hash so it stays stable between refreshes.
 */
fun incognitoRatio(hash: String): Double {
    val sum = weightedSum(hash, limit = 12) { it + 1 }
    return (sum.indexIn(500)) / 100.0
}
