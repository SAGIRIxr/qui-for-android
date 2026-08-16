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
 * Android will not let a widget refresh itself faster than every 30 minutes, and MIUI /
 * HyperOS is stricter still, so the periodic update is a backstop rather than the
 * mechanism: the refresh button and QuiWidgets.refresh() — called whenever the app has
 * fresh numbers in hand — are what keep it current.
 */

package dev.qui.android.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.SizeF
import android.widget.RemoteViews
import dagger.hilt.android.AndroidEntryPoint
import dev.qui.android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

abstract class StatsWidgetProvider : AppWidgetProvider() {

    /**
     * Declared by each subclass rather than injected here: Hilt injects the fields of
     * the class carrying @AndroidEntryPoint, which is always the concrete provider.
     */
    protected abstract val dataSource: WidgetDataSource

    /** What a launcher that does not ask for a size gets. */
    protected abstract val fallbackLayout: Int

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
        val ids = manager.getAppWidgetIds(ComponentName(context, javaClass))
        // A tap on the button means "now", so the reuse window is bypassed.
        render(context, manager, ids, force = true)
    }

    private fun render(
        context: Context,
        manager: AppWidgetManager,
        appWidgetIds: IntArray,
        force: Boolean,
    ) {
        if (appWidgetIds.isEmpty()) return

        // goAsync buys a few seconds of background time; the broadcast would otherwise
        // be considered finished the moment this method returns.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val snapshot = withTimeoutOrNull(WORK_BUDGET_MS) { dataSource.load(force) }
                    ?: WidgetSnapshot(error = WidgetDataSource.ERROR_UNREACHABLE)
                manager.updateAppWidget(appWidgetIds, responsiveViews(context, snapshot))
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * One RemoteViews per size the launcher may hand us, so a widget dragged down to
     * 2x1 shows only the speeds while a 4x2 gets the counts and free space. Launchers
     * before Android 12 ask for a single layout, which is the provider's own.
     */
    private fun responsiveViews(context: Context, snapshot: WidgetSnapshot): RemoteViews {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return statsViews(context, fallbackLayout, snapshot, javaClass)
        }

        return RemoteViews(
            mapOf(
                SizeF(110f, 40f) to
                    statsViews(context, R.layout.widget_compact, snapshot, javaClass),
                SizeF(150f, 110f) to
                    statsViews(context, R.layout.widget_stats_small, snapshot, javaClass),
                SizeF(250f, 110f) to
                    statsViews(context, R.layout.widget_stats_wide, snapshot, javaClass),
            )
        )
    }

    private companion object {
        const val WORK_BUDGET_MS = 8_000L
    }
}

/** 4x2 by default: speeds, all four state counts, total size and free space. */
@AndroidEntryPoint
class QuiWidgetProvider : StatsWidgetProvider() {
    @Inject lateinit var source: WidgetDataSource
    override val dataSource: WidgetDataSource get() = source
    override val fallbackLayout: Int = R.layout.widget_stats_wide
}

/** 2x1 by default: the two speeds and nothing else. */
@AndroidEntryPoint
class QuiSpeedWidgetProvider : StatsWidgetProvider() {
    @Inject lateinit var source: WidgetDataSource
    override val dataSource: WidgetDataSource get() = source
    override val fallbackLayout: Int = R.layout.widget_compact
}

/** 2x2 by default: speeds plus the downloading and seeding counts. */
@AndroidEntryPoint
class QuiOverviewWidgetProvider : StatsWidgetProvider() {
    @Inject lateinit var source: WidgetDataSource
    override val dataSource: WidgetDataSource get() = source
    override val fallbackLayout: Int = R.layout.widget_stats_small
}
