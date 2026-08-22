/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.sprout.core.database.entity.HabitEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Every read filters `deleted_at IS NULL`. Rows are tombstoned rather than removed so that an
 * offline device can learn about a deletion it never saw happen.
 */
@Dao
internal interface HabitDao {

    @Query(
        """
        SELECT * FROM habit
        WHERE deleted_at IS NULL AND archived_at IS NULL
        ORDER BY position ASC, created_at ASC
        """,
    )
    fun observeActive(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit WHERE deleted_at IS NULL ORDER BY position ASC, created_at ASC")
    fun observeAll(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit WHERE id = :id AND deleted_at IS NULL")
    fun observeById(id: String): Flow<HabitEntity?>

    @Query("SELECT * FROM habit WHERE id = :id AND deleted_at IS NULL")
    suspend fun findById(id: String): HabitEntity?

    @Upsert
    suspend fun upsert(habit: HabitEntity)

    @Query("UPDATE habit SET deleted_at = :at, updated_at = :at WHERE id = :id")
    suspend fun softDelete(id: String, at: Instant)

    @Query("UPDATE habit SET archived_at = :at, updated_at = :at WHERE id = :id")
    suspend fun archive(id: String, at: Instant)

    @Query("UPDATE habit SET archived_at = NULL, updated_at = :at WHERE id = :id")
    suspend fun unarchive(id: String, at: Instant)

    @Query("UPDATE habit SET position = :position, updated_at = :at WHERE id = :id")
    suspend fun setPosition(id: String, position: Int, at: Instant)
}
