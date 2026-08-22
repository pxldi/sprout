/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.database.repository

import dev.sprout.core.database.dao.ReminderDao
import dev.sprout.core.database.entity.toDomain
import dev.sprout.core.database.entity.toEntity
import dev.sprout.core.model.Reminder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock

public class ReminderRepository internal constructor(
    private val dao: ReminderDao,
    private val clock: Clock,
) {
    public fun observeEnabled(): Flow<List<Reminder>> =
        dao.observeEnabled().map { rows -> rows.map { it.toDomain() } }

    public fun observeForHabit(habitId: String): Flow<List<Reminder>> =
        dao.observeForHabit(habitId).map { rows -> rows.map { it.toDomain() } }

    /** Used by the boot and timezone-change receivers, which need a snapshot, not a stream. */
    public suspend fun allEnabled(): List<Reminder> = dao.allEnabled().map { it.toDomain() }

    public suspend fun save(reminder: Reminder): Reminder {
        val stamped = reminder.copy(updatedAt = clock.instant())
        dao.upsert(stamped.toEntity())
        return stamped
    }

    public suspend fun delete(id: String) { dao.softDelete(id, clock.instant()) }
}
