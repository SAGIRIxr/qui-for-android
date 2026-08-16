/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package dev.qui.android

import android.content.Context
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
import dev.qui.android.ui.AppLocale
import dev.qui.android.ui.QuiApp
import dev.qui.android.ui.RootViewModel
import dev.qui.android.ui.addintent.AddIntent
import dev.qui.android.ui.addintent.parseAddIntent
import dev.qui.android.ui.theme.QuiAppTheme
import dev.qui.android.widget.WidgetLaunch
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Magnet links and shared .torrent files arrive as Intents. They are surfaced as
     * state so the add sheet can open once the UI is composed, and re-delivered
     * through onNewIntent because the activity is singleTask.
     */
    private val pendingAdd = MutableStateFlow<AddIntent?>(null)

    /** A row tapped on the list widget: open that torrent instead of the list. */
    private val pendingTorrent = MutableStateFlow<Pair<Int, String>?>(null)

    /** Applies a language chosen in Settings on Android 12 and below. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingAdd.value = parseAddIntent(intent, contentResolver)
        consumeWidgetIntent(intent)

        setContent {
            val root: RootViewModel = hiltViewModel()
            val prefs by root.preferences.collectAsStateWithLifecycle()

            QuiAppTheme(
                themeId = prefs.themeId,
                variationId = prefs.themeVariation,
                themeMode = prefs.themeMode,
                dynamicColor = prefs.dynamicColor,
            ) {
                QuiApp(pendingAdd = pendingAdd, pendingTorrent = pendingTorrent)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseAddIntent(intent, contentResolver)?.let { pendingAdd.value = it }
        consumeWidgetIntent(intent)
    }

    /**
     * Widget taps arrive as extras rather than a Uri, so they cannot be confused with
     * a magnet link handed in by another app. The extras are stripped once read,
     * otherwise returning to the task would replay them.
     */
    private fun consumeWidgetIntent(intent: Intent) {
        if (intent.getBooleanExtra(WidgetLaunch.EXTRA_OPEN_ADD, false)) {
            intent.removeExtra(WidgetLaunch.EXTRA_OPEN_ADD)
            // An empty AddIntent opens the sheet with nothing prefilled.
            pendingAdd.value = AddIntent()
            return
        }

        val hash = intent.getStringExtra(WidgetLaunch.EXTRA_HASH) ?: return
        val instanceId = intent.getIntExtra(WidgetLaunch.EXTRA_INSTANCE_ID, -1)
        intent.removeExtra(WidgetLaunch.EXTRA_HASH)
        intent.removeExtra(WidgetLaunch.EXTRA_INSTANCE_ID)
        if (instanceId >= 0) pendingTorrent.value = instanceId to hash
    }
}
