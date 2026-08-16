/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Port of the appearance dialog and preference rows from qui's MobileFooterNav and
 * Settings page: theme + variation, light/dark mode, list density, speed units,
 * incognito mode, and the signed-in account.
 */

package dev.qui.android.ui.settings

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.qui.android.data.SpeedUnit
import dev.qui.android.data.ThemeMode
import dev.qui.android.data.ViewMode
import dev.qui.android.ui.RootViewModel
import dev.qui.android.ui.theme.QuiTheme
import dev.qui.android.ui.theme.QuiThemes

@Composable
fun SettingsScreen(
    root: RootViewModel = hiltViewModel(),
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val prefs by root.preferences.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val palette = QuiTheme.palette
    var showLogout by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionCard("Account") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = account.username ?: "Signed in",
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
                        Icon(Icons.Default.Logout, contentDescription = null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sign out")
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
            SectionCard("Mode") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeChip(
                        label = "Light",
                        icon = Icons.Default.LightMode,
                        selected = prefs.themeMode == ThemeMode.Light,
                    ) { root.setThemeMode(ThemeMode.Light) }
                    ModeChip(
                        label = "Dark",
                        icon = Icons.Default.DarkMode,
                        selected = prefs.themeMode == ThemeMode.Dark,
                    ) { root.setThemeMode(ThemeMode.Dark) }
                    ModeChip(
                        label = "System",
                        icon = Icons.Default.SettingsBrightness,
                        selected = prefs.themeMode == ThemeMode.Auto,
                    ) { root.setThemeMode(ThemeMode.Auto) }
                }
            }
        }

        item {
            SectionCard("Torrent list density") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ViewMode.Normal to "Normal",
                        ViewMode.Compact to "Compact",
                        ViewMode.UltraCompact to "Ultra",
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = prefs.viewMode == mode,
                            onClick = { root.setViewMode(mode) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }

        item {
            SectionCard("Speed units") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = prefs.speedUnit == SpeedUnit.Bytes,
                        onClick = { root.setSpeedUnit(SpeedUnit.Bytes) },
                        label = { Text("B/s") },
                    )
                    FilterChip(
                        selected = prefs.speedUnit == SpeedUnit.Bits,
                        onClick = { root.setSpeedUnit(SpeedUnit.Bits) },
                        label = { Text("bps") },
                    )
                }
            }
        }

        item {
            SectionCard("Behaviour") {
                ToggleRow(
                    title = "Incognito mode",
                    subtitle = "Replace names, categories and trackers with Linux ISOs.",
                    checked = prefs.incognito,
                    onChange = root::setIncognito,
                )
                ToggleRow(
                    title = "Confirm before deleting",
                    subtitle = "Ask before removing torrents.",
                    checked = prefs.confirmDelete,
                    onChange = root::setConfirmDelete,
                )
                ToggleRow(
                    title = "Use system colours",
                    subtitle = "Follow the Android wallpaper palette instead of a qui theme.",
                    checked = prefs.dynamicColor,
                    onChange = root::setDynamicColor,
                )
            }
        }

        item {
            SectionCard("Theme") {
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
    }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false },
            title = { Text("Sign out?") },
            text = { Text("The stored credentials for this server will be removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogout = false
                        root.logout()
                    },
                ) { Text("Sign out", color = palette.destructive) }
            },
            dismissButton = {
                TextButton(onClick = { showLogout = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    val palette = QuiTheme.palette
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = palette.card,
            contentColor = palette.cardForeground,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
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
                contentDescription = "Selected",
                tint = palette.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
