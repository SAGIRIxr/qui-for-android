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

    /** Fills the form from a magnet link or .torrent handed in by another app. */
    fun applyPrefill(intent: AddIntent) = _state.update { current ->
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

    fun setUrls(value: String) = _state.update { it.copy(urls = value, error = null) }

    fun addFiles(files: List<TorrentPayload>) = _state.update {
        it.copy(files = (it.files + files).distinct(), error = null)
    }

    fun removeFile(file: TorrentPayload) = _state.update {
        it.copy(files = it.files - file)
    }

    fun setCategory(value: String) = _state.update { it.copy(category = value) }

    fun toggleTag(tag: String) = _state.update {
        it.copy(tags = if (tag in it.tags) it.tags - tag else it.tags + tag)
    }

    fun setSavePath(value: String) = _state.update { it.copy(savePath = value) }
    fun setStartPaused(value: Boolean) = _state.update { it.copy(startPaused = value) }
    fun setSkipHashCheck(value: Boolean) = _state.update { it.copy(skipHashCheck = value) }
    fun setSequential(value: Boolean) = _state.update { it.copy(sequential = value) }
    fun setFirstLastPiece(value: Boolean) = _state.update { it.copy(firstLastPiece = value) }

    fun submit(instanceId: Int, onAdded: () -> Unit) {
        val current = _state.value
        if (!current.canSubmit) return

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

                    if (response.added == 0 && failureDetail.isNotEmpty()) {
                        _state.update {
                            it.copy(isBusy = false, error = failureDetail.joinToString("\n"))
                        }
                    } else {
                        _state.value = AddTorrentUiState()
                        onAdded()
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isBusy = false, error = error.message) }
                }
        }
    }
}
