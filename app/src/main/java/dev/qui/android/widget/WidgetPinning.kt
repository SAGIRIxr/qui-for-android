/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Adding a widget from inside Settings.
 *
 * requestPinAppWidget is fire-and-forget, and on MIUI / HyperOS it returns true even
 * when the app lacks the "create home screen shortcut" permission — the request is
 * simply dropped and nothing ever appears. There is no API that reports this, so the
 * launcher is given a callback to fire on success and its silence is what the caller
 * reacts to.
 */

package dev.qui.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.StringRes
import dev.qui.android.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A widget offered in Settings, in the same order the launcher's picker lists them. */
data class PinnableWidget(
    @StringRes val name: Int,
    @StringRes val description: Int,
    val provider: Class<out AppWidgetProvider>,
)

val PINNABLE_WIDGETS = listOf(
    PinnableWidget(
        R.string.widget_speed_name,
        R.string.widget_speed_description,
        QuiSpeedWidgetProvider::class.java,
    ),
    PinnableWidget(
        R.string.widget_overview_name,
        R.string.widget_overview_description,
        QuiOverviewWidgetProvider::class.java,
    ),
    PinnableWidget(
        R.string.widget_name,
        R.string.widget_description,
        QuiWidgetProvider::class.java,
    ),
    PinnableWidget(
        R.string.widget_torrents_name,
        R.string.widget_torrents_description,
        QuiTorrentsWidgetProvider::class.java,
    ),
)

private const val ACTION_WIDGET_PINNED = "dev.qui.android.widget.PINNED"
private const val REQUEST_PIN_CALLBACK = 5

/** Counts confirmed placements so the UI can tell "done" from "never happened". */
object WidgetPinTracker {
    private val _pinned = MutableStateFlow(0)
    val pinned: StateFlow<Int> = _pinned.asStateFlow()

    internal fun record() {
        _pinned.value += 1
    }
}

/** Receives the launcher's confirmation that a widget was actually placed. */
class WidgetPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_WIDGET_PINNED) WidgetPinTracker.record()
    }
}

/** False on launchers with no pin support, where the widget picker is the only route. */
fun widgetPinningSupported(context: Context): Boolean =
    AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

/**
 * Asks the launcher to place the widget. The launcher owns the confirmation dialog, so
 * nothing lands on the home screen without the user agreeing to it. Returns false only
 * when the request was rejected outright — a true result still means nothing until the
 * callback arrives.
 */
fun requestPinWidget(context: Context, provider: Class<out AppWidgetProvider>): Boolean =
    runCatching {
        val callback = PendingIntent.getBroadcast(
            context,
            REQUEST_PIN_CALLBACK,
            Intent(context, WidgetPinnedReceiver::class.java).setAction(ACTION_WIDGET_PINNED),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        AppWidgetManager.getInstance(context).requestPinAppWidget(
            ComponentName(context, provider),
            null,
            callback,
        )
    }.getOrDefault(false)

/**
 * The app's own settings page, which is where the shortcut permission lives on the
 * launchers that gate it.
 */
fun openAppSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
