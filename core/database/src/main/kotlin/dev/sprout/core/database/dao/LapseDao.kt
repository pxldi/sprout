/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.sprout.core.database.entity.LapseEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
internal interface LapseDao {

    @Query("SELECT * FROM lapse WHERE habit_id = :habitId AND deleted_at IS NULL ORDER BY at DESC")
    fun observeForHabit(habitId: String): Flow<List<LapseEntity>>

    @Query(
        """
        SELECT * FROM lapse
        WHERE habit_id = :habitId AND deleted_at IS NULL AND at >= :since
        ORDER BY at DESC
        """,
    )
    fun observeForHabitSince(habitId: String, since: Instant): Flow<List<LapseEntity>>

    @Upsert
    suspend fun upsert(lapse: LapseEntity)

    @Query("UPDATE lapse SET deleted_at = :at, updated_at = :at WHERE id = :id")
    suspend fun softDelete(id: String, at: Instant)
}
