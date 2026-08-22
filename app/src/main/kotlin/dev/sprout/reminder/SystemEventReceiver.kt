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

/**
 * Everything that silently throws away a registered alarm, or changes when it should land.
 *
 * Alarms do not survive a reboot or an app update, and neither event tells the user anything —
 * reminders would simply stop, which reads as the app being broken rather than as Android having
 * done what it always does. A timezone or clock change keeps the alarm but moves the wall-clock
 * moment it was standing in for, so the answer there is to recompute rather than to restore.
 */
@AndroidEntryPoint
public class SystemEventReceiver : BroadcastReceiver() {

    @Inject public lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        goAsyncWork(this) { scheduler.reschedule() }
    }
}
