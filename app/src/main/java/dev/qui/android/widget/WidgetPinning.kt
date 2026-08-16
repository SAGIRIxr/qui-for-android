/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Adding a widget from inside Settings. Most launchers support the pin request;
 * the ones that do not are told apart up front so the button is never a dead end.
 */

package dev.qui.android.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import androidx.annotation.StringRes
import dev.qui.android.R

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

/** False on launchers with no pin support, where the widget picker is the only route. */
fun widgetPinningSupported(context: Context): Boolean =
    AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

/**
 * Asks the launcher to place the widget. The launcher owns the confirmation dialog,
 * so nothing lands on the home screen without the user agreeing to it.
 */
fun requestPinWidget(context: Context, provider: Class<out AppWidgetProvider>): Boolean =
    runCatching {
        AppWidgetManager.getInstance(context).requestPinAppWidget(
            ComponentName(context, provider),
            null,
            null,
        )
    }.getOrDefault(false)
