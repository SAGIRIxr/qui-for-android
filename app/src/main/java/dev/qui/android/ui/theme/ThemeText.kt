/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package dev.qui.android.ui.theme

import androidx.annotation.StringRes
import dev.qui.android.R

/** Localized presentation stays separate from generated palettes and persistent IDs. */
data class ThemeText(@StringRes val name: Int, @StringRes val description: Int)

fun themeText(id: String): ThemeText? = when (id) {
    "default" -> ThemeText(R.string.theme_default_name, R.string.theme_default_description)
    "autobrr" -> ThemeText(R.string.theme_autobrr_name, R.string.theme_autobrr_description)
    "dragon" -> ThemeText(R.string.theme_dragon_name, R.string.theme_dragon_description)
    "kyle" -> ThemeText(R.string.theme_kyle_name, R.string.theme_kyle_description)
    "minimal" -> ThemeText(R.string.theme_minimal_name, R.string.theme_minimal_description)
    "napster" -> ThemeText(R.string.theme_napster_name, R.string.theme_napster_description)
    "nightwalker" -> ThemeText(R.string.theme_nightwalker_name, R.string.theme_nightwalker_description)
    "swizzin" -> ThemeText(R.string.theme_swizzin_name, R.string.theme_swizzin_description)
    "wave" -> ThemeText(R.string.theme_wave_name, R.string.theme_wave_description)
    else -> null
}
