/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.database.repository

import dev.sprout.core.database.dao.HabitDao
import dev.sprout.core.database.entity.toDomain
import dev.sprout.core.database.entity.toEntity
import dev.sprout.core.model.Habit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant

/**
 * Habits, as domain objects.
 *
 * Callers never see Room types. `updated_at` is stamped here rather than by callers, so no code
 * path can forget it — a row with a stale `updated_at` would lose a sync merge it should win.
 */
public class HabitRepository internal constructor(
    private val dao: HabitDao,
    private val clock: Clock,
) {
    public fun observeActive(): Flow<List<Habit>> =
        dao.observeActive().map { rows -> rows.map { it.toDomain() } }

    public fun observeAll(): Flow<List<Habit>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    public fun observe(id: String): Flow<Habit?> =
        dao.observeById(id).map { it?.toDomain() }

    public suspend fun find(id: String): Habit? = dao.findById(id)?.toDomain()

    /** Creates or updates. Returns the stored habit, including the stamped [Habit.updatedAt]. */
    public suspend fun save(habit: Habit): Habit {
        val stamped = habit.copy(updatedAt = now())
        dao.upsert(stamped.toEntity())
        return stamped
    }

    public suspend fun archive(id: String) { dao.archive(id, now()) }

    public suspend fun unarchive(id: String) { dao.unarchive(id, now()) }

    /** Tombstones the habit. Its entries cascade. Nothing is physically removed. */
    public suspend fun delete(id: String) { dao.softDelete(id, now()) }

    public suspend fun reorder(idsInOrder: List<String>) {
        val at = now()
        idsInOrder.forEachIndexed { index, id -> dao.setPosition(id, index, at) }
    }

    private fun now(): Instant = clock.instant()
}
