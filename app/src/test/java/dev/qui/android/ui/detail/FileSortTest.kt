package dev.qui.android.ui.detail

import dev.qui.android.data.model.TorrentFile
import org.junit.Assert.assertEquals
import org.junit.Test

class FileSortTest {

    @Test
    fun `default order is case-insensitive name ascending with deterministic ties`() {
        val files = listOf(
            file(2, "zeta.mkv"),
            file(3, "alpha.mkv"),
            file(1, "Alpha.mkv"),
        )

        assertEquals(listOf(1, 3, 2), sortTorrentFiles(files, FileSort()).map { it.index })
    }

    @Test
    fun `size supports both directions and name tie breaking`() {
        val files = listOf(
            file(2, "Beta", size = 20),
            file(1, "Alpha", size = 20),
            file(3, "Zed", size = 10),
        )

        assertEquals(
            listOf(3, 1, 2),
            sortTorrentFiles(
                files,
                FileSort(FileSortColumn.Size, SortDirection.Ascending),
            ).map { it.index },
        )
        assertEquals(
            listOf(1, 2, 3),
            sortTorrentFiles(
                files,
                FileSort(FileSortColumn.Size, SortDirection.Descending),
            ).map { it.index },
        )
    }

    @Test
    fun `progress and priority use numeric values`() {
        val files = listOf(
            file(1, "One", progress = 0.5, priority = 1),
            file(2, "Two", progress = 0.9, priority = 0),
            file(3, "Three", progress = 0.1, priority = 7),
        )

        assertEquals(
            listOf(2, 1, 3),
            sortTorrentFiles(
                files,
                FileSort(FileSortColumn.Progress, SortDirection.Descending),
            ).map { it.index },
        )
        assertEquals(
            listOf(3, 1, 2),
            sortTorrentFiles(
                files,
                FileSort(FileSortColumn.Priority, SortDirection.Descending),
            ).map { it.index },
        )
    }

    @Test
    fun `same column toggles and a new column gets its conventional default`() {
        assertEquals(
            FileSort(FileSortColumn.Name, SortDirection.Descending),
            toggleFileSort(FileSort(), FileSortColumn.Name),
        )
        assertEquals(
            FileSort(FileSortColumn.Size, SortDirection.Ascending),
            toggleFileSort(FileSort(), FileSortColumn.Size),
        )
        assertEquals(
            FileSort(FileSortColumn.Size, SortDirection.Ascending),
            toggleFileSort(
                FileSort(FileSortColumn.Size, SortDirection.Descending),
                FileSortColumn.Size,
            ),
        )
        assertEquals(
            FileSort(FileSortColumn.Name, SortDirection.Ascending),
            toggleFileSort(
                FileSort(FileSortColumn.Priority, SortDirection.Descending),
                FileSortColumn.Name,
            ),
        )
    }

    @Test
    fun `file refresh preserves the selected sort`() {
        val selected = FileSort(FileSortColumn.Progress, SortDirection.Descending)
        val refreshed = DetailUiState(
            files = listOf(file(1, "Old", progress = 0.1)),
            fileSort = selected,
        ).copy(
            files = listOf(
                file(2, "Low", progress = 0.2),
                file(3, "High", progress = 0.8),
            )
        )

        assertEquals(selected, refreshed.fileSort)
        assertEquals(listOf(3, 2), refreshed.sortedFiles.map { it.index })
    }

    private fun file(
        index: Int,
        name: String,
        size: Long = 0,
        progress: Double = 0.0,
        priority: Int = 0,
    ) = TorrentFile(
        index = index,
        name = name,
        size = size,
        progress = progress,
        priority = priority,
    )
}
