/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.model

/**
 * Every row in the database, archived and tombstoned ones included.
 *
 * Tombstones are kept so that importing a backup into a phone that has moved on cannot bring a
 * deleted habit back: the deletion is a row with a later `updatedAt`, and it wins the merge.
 */
public data class Backup(
    val habits: List<Habit>,
    val entries: List<Entry>,
    val reminders: List<Reminder>,
    val lapses: List<Lapse>,
) {
    /** Habits the user can see, for "Saved 5 habits". Tombstones are not habits to them. */
    public val liveHabitCount: Int get() = habits.count { it.deletedAt == null }

    /** Logged days the user can see. */
    public val liveEntryCount: Int get() = entries.count { it.deletedAt == null }
}
