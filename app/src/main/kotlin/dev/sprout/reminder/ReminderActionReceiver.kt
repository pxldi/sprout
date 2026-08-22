/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.sprout.core.database.repository.EntryRepository
import dev.sprout.core.model.EntrySource
import dev.sprout.core.model.EntryStatus
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Done, Skip and Snooze from the notification.
 *
 * The writes go through [EntryRepository] rather than any ViewModel, which is the whole reason
 * the write lock lives down there: this runs in a receiver with no UI attached to it.
 */
@AndroidEntryPoint
public class ReminderActionReceiver : BroadcastReceiver() {

    @Inject public lateinit var entries: EntryRepository

    @Inject public lateinit var scheduler: ReminderScheduler

    @Inject public lateinit var clock: Clock

    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getStringExtra(ReminderNotifier.EXTRA_HABIT_ID) ?: return
        val action = intent.action ?: return
        NotificationManagerCompat.from(context).cancel(habitId.hashCode())

        goAsyncWork(this) {
            val today = LocalDate.now(clock)
            when (action) {
                ReminderNotifier.ACTION_DONE ->
                    entries.log(habitId, today, EntryStatus.DONE, source = EntrySource.NOTIFICATION)

                ReminderNotifier.ACTION_SKIP ->
                    entries.log(habitId, today, EntryStatus.SKIP, source = EntrySource.NOTIFICATION)

                // Deliberately not a completion and not a skip: snoozing says "later", and the day
                // stays unlogged so the rest of the app keeps treating it as still open.
                ReminderNotifier.ACTION_SNOOZE -> scheduler.snooze(habitId)
            }
        }
    }
}
