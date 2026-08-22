/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.database.repository

import dev.sprout.core.database.dao.EntryDao
import dev.sprout.core.database.entity.toDomain
import dev.sprout.core.database.entity.toEntity
import dev.sprout.core.model.Entry
import dev.sprout.core.model.EntrySource
import dev.sprout.core.model.EntryStatus
import dev.sprout.core.model.newId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * Logged days.
 *
 * [log] is an upsert keyed on `(habitId, date)` that reuses the existing row's id. This matters
 * more than it looks: the same day can be logged from the app, a widget and a notification
 * action, and two of those can race. One row per habit per day is enforced by a unique index and
 * preserved here.
 *
 * Every write goes through [writes]. The lock lives here rather than in a ViewModel because a
 * notification action runs in a BroadcastReceiver that has no ViewModel to serialise against —
 * a lock any caller can sidestep is not a lock.
 */
public class EntryRepository internal constructor(
    private val dao: EntryDao,
    private val clock: Clock,
) {
    private val writes = Mutex()

    public fun observeForHabit(habitId: String): Flow<List<Entry>> =
        dao.observeForHabit(habitId).map { rows -> rows.map { it.toDomain() } }

    public fun observeForHabitBetween(
        habitId: String,
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<Entry>> =
        dao.observeForHabitBetween(habitId, from, to).map { rows -> rows.map { it.toDomain() } }

    public fun observeOn(date: LocalDate): Flow<List<Entry>> =
        dao.observeOn(date).map { rows -> rows.map { it.toDomain() } }

    /** Every live entry, grouped by habit — what the Today screen feeds to the scorer. */
    public fun observeAllByHabit(): Flow<Map<String, List<Entry>>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() }.groupBy(Entry::habitId) }

    public suspend fun find(habitId: String, date: LocalDate): Entry? =
        dao.findOn(habitId, date)?.toDomain()

    public suspend fun log(
        habitId: String,
        date: LocalDate,
        status: EntryStatus,
        value: Double? = null,
        note: String? = null,
        source: EntrySource = EntrySource.MANUAL,
    ): Entry = writes.withLock { writeLog(habitId, date, status, value, note, source) }

    /**
     * The body of [log] without the lock, so callers already holding it can reuse it.
     *
     * [writes] is not reentrant: a locked method calling another locked method deadlocks the
     * caller for good, and on a notification action that means a tap that never lands.
     */
    @Suppress("LongParameterList")
    private suspend fun writeLog(
        habitId: String,
        date: LocalDate,
        status: EntryStatus,
        value: Double? = null,
        note: String? = null,
        source: EntrySource = EntrySource.MANUAL,
    ): Entry {
        val at = now()
        // Includes tombstones: re-logging a cleared day must revive that row, not add a second.
        val existing = dao.findOnIncludingDeleted(habitId, date)
        val entry = Entry(
            id = existing?.id ?: newId(),
            habitId = habitId,
            date = date,
            status = status,
            value = value,
            note = note,
            source = source,
            createdAt = existing?.createdAt ?: at,
            updatedAt = at,
            deletedAt = null, // logging again un-deletes a previously cleared day
        )
        dao.upsert(entry.toEntity())
        return entry
    }

    /** Un-logs a day. Tombstoned, not deleted, so the change can sync. */
    public suspend fun clear(habitId: String, date: LocalDate) {
        writes.withLock { dao.softDeleteOn(habitId, date, now()) }
    }

    /**
     * Marks the day done, or un-marks it if it already is.
     *
     * Read-then-write, so it has to hold the lock across both halves. Two taps in quick
     * succession — a double tap, or the notification and the app at once — would otherwise both
     * read "not logged" and both write DONE, and the second tap would silently fail to undo the
     * first.
     */
    public suspend fun toggle(
        habitId: String,
        date: LocalDate,
        source: EntrySource = EntrySource.MANUAL,
    ) {
        writes.withLock {
            val existing = dao.findOn(habitId, date)?.toDomain()
            if (existing?.status?.isCompletion == true) {
                dao.softDeleteOn(habitId, date, now())
            } else {
                writeLog(habitId, date, EntryStatus.DONE, source = source)
            }
        }
    }

    private fun now(): Instant = clock.instant()
}
