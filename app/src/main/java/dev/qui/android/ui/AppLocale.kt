/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Language selection. Mirrors qui's web behaviour: a stored choice wins, otherwise
 * the device's own languages pick the closest of the nine locales qui ships.
 *
 * Android already resolves the initial language from res/values-<lang>, so the
 * automatic path needs no code. This exists for the manual override in Settings,
 * which uses the platform's per-app language API on Android 13+ and falls back to
 * a Configuration wrapper below that.
 */

package dev.qui.android.ui

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** The language tags qui ships, in the order its own picker lists them. */
val SUPPORTED_LANGUAGES = listOf(
    "en", "uk", "zh-CN", "fr", "de", "cs", "it", "ko", "pt-BR",
)

/** Endonyms, copied from qui's languageNames so both UIs read the same. */
val LANGUAGE_NAMES = mapOf(
    "en" to "English",
    "uk" to "Українська",
    "zh-CN" to "简体中文",
    "fr" to "Français",
    "de" to "Deutsch",
    "cs" to "Čeština",
    "it" to "Italiano",
    "ko" to "한국어",
    "pt-BR" to "Português Brasileiro",
)

object AppLocale {

    private const val PREFS = "qui.locale"
    private const val KEY_TAG = "language"

    /**
     * The stored override, or null for "follow the system". Kept in SharedPreferences
     * rather than the DataStore the rest of the app uses because attachBaseContext
     * needs it synchronously, before any coroutine can run.
     */
    fun stored(context: Context): String? =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TAG, null)
            ?.takeIf { it in SUPPORTED_LANGUAGES }

    /**
     * Applies a language for good. Pass null to hand control back to the system.
     * The caller is expected to recreate the activity afterwards.
     */
    fun apply(context: Context, tag: String?) {
        val normalised = tag?.takeIf { it in SUPPORTED_LANGUAGES }
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .apply { if (normalised == null) remove(KEY_TAG) else putString(KEY_TAG, normalised) }
            .apply()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Lets the choice survive a cold start and show up in system settings.
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                if (normalised == null) {
                    LocaleList.getEmptyLocaleList()
                } else {
                    LocaleList.forLanguageTags(normalised)
                }
        }
    }

    /**
     * Wraps a base context in the stored language. A no-op on Android 13+, where the
     * platform has already applied applicationLocales by the time this runs.
     */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = stored(base) ?: return base

        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    /**
     * The language actually in effect, for showing a checkmark in the picker. Null
     * means no override is set, so the row for "system default" is the selected one.
     */
    fun current(context: Context): String? = stored(context)
}
