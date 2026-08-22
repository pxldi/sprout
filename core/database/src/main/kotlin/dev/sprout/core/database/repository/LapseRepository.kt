/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.database.repository

import dev.sprout.core.database.dao.LapseDao
import dev.sprout.core.database.entity.toDomain
import dev.sprout.core.database.entity.toEntity
import dev.sprout.core.model.Lapse
import dev.sprout.core.model.LapseTrigger
import dev.sprout.core.model.newId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant

public class LapseRepository internal constructor(
    private val dao: LapseDao,
    private val clock: Clock,
) {
    public fun observeForHabit(habitId: String): Flow<List<Lapse>> =
        dao.observeForHabit(habitId).map { rows -> rows.map { it.toDomain() } }

    public fun observeForHabitSince(habitId: String, since: Instant): Flow<List<Lapse>> =
        dao.observeForHabitSince(habitId, since).map { rows -> rows.map { it.toDomain() } }

    public suspend fun record(
        habitId: String,
        triggers: Set<LapseTrigger> = emptySet(),
        amount: Double? = null,
        note: String? = null,
        at: Instant = clock.instant(),
    ): Lapse {
        val now = clock.instant()
        val lapse = Lapse(
            id = newId(),
            habitId = habitId,
            at = at,
            triggers = triggers,
            amount = amount,
            note = note,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(lapse.toEntity())
        return lapse
    }

    public suspend fun delete(id: String) { dao.softDelete(id, clock.instant()) }
}
