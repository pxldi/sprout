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
 */
public class EntryRepository internal constructor(
    private val dao: EntryDao,
    private val clock: Clock,
) {
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
        dao.softDeleteOn(habitId, date, now())
    }

    private fun now(): Instant = clock.instant()
}
