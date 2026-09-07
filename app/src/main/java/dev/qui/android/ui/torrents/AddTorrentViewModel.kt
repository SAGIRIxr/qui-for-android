/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package dev.qui.android.ui.torrents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qui.android.data.QuiRepository
import dev.qui.android.ui.addintent.AddIntent
import dev.qui.android.ui.addintent.TorrentPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddTorrentUiState(
    val urls: String = "",
    val files: List<TorrentPayload> = emptyList(),
    val category: String = "",
    val tags: Set<String> = emptySet(),
    val savePath: String = "",
    val startPaused: Boolean = false,
    val skipHashCheck: Boolean = false,
    val sequential: Boolean = false,
    val firstLastPiece: Boolean = false,
    val isBusy: Boolean = false,
    val error: String? = null,
    val addedCount: Int = 0,
) {
    val canSubmit: Boolean
        get() = urls.isNotBlank() || files.isNotEmpty()
}

@HiltViewModel
class AddTorrentViewModel @Inject constructor(
    private val repository: QuiRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AddTorrentUiState())
    val state: StateFlow<AddTorrentUiState> = _state.asStateFlow()

    private fun edit(transform: (AddTorrentUiState) -> AddTorrentUiState) = _state.update {
        if (it.isBusy) it else transform(it)
    }

    /** Fills the form from a magnet link or .torrent handed in by another app. */
    fun applyPrefill(intent: AddIntent) = edit { current ->
        val mergedUrls = (current.urls.lines() + intent.urls)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString("\n")

        current.copy(
            urls = mergedUrls,
            files = (current.files + intent.files).distinct(),
        )
    }

    fun setUrls(value: String) = edit { it.copy(urls = value, error = null) }

    fun addFiles(files: List<TorrentPayload>) = edit {
        it.copy(files = (it.files + files).distinct(), error = null)
    }

    fun removeFile(file: TorrentPayload) = edit {
        it.copy(files = it.files - file)
    }

    fun setCategory(value: String) = edit { it.copy(category = value) }

    fun toggleTag(tag: String) = edit {
        it.copy(tags = if (tag in it.tags) it.tags - tag else it.tags + tag)
    }

    fun setSavePath(value: String) = edit { it.copy(savePath = value) }
    fun setStartPaused(value: Boolean) = edit { it.copy(startPaused = value) }
    fun setSkipHashCheck(value: Boolean) = edit { it.copy(skipHashCheck = value) }
    fun setSequential(value: Boolean) = edit { it.copy(sequential = value) }
    fun setFirstLastPiece(value: Boolean) = edit { it.copy(firstLastPiece = value) }

    fun submit(instanceId: Int, onAdded: () -> Unit, onPartialAdded: () -> Unit = {}) {
        val current = _state.value
        if (!current.canSubmit || current.isBusy) return

        _state.update { it.copy(isBusy = true, error = null) }

        viewModelScope.launch {
            repository.addTorrent(
                instanceId = instanceId,
                urls = current.urls.lines().map { it.trim() }.filter { it.isNotEmpty() },
                files = current.files.map { it.filename to it.bytes },
                category = current.category.takeIf { it.isNotBlank() },
                tags = current.tags.toList(),
                startPaused = current.startPaused,
                savePath = current.savePath.takeIf { it.isNotBlank() },
                autoTmm = null,
                skipHashCheck = current.skipHashCheck,
                sequentialDownload = current.sequential,
                firstLastPiecePrio = current.firstLastPiece,
                contentLayout = null,
                rename = null,
                limitUploadSpeed = null,
                limitDownloadSpeed = null,
                limitRatio = null,
                limitSeedTime = null,
            )
                .onSuccess { response ->
                    // qui reports per-URL and per-file failures inside a 200 response,
                    // so a successful call can still mean nothing was added.
                    val failureDetail = buildList {
                        response.failedURLs?.forEach { add("${it.url}: ${it.error}") }
                        response.failedFiles?.forEach { add("${it.filename}: ${it.error}") }
                    }

                    if (failureDetail.isNotEmpty() || response.failed > 0 || response.added == 0) {
                        val failedUrls = response.failedURLs.orEmpty().map { it.url }.toSet()
                        val failedFiles = response.failedFiles.orEmpty().map { it.filename }.toSet()
                        // Without complete item-level results, do not silently discard a draft.
                        val identified = failureDetail.isNotEmpty() && response.failed <= failureDetail.size
                        _state.update {
                            it.copy(
                                isBusy = false,
                                urls = if (identified) current.urls.lines().filter { url -> url.trim() in failedUrls }.joinToString("\n") else current.urls,
                                files = if (identified) current.files.filter { file -> file.filename in failedFiles } else current.files,
                                addedCount = current.addedCount + response.added,
                                error = failureDetail.joinToString("\n"),
                            )
                        }
                        if (response.added > 0) onPartialAdded()
                    } else {
                        _state.value = AddTorrentUiState()
                        onAdded()
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isBusy = false, error = error.message ?: error.toString()) }
                }
        }
    }
}
