/*
 * Copyright (c) 2026 qui-android contributors
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * Widget refreshing, moved out of the broadcast receivers.
 *
 * A receiver has roughly ten seconds before the system counts it as hung, and the
 * process it wakes may be starting cold: Hilt graph, DataStore read, DNS, TLS
 * handshake, then the request. On a phone that has not opened the app in a day that
 * budget is not enough, which is how a perfectly reachable server ends up reported as
 * unreachable with a refresh button that never helps.
 *
 * WorkManager has no such deadline, waits for connectivity instead of failing without
 * it, and retries with backoff. The receivers now only paint a "refreshing" state and
 * hand the work over.
 */

package dev.qui.android.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.qui.android.data.AppPreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Hilt cannot inject a Worker without the extra hilt-work artifact, and one dependency
 * does not justify it.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface WidgetEntryPoint {
    fun widgetDataSource(): WidgetDataSource
    fun preferences(): AppPreferencesStore
}

class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dataSource = EntryPointAccessors
            .fromApplication(applicationContext, WidgetEntryPoint::class.java)
            .widgetDataSource()

        val snapshot = dataSource.load(force = true)

        // One bad attempt is usually a phone that has just woken up, so the first
        // failure is retried quietly rather than painted over good numbers.
        if (!snapshot.hasData && runAttemptCount < RETRY_ATTEMPTS) {
            return Result.retry()
        }

        QuiWidgets.draw(applicationContext, snapshot)
        return Result.success()
    }

    private companion object {
        const val RETRY_ATTEMPTS = 2
    }
}

object WidgetRefreshScheduler {
    private const val PERIODIC_WORK = "qui-widget-refresh"
    private const val ONE_SHOT_WORK = "qui-widget-refresh-now"

    /**
     * The floor WorkManager enforces. The platform's own updatePeriodMillis stops at
     * thirty, which is why the widgets no longer use it.
     */
    const val MINIMUM_MINUTES = 15

    private val networkRequired = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Zero turns the schedule off; the refresh button and the app still update. */
    fun schedule(context: Context, minutes: Int) {
        val manager = WorkManager.getInstance(context)
        if (minutes <= 0) {
            manager.cancelUniqueWork(PERIODIC_WORK)
            return
        }

        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
            minutes.coerceAtLeast(MINIMUM_MINUTES).toLong(),
            TimeUnit.MINUTES,
        )
            .setConstraints(networkRequired)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()

        // UPDATE rather than REPLACE so changing the interval does not reset the timer
        // and cost an extra immediate run.
        manager.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /** The refresh button, and the app pushing numbers it already has. */
    fun refreshNow(context: Context) {
        val builder = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setConstraints(networkRequired)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)

        // Expedited work below Android 12 is run as a foreground service, which means
        // WorkManager asks the worker for a notification to show — and a widget
        // refresh has no business posting one. Above 12 it is free, with the quota
        // fallback keeping it ordinary work rather than throwing once the budget is
        // spent.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }

        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_SHOT_WORK,
            ExistingWorkPolicy.REPLACE,
            builder.build(),
        )
    }

    /** True while at least one widget is on a home screen. */
    fun anyWidgetPlaced(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return QuiWidgets.PROVIDERS.any {
            manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty()
        }
    }

    /**
     * Reconciles the schedule with reality: the interval from Settings while a widget
     * is on a home screen, nothing once the last one is removed. Called when widgets
     * come and go, since polling for a widget nobody has is pure battery.
     */
    fun sync(context: Context) {
        val prefs = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .preferences()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val minutes = if (anyWidgetPlaced(context)) {
                prefs.snapshot.first().widgetRefreshMinutes
            } else {
                0
            }
            schedule(context, minutes)
        }
    }
}
