/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * The tall widget: whatever is transferring right now, with progress bars. Like the
 * stats providers it paints from cache and leaves the fetch to WidgetRefreshWorker.
 */

package dev.qui.android.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class QuiTorrentsWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var dataSource: WidgetDataSource

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        paintPending(context, appWidgetManager, appWidgetIds)
        WidgetRefreshScheduler.refreshNow(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_WIDGET_REFRESH) return

        val manager = AppWidgetManager.getInstance(context)
        paintPending(context, manager, manager.getAppWidgetIds(ComponentName(context, javaClass)))
        WidgetRefreshScheduler.refreshNow(context)
    }

    override fun onEnabled(context: Context) {
        WidgetRefreshScheduler.sync(context)
    }

    override fun onDisabled(context: Context) {
        WidgetRefreshScheduler.sync(context)
    }

    private fun paintPending(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        if (appWidgetIds.isEmpty()) return

        val snapshot = dataSource.cached?.copy(refreshing = true)
            ?: WidgetSnapshot(error = WidgetDataSource.ERROR_NO_RESPONSE, refreshing = true)
        appWidgetIds.forEach { id ->
            manager.updateAppWidget(id, torrentsViews(context, id, snapshot, javaClass))
        }
    }
}
