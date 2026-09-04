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
    fun `all columns sort in both directions`() {
        val files = listOf(
            file(4, "Delta", size = 40, progress = 0.4, priority = 6),
            file(2, "bravo", size = 10, progress = 0.8, priority = 1),
            file(1, "Alpha", size = 30, progress = 0.2, priority = 7),
            file(3, "charlie", size = 20, progress = 0.6, priority = 0),
        )
        val cases = listOf(
            Triple(
                "name ascending",
                FileSort(FileSortColumn.Name, SortDirection.Ascending),
                listOf(1, 2, 3, 4),
            ),
            Triple(
                "name descending",
                FileSort(FileSortColumn.Name, SortDirection.Descending),
                listOf(4, 3, 2, 1),
            ),
            Triple(
                "size ascending",
                FileSort(FileSortColumn.Size, SortDirection.Ascending),
                listOf(2, 3, 1, 4),
            ),
            Triple(
                "size descending",
                FileSort(FileSortColumn.Size, SortDirection.Descending),
                listOf(4, 1, 3, 2),
            ),
            Triple(
                "progress ascending",
                FileSort(FileSortColumn.Progress, SortDirection.Ascending),
                listOf(1, 4, 3, 2),
            ),
            Triple(
                "progress descending",
                FileSort(FileSortColumn.Progress, SortDirection.Descending),
                listOf(2, 3, 4, 1),
            ),
            Triple(
                "priority ascending",
                FileSort(FileSortColumn.Priority, SortDirection.Ascending),
                listOf(3, 2, 4, 1),
            ),
            Triple(
                "priority descending",
                FileSort(FileSortColumn.Priority, SortDirection.Descending),
                listOf(1, 4, 2, 3),
            ),
        )

        cases.forEach { (label, sort, expectedIndexes) ->
            assertEquals(label, expectedIndexes, sortTorrentFiles(files, sort).map { it.index })
        }
    }

    @Test
    fun `equal sizes use case-insensitive then original name tie breaks`() {
        val files = listOf(
            file(4, "beta", size = 20),
            file(2, "alpha", size = 20),
            file(5, "Tail", size = 30),
            file(3, "Alpha", size = 20),
            file(1, "Zed", size = 10),
        )

        assertEquals(
            listOf(1, 3, 2, 4, 5),
            sortTorrentFiles(
                files,
                FileSort(FileSortColumn.Size, SortDirection.Ascending),
            ).map { it.index },
        )
        assertEquals(
            listOf(5, 3, 2, 4, 1),
            sortTorrentFiles(
                files,
                FileSort(FileSortColumn.Size, SortDirection.Descending),
            ).map { it.index },
        )
    }

    @Test
    fun `index is the final tie break when primary and names match`() {
        val files = listOf(
            file(3, "Same", size = 20),
            file(1, "Same", size = 20),
            file(2, "Same", size = 20),
        )

        assertEquals(
            listOf(1, 2, 3),
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
    fun `same column toggles in both directions`() {
        val descending = toggleFileSort(FileSort(), FileSortColumn.Name)
        assertEquals(
            FileSort(FileSortColumn.Name, SortDirection.Descending),
            descending,
        )

        val ascending = toggleFileSort(descending, FileSortColumn.Name)
        assertEquals(
            FileSort(FileSortColumn.Name, SortDirection.Ascending),
            ascending,
        )
    }

    @Test
    fun `switching away and back resets the returned column to ascending`() {
        val sizeDescending = FileSort(FileSortColumn.Size, SortDirection.Descending)
        val progressAscending = toggleFileSort(sizeDescending, FileSortColumn.Progress)
        assertEquals(
            FileSort(FileSortColumn.Progress, SortDirection.Ascending),
            progressAscending,
        )

        val progressDescending = toggleFileSort(progressAscending, FileSortColumn.Progress)
        assertEquals(
            FileSort(FileSortColumn.Progress, SortDirection.Descending),
            progressDescending,
        )

        val sizeAscending = toggleFileSort(progressDescending, FileSortColumn.Size)
        assertEquals(
            FileSort(FileSortColumn.Size, SortDirection.Ascending),
            sizeAscending,
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
