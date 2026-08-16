/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package dev.qui.android.ui.login

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qui.android.R
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
    // Errors are resource ids so they follow the app language; `errorRaw` carries the
    // server's own wording for failures we have no phrasing of our own for.
    @StringRes val errorRes: Int? = null,
    val errorRaw: String? = null,
    @StringRes val probeRes: Int? = null,
)

/** Either a translated failure or the raw text the server gave us. */
internal data class LoginError(@StringRes val res: Int?, val raw: String?)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: QuiRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private fun LoginUiState.cleared() = copy(errorRes = null, errorRaw = null)

    fun setServerUrl(value: String) = _state.update {
        it.cleared().copy(serverUrl = value, probeRes = null, setupRequired = false)
    }

    fun setUsername(value: String) = _state.update { it.cleared().copy(username = value) }
    fun setPassword(value: String) = _state.update { it.cleared().copy(password = value) }
    fun setApiKey(value: String) = _state.update { it.cleared().copy(apiKey = value) }
    fun setMode(mode: AuthMode) = _state.update { it.cleared().copy(mode = mode) }
    fun setTrustAllCerts(value: Boolean) = _state.update { it.copy(trustAllCerts = value) }

    /**
     * Asks the server whether it still needs its first account. Doing this before the
     * user types credentials means the button can say "Create account" when it should.
     */
    fun probeServer() {
        val url = _state.value.serverUrl.trim()
        if (url.isBlank()) {
            _state.update { it.copy(errorRes = R.string.login_error_no_address, errorRaw = null) }
            return
        }

        _state.update { it.cleared().copy(isBusy = true, probeRes = null) }
        viewModelScope.launch {
            repository.isSetupRequired(url)
                .onSuccess { required ->
                    _state.update {
                        it.copy(
                            isBusy = false,
                            setupRequired = required,
                            probeRes = if (required) {
                                R.string.login_probe_setup_required
                            } else {
                                R.string.login_probe_ok
                            },
                        )
                    }
                }
                .onFailure { error ->
                    val failure = error.friendlyMessage()
                    _state.update {
                        it.copy(isBusy = false, errorRes = failure.res, errorRaw = failure.raw)
                    }
                }
        }
    }

    fun submit(onAuthenticated: () -> Unit) {
        val current = _state.value
        if (current.serverUrl.isBlank()) {
            _state.update { it.copy(errorRes = R.string.login_error_no_address, errorRaw = null) }
            return
        }

        _state.update { it.cleared().copy(isBusy = true) }
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
                    val failure = error.friendlyMessage()
                    _state.update {
                        it.copy(isBusy = false, errorRes = failure.res, errorRaw = failure.raw)
                    }
                }
        }
    }
}

/** Raw exception text is unhelpful on a login form; translate the common failures. */
internal fun Throwable.friendlyMessage(): LoginError {
    val raw = message ?: return LoginError(R.string.login_error_generic, null)
    val res = when {
        raw.contains("401") -> R.string.login_error_credentials
        raw.contains("403") -> R.string.login_error_forbidden
        raw.contains("404") -> R.string.login_error_not_qui
        raw.contains("Unable to resolve host", true) -> R.string.login_error_host
        raw.contains("CertPathValidator", true) || raw.contains("SSLHandshake", true) ->
            R.string.login_error_tls
        raw.contains("Failed to connect", true) || raw.contains("timeout", true) ->
            R.string.login_error_unreachable
        else -> null
    }
    return LoginError(res, if (res == null) raw else null)
}
