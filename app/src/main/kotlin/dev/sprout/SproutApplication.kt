/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.sprout.core.database.repository.HabitRepository
import dev.sprout.core.database.repository.ReminderRepository
import dev.sprout.reminder.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

@HiltAndroidApp
class SproutApplication : Application() {

    @Inject lateinit var habits: HabitRepository

    @Inject lateinit var reminders: ReminderRepository

    @Inject lateinit var scheduler: ReminderScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Re-registers the alarm whenever the data behind it moves.
     *
     * Done by watching the repositories rather than by calling the scheduler from each screen.
     * Creating, editing, archiving and deleting all have to reschedule, and so will the widget and
     * the importer — every one of those is a place to forget. A feature module also cannot reach
     * [ReminderScheduler] without depending on `:app`, which is backwards.
     *
     * Only covers changes made while the process is alive, which is all it needs to: the alarm
     * itself survives, and everything that destroys one is handled by the system receivers.
     */
    override fun onCreate() {
        super.onCreate()
        combine(habits.observeActive(), reminders.observeEnabled()) { habits, reminders ->
            // Schedules and reminder times are the only inputs; ignore renames and reordering,
            // which would otherwise re-register the same alarm on every keystroke of an edit.
            habits.map { it.id to it.schedule } to reminders.map { it.copy(updatedAt = it.createdAt) }
        }
            .distinctUntilChanged()
            .onEach { scheduler.reschedule() }
            .launchIn(scope)
    }
}
