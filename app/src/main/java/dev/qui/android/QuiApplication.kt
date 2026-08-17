/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 */

package dev.qui.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.qui.android.data.AppPreferencesStore
import dev.qui.android.widget.WidgetRefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class QuiApplication : Application() {

    @Inject lateinit var prefsStore: AppPreferencesStore

    override fun onCreate() {
        super.onCreate()

        // WorkManager keeps a unique schedule of its own, so re-declaring it on every
        // start is cheap and is also how a changed interval takes effect.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            prefsStore.snapshot
                .map { it.widgetRefreshMinutes }
                .distinctUntilChanged()
                .collect { minutes ->
                    // Nothing to refresh for while no widget is on a home screen.
                    val placed = WidgetRefreshScheduler.anyWidgetPlaced(this@QuiApplication)
                    WidgetRefreshScheduler.schedule(
                        this@QuiApplication,
                        if (placed) minutes else 0,
                    )
                }
        }
    }
}
