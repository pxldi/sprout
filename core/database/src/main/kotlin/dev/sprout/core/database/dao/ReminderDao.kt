/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.sprout.core.database.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
internal interface ReminderDao {

    @Query("SELECT * FROM reminder WHERE deleted_at IS NULL AND enabled = 1")
    fun observeEnabled(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder WHERE habit_id = :habitId AND deleted_at IS NULL")
    fun observeForHabit(habitId: String): Flow<List<ReminderEntity>>

    /** Rescheduling after boot or a timezone change needs a plain list, not a stream. */
    @Query("SELECT * FROM reminder WHERE deleted_at IS NULL AND enabled = 1")
    suspend fun allEnabled(): List<ReminderEntity>

    @Upsert
    suspend fun upsert(reminder: ReminderEntity)

    @Query("UPDATE reminder SET deleted_at = :at, updated_at = :at WHERE id = :id")
    suspend fun softDelete(id: String, at: Instant)
}
