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
import dev.sprout.core.model.Lapse
import dev.sprout.core.model.LapseTrigger
import java.time.Instant

@Entity(
    tableName = "lapse",
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
internal data class LapseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "habit_id") val habitId: String,
    val at: Instant,
    val triggers: Set<LapseTrigger>,
    val amount: Double?,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant?,
)

internal fun LapseEntity.toDomain(): Lapse = Lapse(
    id = id, habitId = habitId, at = at, triggers = triggers, amount = amount, note = note,
    createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt,
)

internal fun Lapse.toEntity(): LapseEntity = LapseEntity(
    id = id, habitId = habitId, at = at, triggers = triggers, amount = amount, note = note,
    createdAt = createdAt, updatedAt = updatedAt, deletedAt = deletedAt,
)
