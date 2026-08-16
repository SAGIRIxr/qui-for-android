/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * The tall widget: whatever is transferring right now, with progress bars.
 */

package dev.qui.android.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.qui.android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@AndroidEntryPoint
class QuiTorrentsWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var dataSource: WidgetDataSource

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        render(context, appWidgetManager, appWidgetIds, force = false)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_WIDGET_REFRESH) return

        val manager = AppWidgetManager.getInstance(context)
        render(context, manager, manager.getAppWidgetIds(ComponentName(context, javaClass)), true)
    }

    private fun render(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
        force: Boolean,
    ) {
        if (appWidgetIds.isEmpty()) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val snapshot = withTimeoutOrNull(WORK_BUDGET_MS) { dataSource.load(force) }
                    ?: WidgetSnapshot(error = WidgetDataSource.ERROR_UNREACHABLE)

                appWidgetIds.forEach { id ->
                    manager.updateAppWidget(id, torrentsViews(context, id, snapshot, javaClass))
                }
                // The header is already drawn from the new snapshot; this tells the
                // factory its rows are stale too.
                manager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val WORK_BUDGET_MS = 8_000L
    }
}
