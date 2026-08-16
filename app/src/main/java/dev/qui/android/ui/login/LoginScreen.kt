/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Mirrors qui's Login.tsx / Setup.tsx: one card, server address first, then either
 * password credentials or an API key.
 */

package dev.qui.android.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.qui.android.R
import dev.qui.android.ui.components.QuiLogo
import dev.qui.android.ui.theme.QuiTheme

@Composable
fun LoginScreen(
    onAuthenticated: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = QuiTheme.palette
    var passwordVisible by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .widthIn(max = 460.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            QuiLogo(modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                text = "qui",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = palette.mutedForeground,
            )

            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = palette.card,
                    contentColor = palette.cardForeground,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    OutlinedTextField(
                        value = state.serverUrl,
                        onValueChange = viewModel::setServerUrl,
                        label = { Text(stringResource(R.string.login_server_address)) },
                        placeholder = {
                            Text(stringResource(R.string.login_server_placeholder))
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Next,
                        ),
                    )

                    OutlinedButton(
                        onClick = viewModel::probeServer,
                        enabled = !state.isBusy && state.serverUrl.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.login_test_connection))
                    }

                    state.probeRes?.let {
                        Text(
                            text = stringResource(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.chart3,
                        )
                    }

                    TabRow(selectedTabIndex = if (state.mode == AuthMode.Password) 0 else 1) {
                        Tab(
                            selected = state.mode == AuthMode.Password,
                            onClick = { viewModel.setMode(AuthMode.Password) },
                            text = { Text(stringResource(R.string.login_password)) },
                        )
                        Tab(
                            selected = state.mode == AuthMode.ApiKey,
                            onClick = { viewModel.setMode(AuthMode.ApiKey) },
                            text = { Text(stringResource(R.string.login_api_key)) },
                        )
                    }

                    if (state.mode == AuthMode.Password) {
                        OutlinedTextField(
                            value = state.username,
                            onValueChange = viewModel::setUsername,
                            label = { Text(stringResource(R.string.login_username)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        )
                        OutlinedTextField(
                            value = state.password,
                            onValueChange = viewModel::setPassword,
                            label = { Text(stringResource(R.string.login_password)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) {
                                            Icons.Default.VisibilityOff
                                        } else {
                                            Icons.Default.Visibility
                                        },
                                        contentDescription = stringResource(
                                            if (passwordVisible) {
                                                R.string.login_hide_password
                                            } else {
                                                R.string.login_show_password
                                            }
                                        ),
                                    )
                                }
                            },
                        )
                    } else {
                        OutlinedTextField(
                            value = state.apiKey,
                            onValueChange = viewModel::setApiKey,
                            label = { Text(stringResource(R.string.login_api_key)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (passwordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) {
                                            Icons.Default.VisibilityOff
                                        } else {
                                            Icons.Default.Visibility
                                        },
                                        contentDescription = stringResource(
                                            R.string.login_toggle_key_visibility
                                        ),
                                    )
                                }
                            },
                        )
                        Text(
                            text = stringResource(R.string.login_api_key_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.mutedForeground,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.login_trust_certs),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                stringResource(R.string.login_trust_certs_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.mutedForeground,
                            )
                        }
                        Switch(
                            checked = state.trustAllCerts,
                            onCheckedChange = viewModel::setTrustAllCerts,
                        )
                    }

                    (state.errorRes?.let { stringResource(it) } ?: state.errorRaw)?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.destructive,
                        )
                    }

                    Button(
                        onClick = { viewModel.submit(onAuthenticated) },
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(
                                stringResource(
                                    when {
                                        state.mode == AuthMode.ApiKey ->
                                            R.string.login_connect
                                        state.setupRequired ->
                                            R.string.login_create_account
                                        else -> R.string.login_sign_in
                                    }
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
