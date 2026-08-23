/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.sprout.core.database.entity.EntryEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
internal interface EntryDao {

    @Query(
        """
        SELECT * FROM entry
        WHERE habit_id = :habitId AND deleted_at IS NULL
        ORDER BY date ASC
        """,
    )
    fun observeForHabit(habitId: String): Flow<List<EntryEntity>>

    /**
     * Every live entry, for every habit.
     *
     * The strength score is an EMA walked from a habit's first entry, so scoring genuinely needs
     * the whole history — a windowed query would restart the EMA at zero and understate a
     * long-running habit. One query beats one per habit; a few thousand rows is nothing.
     */
    @Query("SELECT * FROM entry WHERE deleted_at IS NULL ORDER BY date ASC")
    fun observeAll(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entry WHERE habit_id = :habitId AND date = :date AND deleted_at IS NULL")
    suspend fun findOn(habitId: String, date: LocalDate): EntryEntity?

    /**
     * Includes tombstoned rows.
     *
     * Logging must reuse the id of a previously cleared day. The unique index on
     * `(habit_id, date)` means a fresh id for the same day loses to a constraint conflict that
     * `@Upsert` resolves by primary key — that is, silently not at all.
     */
    @Query("SELECT * FROM entry WHERE habit_id = :habitId AND date = :date")
    suspend fun findOnIncludingDeleted(habitId: String, date: LocalDate): EntryEntity?

    @Upsert
    suspend fun upsert(entry: EntryEntity)

    /** Un-logging a day tombstones the row rather than deleting it, so sync can propagate it. */
    @Query("UPDATE entry SET deleted_at = :at, updated_at = :at WHERE habit_id = :habitId AND date = :date")
    suspend fun softDeleteOn(habitId: String, date: LocalDate, at: Instant)
}
