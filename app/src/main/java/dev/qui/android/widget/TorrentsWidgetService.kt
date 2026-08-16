/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Feeds rows to the list widget. The framework calls onDataSetChanged on a binder
 * thread and expects it to block until the data is ready, which is why runBlocking is
 * the right call here rather than a leak of the coroutine scope.
 */

package dev.qui.android.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import dagger.hilt.android.AndroidEntryPoint
import dev.qui.android.R
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class TorrentsWidgetService : RemoteViewsService() {

    @Inject lateinit var dataSource: WidgetDataSource

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        TorrentsRemoteViewsFactory(applicationContext, dataSource)
}

private class TorrentsRemoteViewsFactory(
    private val context: Context,
    private val dataSource: WidgetDataSource,
) : RemoteViewsService.RemoteViewsFactory {

    private var snapshot: WidgetSnapshot = WidgetSnapshot()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        // The provider has just loaded; inside the reuse window this returns that same
        // snapshot instead of hitting the server again.
        snapshot = runBlocking { dataSource.load() }
    }

    override fun onDestroy() = Unit

    override fun getCount(): Int = snapshot.rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val row = snapshot.rows.getOrNull(position) ?: return blankRow()
        return rowViews(context, row, snapshot.speedUnit)
    }

    /** An empty row is less jarring than the framework's stock "Loading…" placeholder. */
    override fun getLoadingView(): RemoteViews = blankRow()

    private fun blankRow() = RemoteViews(context.packageName, R.layout.widget_torrents_item)

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        snapshot.rows.getOrNull(position)?.hash?.hashCode()?.toLong() ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
