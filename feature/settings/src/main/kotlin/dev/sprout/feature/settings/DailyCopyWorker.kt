/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.settings

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Writes the daily copy.
 *
 * Reaches [Backups] through a Hilt entry point instead of `@HiltWorker`, which would need
 * another dependency and a custom WorkManager initializer for one worker.
 */
public class DailyCopyWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val backups = EntryPointAccessors
            .fromApplication(applicationContext, DailyCopyEntryPoint::class.java)
            .backups()
        // A failed run is recorded for Settings to show. The periodic work keeps its schedule
        // either way, so tomorrow tries again.
        return when (backups.copyNow()) {
            CopyResult.NO_FOLDER, CopyResult.SAVED -> Result.success()
            CopyResult.FAILED -> Result.failure()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
public interface DailyCopyEntryPoint {
    public fun backups(): Backups
}

/** Keeps the periodic work in step with the setting. Called by the application on each change. */
public object DailyCopySchedule {

    private const val WORK_NAME = "daily-copy"

    public fun apply(context: Context, on: Boolean) {
        val work = WorkManager.getInstance(context)
        if (!on) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        // The first run is a day out: choosing the folder writes today's copy straight away.
        // KEEP, so an app start does not push tomorrow's copy back by another day.
        val request = PeriodicWorkRequestBuilder<DailyCopyWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(1, TimeUnit.DAYS)
            .build()
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
