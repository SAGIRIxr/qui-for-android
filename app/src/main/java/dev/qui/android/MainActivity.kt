/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package dev.qui.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.qui.android.ui.QuiApp
import dev.qui.android.ui.RootViewModel
import dev.qui.android.ui.addintent.AddIntent
import dev.qui.android.ui.addintent.parseAddIntent
import dev.qui.android.ui.theme.QuiAppTheme
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Magnet links and shared .torrent files arrive as Intents. They are surfaced as
     * state so the add sheet can open once the UI is composed, and re-delivered
     * through onNewIntent because the activity is singleTask.
     */
    private val pendingAdd = MutableStateFlow<AddIntent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingAdd.value = parseAddIntent(intent, contentResolver)

        setContent {
            val root: RootViewModel = hiltViewModel()
            val prefs by root.preferences.collectAsStateWithLifecycle()

            QuiAppTheme(
                themeId = prefs.themeId,
                variationId = prefs.themeVariation,
                themeMode = prefs.themeMode,
                dynamicColor = prefs.dynamicColor,
            ) {
                QuiApp(pendingAdd = pendingAdd)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseAddIntent(intent, contentResolver)?.let { pendingAdd.value = it }
    }
}
