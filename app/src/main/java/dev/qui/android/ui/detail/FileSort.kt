/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package dev.qui.android.ui.detail

import dev.qui.android.data.model.TorrentFile
import java.util.Locale

enum class FileSortColumn { Name, Size, Progress, Priority }

enum class SortDirection { Ascending, Descending }

data class FileSort(
    val column: FileSortColumn = FileSortColumn.Name,
    val direction: SortDirection = SortDirection.Ascending,
)

fun toggleFileSort(current: FileSort, column: FileSortColumn): FileSort {
    if (current.column == column) {
        val direction = when (current.direction) {
            SortDirection.Ascending -> SortDirection.Descending
            SortDirection.Descending -> SortDirection.Ascending
        }
        return current.copy(direction = direction)
    }

    return FileSort(column = column, direction = SortDirection.Ascending)
}

fun sortTorrentFiles(files: List<TorrentFile>, sort: FileSort): List<TorrentFile> {
    val primary = when (sort.column) {
        FileSortColumn.Name -> compareBy<TorrentFile> { it.name.lowercase(Locale.ROOT) }
        FileSortColumn.Size -> compareBy(TorrentFile::size)
        FileSortColumn.Progress -> compareBy(TorrentFile::progress)
        FileSortColumn.Priority -> compareBy(TorrentFile::priority)
    }
    val directed = if (sort.direction == SortDirection.Ascending) {
        primary
    } else {
        primary.reversed()
    }
    val deterministicTieBreak = compareBy<TorrentFile>(
        { it.name.lowercase(Locale.ROOT) },
        TorrentFile::name,
        TorrentFile::index,
    )
    return files.sortedWith(directed.then(deterministicTieBreak))
}
