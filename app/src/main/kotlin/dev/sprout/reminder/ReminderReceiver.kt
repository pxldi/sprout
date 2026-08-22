/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** The alarm went off: say the thing, then work out when to wake up next. */
@AndroidEntryPoint
public class ReminderReceiver : BroadcastReceiver() {

    @Inject public lateinit var notifier: ReminderNotifier

    @Inject public lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val habitIds = intent.getStringArrayExtra(EXTRA_HABIT_IDS)?.toList().orEmpty()
        // Rescheduling happens even when the list is empty. A fire with nothing in it means the
        // extras went stale, and returning early there would leave no alarm registered at all —
        // the app would go quiet until something else happened to trigger a reschedule.
        goAsyncWork(this) {
            notifier.notifyDue(habitIds)
            scheduler.reschedule()
        }
    }

    public companion object {
        public const val EXTRA_HABIT_IDS: String = "habit_ids"
    }
}
