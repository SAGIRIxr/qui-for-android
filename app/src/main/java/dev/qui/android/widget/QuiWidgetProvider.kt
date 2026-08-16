/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Speeds and torrent counts on the home screen.
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

@AndroidEntryPoint
class QuiWidgetProvider : AppWidgetProvider() {

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
     * One RemoteViews per size the launcher may hand us, so a 2x1 shows only the
     * speeds while a 4x2 gets the counts and free space. Launchers before Android 12
     * ask for a single layout, which is the widest one.
     */
    private fun responsiveViews(context: Context, snapshot: WidgetSnapshot): RemoteViews {
        val wide = statsViews(context, R.layout.widget_stats_wide, snapshot, javaClass)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return wide

        return RemoteViews(
            mapOf(
                SizeF(110f, 40f) to
                    statsViews(context, R.layout.widget_compact, snapshot, javaClass),
                SizeF(150f, 110f) to
                    statsViews(context, R.layout.widget_stats_small, snapshot, javaClass),
                SizeF(250f, 110f) to wide,
            )
        )
    }

    private companion object {
        const val WORK_BUDGET_MS = 8_000L
    }
}
