/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.sprout.core.model.Entry
import dev.sprout.core.model.EntrySource
import dev.sprout.core.model.EntryStatus
import java.time.Instant
import java.time.LocalDate

/**
 * One logged day.
 *
 * The unique index on `(habit_id, date)` is what stops a widget tap and a notification action
 * racing into two rows for the same day. Logging is upsert, never blind insert.
 */
@Entity(
    tableName = "entry",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habit_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["habit_id", "date"], unique = true),
        Index(value = ["date"]),
    ],
)
internal data class EntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "habit_id") val habitId: String,
    val date: LocalDate,
    val status: String,
    val value: Double?,
    val note: String?,
    val source: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant?,
)

internal fun EntryEntity.toDomain(): Entry = Entry(
    id = id,
    habitId = habitId,
    date = date,
    status = EntryStatus.valueOf(status),
    value = value,
    note = note,
    source = EntrySource.valueOf(source),
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun Entry.toEntity(): EntryEntity = EntryEntity(
    id = id,
    habitId = habitId,
    date = date,
    status = status.name,
    value = value,
    note = note,
    source = source.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)
