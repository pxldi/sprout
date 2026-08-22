/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.sprout.core.database.repository.HabitRepository
import dev.sprout.core.database.repository.ReminderRepository
import dev.sprout.core.scheduling.ReminderCalendar
import dev.sprout.core.scheduling.ReminderTarget
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps exactly one alarm registered with the system.
 *
 * One alarm, not one per habit: [ReminderCalendar] already collapses everything down to the next
 * moment the app must wake up, and the receiver reschedules on the way out. A habit per alarm
 * would multiply the wakeups the OS has to honour for no gain, and Doze budgets them.
 */
@Singleton
public class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val habits: HabitRepository,
    private val reminders: ReminderRepository,
    private val clock: Clock,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    /** Recomputes the next fire and re-registers it. Safe to call as often as you like. */
    public suspend fun reschedule() {
        val fire = ReminderCalendar.nextFire(targets(), clock.instant(), ZoneId.systemDefault())
        val intent = fireIntent(fire?.due?.map { it.habitId }.orEmpty())
        if (fire == null) {
            alarms.cancel(intent)
            return
        }
        val at = fire.at.toEpochMilli()
        if (canBeExact()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } else {
            // Denied by default from Android 14 for apps that are not clocks or calendars. A habit
            // nudge is neither, so this is the expected path rather than the degraded one — an
            // inexact window still lands, just not to the minute.
            alarms.setWindow(AlarmManager.RTC_WAKEUP, at, INEXACT_WINDOW_MILLIS, intent)
        }
    }

    /** Wakes the app again in [SNOOZE_MINUTES], for one habit only. */
    public fun snooze(habitId: String) {
        val at = clock.instant().plusSeconds(SNOOZE_MINUTES * SECONDS_PER_MINUTE).toEpochMilli()
        val intent = fireIntent(listOf(habitId), requestCode = habitId.hashCode())
        if (canBeExact()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } else {
            alarms.setWindow(AlarmManager.RTC_WAKEUP, at, INEXACT_WINDOW_MILLIS, intent)
        }
    }

    /**
     * Reminders paired with the schedule of the habit they belong to.
     *
     * Drawn from *active* habits, so archiving a habit silences it without touching its reminder
     * rows — which matters, because unarchiving has to bring them back exactly as they were.
     */
    private suspend fun targets(): List<ReminderTarget> {
        val byId = habits.observeActive().first().associateBy { it.id }
        return reminders.allEnabled().mapNotNull { reminder ->
            byId[reminder.habitId]?.let { ReminderTarget(reminder, it.schedule) }
        }
    }

    private fun canBeExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    private fun fireIntent(habitIds: List<String>, requestCode: Int = REQUEST_NEXT): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(ReminderReceiver.EXTRA_HABIT_IDS, habitIds.toTypedArray())
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            // UPDATE_CURRENT so the habit ids in a re-registered alarm actually replace the old
            // ones; without it the extras of the first-ever alarm would be reused forever.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val REQUEST_NEXT = 1
        const val INEXACT_WINDOW_MILLIS = 10 * 60 * 1000L
        const val SNOOZE_MINUTES = 15L
        const val SECONDS_PER_MINUTE = 60L
    }
}
