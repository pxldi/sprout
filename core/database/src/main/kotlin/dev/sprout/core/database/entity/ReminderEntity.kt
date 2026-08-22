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
import dev.sprout.core.model.Reminder
import java.time.Instant
import java.time.LocalTime

@Entity(
    tableName = "reminder",
    foreignKeys = [
        ForeignKey(
            entity = HabitEntity::class,
            parentColumns = ["id"],
            childColumns = ["habit_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["habit_id"])],
)
internal data class ReminderEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "habit_id") val habitId: String,
    val time: LocalTime,
    @ColumnInfo(name = "days_mask") val daysMask: Int,
    @ColumnInfo(name = "lead_minutes") val leadMinutes: Int,
    val enabled: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant?,
)

internal fun ReminderEntity.toDomain(): Reminder = Reminder(
    id = id, habitId = habitId, time = time, daysMask = daysMask, leadMinutes = leadMinutes,
    enabled = enabled, createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt,
)

internal fun Reminder.toEntity(): ReminderEntity = ReminderEntity(
    id = id, habitId = habitId, time = time, daysMask = daysMask, leadMinutes = leadMinutes,
    enabled = enabled, createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt,
)
