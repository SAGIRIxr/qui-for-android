/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Speeds and torrent counts on the home screen.
 *
 * Three providers rather than one resizable entry: most people never discover that a
 * widget can be dragged bigger, so each size is offered separately in the picker. They
 * share this class and stay resizable — only the size the launcher starts them at
 * differs.
 *
 * The receivers do no network work. They paint what is already known, mark it as
 * refreshing and hand the fetch to WidgetRefreshWorker, which has no ten-second
 * deadline hanging over it — see WidgetRefresh.kt.
 */

package dev.qui.android.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

abstract class StatsWidgetProvider : AppWidgetProvider() {

    /**
     * Declared by each subclass rather than injected here: Hilt injects the fields of
     * the class carrying @AndroidEntryPoint, which is always the concrete provider.
     */
    protected abstract val dataSource: WidgetDataSource

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
        paintPending(context, manager, manager.getAppWidgetIds(componentName(context)))
        WidgetRefreshScheduler.refreshNow(context)
    }

    /**
     * Acknowledges the tap straight away. Without this the widget sits unchanged until
     * the worker lands, which reads as a button that does nothing.
     */
    private fun paintPending(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        if (appWidgetIds.isEmpty()) return

        val snapshot = dataSource.cached?.copy(refreshing = true)
            ?: WidgetSnapshot(error = WidgetDataSource.ERROR_NO_RESPONSE, refreshing = true)
        manager.updateAppWidget(appWidgetIds, responsiveStatsViews(context, snapshot, javaClass))
    }

    private fun componentName(context: Context) =
        android.content.ComponentName(context, javaClass)

    /** First widget of this kind placed. */
    override fun onEnabled(context: Context) {
        WidgetRefreshScheduler.sync(context)
    }

    /** Last one removed; the schedule stops once no provider has any left. */
    override fun onDisabled(context: Context) {
        WidgetRefreshScheduler.sync(context)
    }
}

/** 4x2 by default: speeds, all four state counts, total size and free space. */
@AndroidEntryPoint
class QuiWidgetProvider : StatsWidgetProvider() {
    @Inject lateinit var source: WidgetDataSource
    override val dataSource: WidgetDataSource get() = source
}

/** 2x1 by default: the two speeds and nothing else. */
@AndroidEntryPoint
class QuiSpeedWidgetProvider : StatsWidgetProvider() {
    @Inject lateinit var source: WidgetDataSource
    override val dataSource: WidgetDataSource get() = source
}

/** 2x2 by default: speeds plus the downloading and seeding counts. */
@AndroidEntryPoint
class QuiOverviewWidgetProvider : StatsWidgetProvider() {
    @Inject lateinit var source: WidgetDataSource
    override val dataSource: WidgetDataSource get() = source
}
