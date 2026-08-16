/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Home-screen widget with the totals from the dashboard's summary card.
 *
 * Android will not let a widget refresh itself faster than every 30 minutes, so this
 * is a glance rather than a live meter; QuiWidgets.refresh() is what actually keeps it
 * current, called whenever the app itself has fresh numbers in hand.
 */

package dev.qui.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dagger.hilt.android.AndroidEntryPoint
import dev.qui.android.MainActivity
import dev.qui.android.R
import dev.qui.android.data.QuiRepository
import dev.qui.android.data.SpeedUnit
import dev.qui.android.data.remote.SessionStore
import dev.qui.android.ui.format.formatSpeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@AndroidEntryPoint
class QuiWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var repository: QuiRepository

    @Inject lateinit var session: SessionStore

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // goAsync buys a few seconds of background time; the broadcast would otherwise
        // be considered finished the moment this method returns.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val views = withTimeoutOrNull(WORK_BUDGET_MS) { buildViews(context) }
                    ?: placeholder(context, context.getString(R.string.login_error_unreachable))
                appWidgetManager.updateAppWidget(appWidgetIds, views)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun buildViews(context: Context): RemoteViews {
        if (session.isConfigured.first() != true) {
            return placeholder(context, context.getString(R.string.widget_not_signed_in))
        }

        val instances = repository.instances().getOrNull().orEmpty().filter { it.isActive }
        if (instances.isEmpty()) {
            return placeholder(context, context.getString(R.string.torrents_no_clients_title))
        }

        var download = 0L
        var upload = 0L
        var torrents = 0

        // One cheap listing per client carries the server-side totals; the row limit
        // does not change what stats/serverState contain.
        instances.forEach { instance ->
            val response = repository.torrents(
                instanceId = instance.id,
                page = 0,
                limit = 1,
                sort = "added_on",
                order = "desc",
                search = null,
                filters = null,
            ).getOrNull() ?: return@forEach

            download += response.serverState?.dlInfoSpeed
                ?: response.stats?.totalDownloadSpeed ?: 0
            upload += response.serverState?.upInfoSpeed
                ?: response.stats?.totalUploadSpeed ?: 0
            torrents += response.total
        }

        return RemoteViews(context.packageName, R.layout.widget_speeds).apply {
            setTextViewText(R.id.widget_download, "↓ ${formatSpeed(download, SpeedUnit.Bytes)}")
            setTextViewText(R.id.widget_upload, "↑ ${formatSpeed(upload, SpeedUnit.Bytes)}")
            setTextViewText(
                R.id.widget_subtitle,
                context.resources.getQuantityString(R.plurals.torrents_count, torrents, torrents),
            )
            setOnClickPendingIntent(R.id.widget_root, launchIntent(context))
        }
    }

    private fun placeholder(context: Context, message: String) =
        RemoteViews(context.packageName, R.layout.widget_speeds).apply {
            setTextViewText(R.id.widget_download, "↓ –")
            setTextViewText(R.id.widget_upload, "↑ –")
            setTextViewText(R.id.widget_subtitle, message)
            setOnClickPendingIntent(R.id.widget_root, launchIntent(context))
        }

    private fun launchIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        const val WORK_BUDGET_MS = 8_000L
    }
}

object QuiWidgets {
    /**
     * Nudges every placed widget to redraw. Safe to call when none exist — the
     * broadcast simply reaches no receivers.
     */
    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, QuiWidgetProvider::class.java)
        )
        if (ids.isEmpty()) return

        context.sendBroadcast(
            Intent(context, QuiWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
        )
    }
}
