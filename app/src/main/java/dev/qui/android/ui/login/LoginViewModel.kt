/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package dev.qui.android.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qui.android.data.QuiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** qui either needs its first account created or accepts a login; the server tells us which. */
enum class AuthMode { Password, ApiKey }

data class LoginUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val apiKey: String = "",
    val mode: AuthMode = AuthMode.Password,
    val trustAllCerts: Boolean = false,
    val setupRequired: Boolean = false,
    val isBusy: Boolean = false,
    val error: String? = null,
    val probeMessage: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: QuiRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun setServerUrl(value: String) = _state.update {
        it.copy(serverUrl = value, error = null, probeMessage = null, setupRequired = false)
    }

    fun setUsername(value: String) = _state.update { it.copy(username = value, error = null) }
    fun setPassword(value: String) = _state.update { it.copy(password = value, error = null) }
    fun setApiKey(value: String) = _state.update { it.copy(apiKey = value, error = null) }
    fun setMode(mode: AuthMode) = _state.update { it.copy(mode = mode, error = null) }
    fun setTrustAllCerts(value: Boolean) = _state.update { it.copy(trustAllCerts = value) }

    /**
     * Asks the server whether it still needs its first account. Doing this before the
     * user types credentials means the button can say "Create account" when it should.
     */
    fun probeServer() {
        val url = _state.value.serverUrl.trim()
        if (url.isBlank()) {
            _state.update { it.copy(error = "Enter the address of your qui server") }
            return
        }

        _state.update { it.copy(isBusy = true, error = null, probeMessage = null) }
        viewModelScope.launch {
            repository.isSetupRequired(url)
                .onSuccess { required ->
                    _state.update {
                        it.copy(
                            isBusy = false,
                            setupRequired = required,
                            probeMessage = if (required) {
                                "This qui server has no account yet — create one below."
                            } else {
                                "Connected. Sign in below."
                            },
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isBusy = false, error = error.friendlyMessage())
                    }
                }
        }
    }

    fun submit(onAuthenticated: () -> Unit) {
        val current = _state.value
        if (current.serverUrl.isBlank()) {
            _state.update { it.copy(error = "Enter the address of your qui server") }
            return
        }

        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            val result = when {
                current.mode == AuthMode.ApiKey -> repository.loginWithApiKey(
                    serverUrl = current.serverUrl.trim(),
                    apiKey = current.apiKey.trim(),
                    trustAllCerts = current.trustAllCerts,
                )

                current.setupRequired -> repository.setupFirstUser(
                    serverUrl = current.serverUrl.trim(),
                    username = current.username.trim(),
                    password = current.password,
                    trustAllCerts = current.trustAllCerts,
                )

                else -> repository.loginWithPassword(
                    serverUrl = current.serverUrl.trim(),
                    username = current.username.trim(),
                    password = current.password,
                    trustAllCerts = current.trustAllCerts,
                )
            }

            result
                .onSuccess {
                    _state.update { it.copy(isBusy = false) }
                    onAuthenticated()
                }
                .onFailure { error ->
                    _state.update { it.copy(isBusy = false, error = error.friendlyMessage()) }
                }
        }
    }
}

/** Raw exception text is unhelpful on a login form; translate the common failures. */
internal fun Throwable.friendlyMessage(): String {
    val raw = message ?: return "Something went wrong"
    return when {
        raw.contains("401") -> "Wrong credentials"
        raw.contains("403") -> "Access denied by the server"
        raw.contains("404") -> "That address does not look like a qui server"
        raw.contains("Unable to resolve host", true) -> "Cannot reach that host"
        raw.contains("CertPathValidator", true) || raw.contains("SSLHandshake", true) ->
            "TLS certificate was rejected — enable \"Trust self-signed certificates\" if this is your own server"
        raw.contains("Failed to connect", true) || raw.contains("timeout", true) ->
            "Cannot reach the server — check the address and that it is running"
        else -> raw
    }
}
