/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.database.repository

import androidx.room.withTransaction
import dev.sprout.core.database.SproutDatabase
import dev.sprout.core.database.dao.BackupDao
import dev.sprout.core.database.entity.toDomain
import dev.sprout.core.database.entity.toEntity
import dev.sprout.core.model.Backup
import dev.sprout.core.model.Entry
import dev.sprout.core.model.Habit
import java.time.Instant

/**
 * What an import wrote. [habits] and [entries] count rows the user can see; [rows] counts every
 * row, tombstones and reminders included, so a file that only deletes something is not "nothing".
 */
public data class RestoreCounts(val habits: Int, val entries: Int, val rows: Int) {
    public val isEmpty: Boolean get() = rows == 0
}

/**
 * Reads every table for export, and merges a backup back in.
 *
 * An import merges instead of replacing, so importing an old file can never lose anything
 * logged since. Each row from the file is written only if the phone has no such row or has an
 * older version of it, by `updated_at`. Restoring into a wiped app therefore writes everything,
 * and importing the same file twice writes nothing the second time.
 *
 * Timestamps are kept as they are in the file. This is the one write path that does not stamp
 * `updated_at`: a restored row was last changed when the file says, and stamping it now would
 * let a stale row win the next merge.
 */
public class BackupRepository internal constructor(
    private val db: SproutDatabase,
    private val dao: BackupDao,
    private val entries: EntryRepository,
) {
    public suspend fun snapshot(): Backup = db.withTransaction {
        Backup(
            habits = dao.habits().map { it.toDomain() },
            entries = dao.entries().map { it.toDomain() },
            reminders = dao.reminders().map { it.toDomain() },
            lapses = dao.lapses().map { it.toDomain() },
        )
    }

    /**
     * All or nothing. Habits go in first because the other tables point at them; a row whose
     * habit is in neither the file nor the phone fails the foreign key and rolls back the lot.
     *
     * Holds the entry write lock for the whole transaction, so a notification action cannot
     * log a day in the middle of it.
     */
    public suspend fun restore(backup: Backup): RestoreCounts = entries.withWriteLock {
        db.withTransaction {
            val habits = restoreHabits(backup.habits)
            val days = restoreEntries(backup.entries)
            val reminders = backup.reminders.filter { isNewer(it.updatedAt, dao.reminder(it.id)?.updatedAt) }
            reminders.forEach { dao.upsertReminder(it.toEntity()) }
            val lapses = backup.lapses.filter { isNewer(it.updatedAt, dao.lapse(it.id)?.updatedAt) }
            lapses.forEach { dao.upsertLapse(it.toEntity()) }
            RestoreCounts(
                habits = habits.count { it.deletedAt == null },
                entries = days.count { it.deletedAt == null },
                rows = habits.size + days.size + reminders.size + lapses.size,
            )
        }
    }

    /** Returns the habits written. */
    private suspend fun restoreHabits(habits: List<Habit>): List<Habit> {
        val written = habits.filter { isNewer(it.updatedAt, dao.habit(it.id)?.updatedAt) }
        written.forEach { dao.upsertHabit(it.toEntity()) }
        return written
    }

    /**
     * Matched by day, not by id. A day logged on this phone before the import has its own id,
     * and the unique index allows one row per habit per day, so the newer of the two is written
     * under the id already here. Returns the entries written.
     */
    private suspend fun restoreEntries(entries: List<Entry>): List<Entry> {
        val written = mutableListOf<Entry>()
        for (entry in entries) {
            val here = dao.entryOn(entry.habitId, entry.date)
            if (isNewer(entry.updatedAt, here?.updatedAt)) {
                dao.upsertEntry(entry.copy(id = here?.id ?: entry.id).toEntity())
                written += entry
            }
        }
        return written
    }

    /** A tie keeps the phone's row: the same edit, or one the file cannot prove is later. */
    private fun isNewer(incoming: Instant, here: Instant?): Boolean = here == null || incoming > here
}
