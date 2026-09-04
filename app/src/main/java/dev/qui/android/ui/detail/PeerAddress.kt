/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package dev.qui.android.ui.detail

import dev.qui.android.data.model.TorrentPeer

fun formatPeerAddress(peer: TorrentPeer): String {
    peer.key?.takeIf(String::isNotBlank)?.let { return it }

    val ip = peer.ip?.trim().orEmpty()
    if (ip.isEmpty()) return "-"

    val port = peer.port?.takeIf { it in 1..65535 } ?: return ip
    val host = if (':' in ip && !(ip.startsWith('[') && ip.endsWith(']'))) {
        "[$ip]"
    } else {
        ip
    }
    return "$host:$port"
}
