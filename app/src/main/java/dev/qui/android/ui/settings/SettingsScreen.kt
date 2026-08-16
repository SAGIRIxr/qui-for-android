/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Port of the appearance dialog and preference rows from qui's MobileFooterNav and
 * Settings page: theme + variation, light/dark mode, list density, speed units,
 * language, incognito mode, and the signed-in account.
 */

package dev.qui.android.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.qui.android.BuildConfig
import dev.qui.android.R
import dev.qui.android.data.SpeedUnit
import dev.qui.android.data.ThemeMode
import dev.qui.android.data.ViewMode
import dev.qui.android.ui.AppLocale
import dev.qui.android.ui.LANGUAGE_NAMES
import dev.qui.android.ui.RootViewModel
import dev.qui.android.ui.SUPPORTED_LANGUAGES
import dev.qui.android.ui.components.QuiCard
import dev.qui.android.ui.theme.QuiTheme
import dev.qui.android.ui.theme.QuiThemes

private const val SOURCE_URL = "https://github.com/SAGIRIxr/qui-for-android"

@Composable
fun SettingsScreen(
    root: RootViewModel = hiltViewModel(),
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val prefs by root.preferences.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val palette = QuiTheme.palette
    val context = LocalContext.current
    var showLogout by remember { mutableStateOf(false) }
    var showLanguages by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionCard(stringResource(R.string.settings_account)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = account.username
                                ?: stringResource(R.string.settings_signed_in),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = account.serverUrl ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.mutedForeground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(onClick = { showLogout = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.settings_sign_out))
                    }
                }
                account.version?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "qui $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.mutedForeground,
                    )
                }
            }
        }

        item {
            SectionCard(stringResource(R.string.settings_language)) {
                val active = AppLocale.current(context)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLanguages = true }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Language,
                        contentDescription = null,
                        tint = palette.mutedForeground,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = active?.let { LANGUAGE_NAMES[it] ?: it }
                            ?: stringResource(R.string.settings_language_system),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            SectionCard(stringResource(R.string.settings_mode)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip(
                        label = stringResource(R.string.settings_mode_light),
                        icon = Icons.Default.LightMode,
                        selected = prefs.themeMode == ThemeMode.Light,
                    ) { root.setThemeMode(ThemeMode.Light) }
                    ModeChip(
                        label = stringResource(R.string.settings_mode_dark),
                        icon = Icons.Default.DarkMode,
                        selected = prefs.themeMode == ThemeMode.Dark,
                    ) { root.setThemeMode(ThemeMode.Dark) }
                    ModeChip(
                        label = stringResource(R.string.settings_mode_system),
                        icon = Icons.Default.SettingsBrightness,
                        selected = prefs.themeMode == ThemeMode.Auto,
                    ) { root.setThemeMode(ThemeMode.Auto) }
                }
            }
        }

        item {
            SectionCard(stringResource(R.string.settings_view_mode)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ViewMode.Normal to R.string.settings_view_mode_normal,
                        ViewMode.Compact to R.string.settings_view_mode_compact,
                        ViewMode.UltraCompact to R.string.settings_view_mode_ultra,
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = prefs.viewMode == mode,
                            onClick = { root.setViewMode(mode) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            }
        }

        item {
            SectionCard(stringResource(R.string.settings_speed_units)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = prefs.speedUnit == SpeedUnit.Bytes,
                        onClick = { root.setSpeedUnit(SpeedUnit.Bytes) },
                        label = { Text("MiB/s") },
                    )
                    FilterChip(
                        selected = prefs.speedUnit == SpeedUnit.Bits,
                        onClick = { root.setSpeedUnit(SpeedUnit.Bits) },
                        label = { Text("Mbps") },
                    )
                }
            }
        }

        item {
            SectionCard(stringResource(R.string.settings_behaviour)) {
                ToggleRow(
                    title = stringResource(R.string.settings_incognito),
                    subtitle = stringResource(R.string.settings_incognito_hint),
                    checked = prefs.incognito,
                    onChange = root::setIncognito,
                )
                ToggleRow(
                    title = stringResource(R.string.settings_confirm_delete),
                    subtitle = stringResource(R.string.settings_confirm_delete_hint),
                    checked = prefs.confirmDelete,
                    onChange = root::setConfirmDelete,
                )
                ToggleRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    subtitle = stringResource(R.string.settings_dynamic_color_hint),
                    checked = prefs.dynamicColor,
                    onChange = root::setDynamicColor,
                )
            }
        }

        item {
            SectionCard(stringResource(R.string.settings_theme)) {
                QuiThemes.forEach { theme ->
                    ThemeRow(
                        name = theme.name,
                        description = theme.description,
                        selected = prefs.themeId == theme.id && prefs.themeVariation == null,
                        swatch = theme.light.primary,
                        onClick = { root.setTheme(theme.id, null) },
                    )

                    if (theme.variations.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(start = 30.dp, top = 4.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            theme.variations.forEach { variation ->
                                val isSelected = prefs.themeId == theme.id &&
                                    prefs.themeVariation == variation.id
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(variation.light.primary)
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) {
                                                palette.foreground
                                            } else {
                                                palette.border
                                            },
                                            shape = CircleShape,
                                        )
                                        .clickable { root.setTheme(theme.id, variation.id) },
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionCard(stringResource(R.string.settings_about)) {
                Text(
                    text = "qui for Android ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.mutedForeground,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL))
                            )
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Code,
                        contentDescription = null,
                        tint = palette.mutedForeground,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.settings_source_code),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.primary,
                    )
                }
            }
        }
    }

    if (showLanguages) {
        LanguageDialog(
            current = AppLocale.current(context),
            onDismiss = { showLanguages = false },
            onSelect = { tag ->
                showLanguages = false
                AppLocale.apply(context, tag)
                // Resources are resolved at activity creation, so the whole tree has to
                // be rebuilt for the new language to take effect.
                (context as? android.app.Activity)?.recreate()
            },
        )
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text(stringResource(R.string.settings_sign_out_title)) },
            text = { Text(stringResource(R.string.settings_sign_out_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogout = false
                        root.logout()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.settings_sign_out),
                        color = palette.destructive,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogout = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

/**
 * The same list qui's footer nav offers, plus a "system default" row that hands
 * language selection back to Android's own matching.
 */
@Composable
private fun LanguageDialog(
    current: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    val palette = QuiTheme.palette

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                LanguageRow(
                    label = stringResource(R.string.settings_language_system),
                    selected = current == null,
                    onClick = { onSelect(null) },
                )
                SUPPORTED_LANGUAGES.forEach { tag ->
                    LanguageRow(
                        label = LANGUAGE_NAMES[tag] ?: tag,
                        selected = current == tag,
                        onClick = { onSelect(tag) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        },
    )
}

@Composable
private fun LanguageRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = QuiTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) palette.primary else palette.foreground,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = stringResource(R.string.settings_selected),
                tint = palette.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    val palette = QuiTheme.palette
    QuiCard {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = palette.mutedForeground,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun ModeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(icon, contentDescription = null, Modifier.size(16.dp))
        },
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val palette = QuiTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = palette.mutedForeground,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ThemeRow(
    name: String,
    description: String,
    selected: Boolean,
    swatch: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val palette = QuiTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(swatch)
                .border(1.dp, palette.border, CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            if (description.isNotBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.mutedForeground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = stringResource(R.string.settings_selected),
                tint = palette.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
