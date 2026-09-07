package dev.qui.android.ui.torrents

internal enum class TorrentBackAction { CloseSwipe, ClearSelection, PreviousPage, System }

/** Modal windows own Back first; these are the remaining in-page priorities. */
internal fun torrentBackAction(swipeOpen: Boolean, selectionMode: Boolean, hasHistory: Boolean): TorrentBackAction =
    when {
        swipeOpen -> TorrentBackAction.CloseSwipe
        selectionMode -> TorrentBackAction.ClearSelection
        hasHistory -> TorrentBackAction.PreviousPage
        else -> TorrentBackAction.System
    }
