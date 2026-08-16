/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Turns a WidgetSnapshot into RemoteViews. All three stats layouts share these
 * setters: RemoteViews ignores an action whose view id is absent, so the compact
 * layout simply drops the tiles it does not contain.
 */

package dev.qui.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import dev.qui.android.MainActivity
import dev.qui.android.R
import dev.qui.android.data.SpeedUnit
import dev.qui.android.ui.format.formatBytes
import dev.qui.android.ui.format.formatEta
import dev.qui.android.ui.format.formatSpeed
import java.text.DateFormat
import java.util.Date

/** Extras MainActivity reads to decide where a widget tap should land. */
object WidgetLaunch {
    const val EXTRA_OPEN_ADD = "dev.qui.android.widget.OPEN_ADD"
    const val EXTRA_INSTANCE_ID = "dev.qui.android.widget.INSTANCE_ID"
    const val EXTRA_HASH = "dev.qui.android.widget.HASH"
}

internal const val ACTION_WIDGET_REFRESH = "dev.qui.android.widget.REFRESH"

/**
 * PendingIntents that differ only in their extras are treated as the same intent, so
 * each target gets its own request code to keep them from overwriting one another.
 */
private const val REQUEST_OPEN = 1
private const val REQUEST_ADD = 2
private const val REQUEST_REFRESH = 3
private const val REQUEST_ROW_TEMPLATE = 4

internal fun openAppIntent(context: Context): PendingIntent =
    PendingIntent.getActivity(
        context,
        REQUEST_OPEN,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

internal fun addTorrentIntent(context: Context): PendingIntent =
    PendingIntent.getActivity(
        context,
        REQUEST_ADD,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(WidgetLaunch.EXTRA_OPEN_ADD, true),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

internal fun refreshIntent(context: Context, provider: Class<*>): PendingIntent =
    PendingIntent.getBroadcast(
        context,
        REQUEST_REFRESH,
        Intent(context, provider).apply {
            action = ACTION_WIDGET_REFRESH
            // Two providers share the action, so the component alone must not decide
            // which PendingIntent wins the cache.
            data = Uri.parse("qui://refresh/${provider.name}")
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

/**
 * The template a list row fills in. Mutable is required: RemoteViews merges the row's
 * fill-in intent into it, which an immutable PendingIntent forbids.
 */
internal fun rowTemplateIntent(context: Context): PendingIntent =
    PendingIntent.getActivity(
        context,
        REQUEST_ROW_TEMPLATE,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

/** Builds one stats layout. Call once per size in the responsive map. */
internal fun statsViews(
    context: Context,
    layoutId: Int,
    snapshot: WidgetSnapshot,
    provider: Class<*>,
): RemoteViews = RemoteViews(context.packageName, layoutId).apply {
    setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
    setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(context, provider))
    setOnClickPendingIntent(R.id.widget_add, addTorrentIntent(context))

    setTextViewText(R.id.widget_title, scopeLabel(context, snapshot))
    setTextViewText(R.id.widget_updated, timeOf(snapshot.updatedAt))

    if (!snapshot.hasData) {
        setTextViewText(R.id.widget_download, "↓ –")
        setTextViewText(R.id.widget_upload, "↑ –")
        setTextViewText(R.id.tile_downloading_value, "–")
        setTextViewText(R.id.tile_seeding_value, "–")
        setTextViewText(R.id.tile_stopped_value, "–")
        setTextViewText(R.id.tile_errored_value, "–")
        setTextViewText(R.id.widget_subtitle, statusMessage(context, snapshot))
        return@apply
    }

    setTextViewText(
        R.id.widget_download,
        "↓ ${formatSpeed(snapshot.download, snapshot.speedUnit)}",
    )
    setTextViewText(
        R.id.widget_upload,
        "↑ ${formatSpeed(snapshot.upload, snapshot.speedUnit)}",
    )
    setTextViewText(R.id.tile_downloading_value, snapshot.downloading.toString())
    setTextViewText(R.id.tile_seeding_value, snapshot.seeding.toString())
    setTextViewText(R.id.tile_stopped_value, snapshot.stopped.toString())
    setTextViewText(R.id.tile_errored_value, snapshot.errored.toString())
    setTextViewText(R.id.widget_subtitle, subtitle(context, snapshot))
}

/** Header of the list widget; the rows themselves come from the RemoteViewsFactory. */
internal fun torrentsViews(
    context: Context,
    appWidgetId: Int,
    snapshot: WidgetSnapshot,
    provider: Class<*>,
): RemoteViews = RemoteViews(context.packageName, R.layout.widget_torrents).apply {
    setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(context, provider))
    setOnClickPendingIntent(R.id.widget_add, addTorrentIntent(context))

    setTextViewText(R.id.widget_title, scopeLabel(context, snapshot))

    if (snapshot.hasData) {
        setTextViewText(
            R.id.widget_download,
            "↓ ${formatSpeed(snapshot.download, snapshot.speedUnit)}",
        )
        setTextViewText(
            R.id.widget_upload,
            "↑ ${formatSpeed(snapshot.upload, snapshot.speedUnit)}",
        )
        setTextViewText(R.id.widget_empty, context.getString(R.string.widget_no_active))
    } else {
        setTextViewText(R.id.widget_download, "↓ –")
        setTextViewText(R.id.widget_upload, "↑ –")
        setTextViewText(R.id.widget_empty, statusMessage(context, snapshot))
    }

    val serviceIntent = Intent(context, TorrentsWidgetService::class.java).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        // Extras are ignored when the framework compares service intents, so the id
        // has to be part of the data for each widget to get its own factory.
        data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
    }
    setRemoteAdapter(R.id.widget_list, serviceIntent)
    setEmptyView(R.id.widget_list, R.id.widget_empty)
    setPendingIntentTemplate(R.id.widget_list, rowTemplateIntent(context))
}

/** One row of the list widget. */
internal fun rowViews(context: Context, row: WidgetRow, unit: SpeedUnit): RemoteViews =
    RemoteViews(context.packageName, R.layout.widget_torrents_item).apply {
    setTextViewText(R.id.row_name, row.name)
    setProgressBar(R.id.row_progress, 100, row.progress, false)
    setTextViewText(R.id.row_meta, rowMeta(row, unit))

    setOnClickFillInIntent(
        R.id.row_root,
        Intent()
            .putExtra(WidgetLaunch.EXTRA_INSTANCE_ID, row.instanceId)
            .putExtra(WidgetLaunch.EXTRA_HASH, row.hash),
    )
}

private fun rowMeta(row: WidgetRow, unit: SpeedUnit): String = buildList {
    add("${row.progress}%")
    add(formatBytes(row.size))
    if (row.dlspeed > 0) add("↓ ${formatSpeed(row.dlspeed, unit, compact = true)}")
    if (row.upspeed > 0) add("↑ ${formatSpeed(row.upspeed, unit, compact = true)}")
    if (row.dlspeed > 0 && row.eta > 0) formatEta(row.eta).takeIf { it.isNotEmpty() }?.let(::add)
}.joinToString(" · ")

private fun scopeLabel(context: Context, snapshot: WidgetSnapshot): String = when {
    snapshot.instanceName != null -> snapshot.instanceName
    snapshot.instanceCount > 1 -> context.getString(R.string.dashboard_all_clients)
    else -> context.getString(R.string.app_name)
}

private fun subtitle(context: Context, snapshot: WidgetSnapshot): String = buildList {
    add(
        context.resources.getQuantityString(
            R.plurals.torrents_count,
            snapshot.total,
            snapshot.total,
        )
    )
    if (snapshot.totalSize > 0) add(formatBytes(snapshot.totalSize))
    snapshot.freeSpace?.let {
        add(context.getString(R.string.widget_free_space, formatBytes(it)))
    }
    // Numbers that survived a failed refresh must say so, or they read as current.
    if (snapshot.stale) {
        add(context.getString(R.string.widget_stale, timeOf(snapshot.updatedAt)))
    }
}.joinToString(" · ")

private fun statusMessage(context: Context, snapshot: WidgetSnapshot): String = when {
    !snapshot.signedIn -> context.getString(R.string.widget_not_signed_in)
    snapshot.error == WidgetDataSource.ERROR_NO_CLIENTS ->
        context.getString(R.string.torrents_no_clients_title)
    else -> context.getString(R.string.login_error_unreachable)
}

private fun timeOf(millis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))

object QuiWidgets {
    private val PROVIDERS = listOf(
        QuiWidgetProvider::class.java,
        QuiTorrentsWidgetProvider::class.java,
    )

    /**
     * Nudges every placed widget to redraw. Safe to call when none exist — providers
     * with no widgets on screen are skipped.
     */
    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        PROVIDERS.forEach { provider ->
            val ids = manager.getAppWidgetIds(ComponentName(context, provider))
            if (ids.isEmpty()) return@forEach

            context.sendBroadcast(
                Intent(context, provider).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
            )
        }
    }
}
