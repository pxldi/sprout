/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.sprout.MainActivity
import dev.sprout.core.database.repository.EntryRepository
import dev.sprout.core.database.repository.HabitRepository
import dev.sprout.core.model.Habit
import dev.sprout.core.ui.R
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the reminder itself.
 *
 * The one rule this file exists to enforce: a habit already logged today is not reminded about.
 * That check has to happen here rather than when the alarm is set, because the user may well log
 * the habit in the hours between the two — and being nagged about something you have already done
 * is the fastest way to teach someone to swipe the app's notifications away unread.
 */
@Singleton
public class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val habits: HabitRepository,
    private val entries: EntryRepository,
    private val clock: Clock,
) {
    public suspend fun notifyDue(habitIds: List<String>) {
        if (!canPost()) return
        ensureChannel()
        val today = LocalDate.now(clock)
        habitIds.forEach { id ->
            val habit = habits.find(id) ?: return@forEach
            if (entries.find(id, today) != null) return@forEach
            post(habit)
        }
    }

    private fun post(habit: Habit) {
        val note = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(habit.name)
            .setContentText(habit.minimumVersion ?: context.getString(R.string.notify_prompt))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, context.getString(R.string.notify_done), action(habit.id, ACTION_DONE))
            .addAction(0, context.getString(R.string.notify_skip), action(habit.id, ACTION_SKIP))
            .addAction(0, context.getString(R.string.notify_snooze), action(habit.id, ACTION_SNOOZE))
            .build()
        NotificationManagerCompat.from(context).notify(habit.id.hashCode(), note)
    }

    /**
     * Tapping the body opens the app directly.
     *
     * It has to be an Activity [PendingIntent]: since Android 12 a notification may not route
     * through a receiver that then starts an activity, and a trampoline is silently dropped.
     */
    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(habitId: String, action: String): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_HABIT_ID, habitId)
        return PendingIntent.getBroadcast(
            context,
            (habitId + action).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * The permission only exists from Android 13; below that, posting is always allowed.
     *
     * Checked rather than assumed because the request is made in context — when the first
     * reminder is saved — so the app can be running with reminders configured and the permission
     * refused, and posting then would throw.
     */
    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notify_channel_reminders),
            // Not HIGH: a habit nudge that takes over the screen is a nag, and the design rules
            // say this app never raises its voice.
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notify_channel_reminders_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    public companion object {
        public const val CHANNEL_ID: String = "reminders"
        public const val EXTRA_HABIT_ID: String = "habit_id"
        public const val ACTION_DONE: String = "dev.sprout.action.DONE"
        public const val ACTION_SKIP: String = "dev.sprout.action.SKIP"
        public const val ACTION_SNOOZE: String = "dev.sprout.action.SNOOZE"
    }
}
