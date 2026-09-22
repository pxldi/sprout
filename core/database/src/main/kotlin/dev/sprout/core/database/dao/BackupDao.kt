/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.sprout.core.database.entity.EntryEntity
import dev.sprout.core.database.entity.HabitEntity
import dev.sprout.core.database.entity.LapseEntity
import dev.sprout.core.database.entity.ReminderEntity
import java.time.LocalDate

/**
 * Whole tables, for export and import.
 *
 * Unlike every other DAO, nothing here filters `deleted_at`: a backup that dropped tombstones
 * could bring a deleted habit back when imported. The fixed order keeps the file stable, so two
 * exports differ only where the data does, and a diff of two daily copies is readable.
 */
@Dao
@Suppress("TooManyFunctions") // A read, a lookup and a write for each of the four tables.
internal interface BackupDao {

    @Query("SELECT * FROM habit ORDER BY position ASC, created_at ASC, id ASC")
    suspend fun habits(): List<HabitEntity>

    @Query("SELECT * FROM entry ORDER BY habit_id ASC, date ASC")
    suspend fun entries(): List<EntryEntity>

    @Query("SELECT * FROM reminder ORDER BY habit_id ASC, created_at ASC, id ASC")
    suspend fun reminders(): List<ReminderEntity>

    @Query("SELECT * FROM lapse ORDER BY habit_id ASC, at ASC, id ASC")
    suspend fun lapses(): List<LapseEntity>

    @Query("SELECT * FROM habit WHERE id = :id")
    suspend fun habit(id: String): HabitEntity?

    /** By day, not by id: see `BackupRepository.restore` for why. */
    @Query("SELECT * FROM entry WHERE habit_id = :habitId AND date = :date")
    suspend fun entryOn(habitId: String, date: LocalDate): EntryEntity?

    @Query("SELECT * FROM reminder WHERE id = :id")
    suspend fun reminder(id: String): ReminderEntity?

    @Query("SELECT * FROM lapse WHERE id = :id")
    suspend fun lapse(id: String): LapseEntity?

    @Upsert suspend fun upsertHabit(habit: HabitEntity)

    @Upsert suspend fun upsertEntry(entry: EntryEntity)

    @Upsert suspend fun upsertReminder(reminder: ReminderEntity)

    @Upsert suspend fun upsertLapse(lapse: LapseEntity)
}
