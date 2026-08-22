/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.sprout.core.model.Habit
import dev.sprout.core.model.HabitType
import dev.sprout.core.model.ScheduleRuleCodec
import java.time.Instant

@Entity(tableName = "habit")
internal data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val schedule: String,
    @ColumnInfo(name = "identity_phrase") val identityPhrase: String?,
    @ColumnInfo(name = "minimum_version") val minimumVersion: String?,
    @ColumnInfo(name = "cue_text") val cue: String?,
    @ColumnInfo(name = "coping_plan") val copingPlan: String?,
    @ColumnInfo(name = "anchor_habit_id") val anchorHabitId: String?,
    @ColumnInfo(name = "bundle_text") val bundleText: String?,
    val unit: String?,
    val target: Double?,
    val ceiling: Double?,
    @ColumnInfo(name = "color_argb") val colorArgb: Int?,
    val icon: String?,
    val position: Int,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "archived_at") val archivedAt: Instant?,
    @ColumnInfo(name = "deleted_at") val deletedAt: Instant?,
)

internal fun HabitEntity.toDomain(): Habit = Habit(
    id = id,
    name = name,
    type = HabitType.valueOf(type),
    schedule = ScheduleRuleCodec.decode(schedule),
    identityPhrase = identityPhrase,
    minimumVersion = minimumVersion,
    cue = cue,
    copingPlan = copingPlan,
    anchorHabitId = anchorHabitId,
    bundleText = bundleText,
    unit = unit,
    target = target,
    ceiling = ceiling,
    colorArgb = colorArgb,
    icon = icon,
    position = position,
    createdAt = createdAt,
    updatedAt = updatedAt,
    archivedAt = archivedAt,
    deletedAt = deletedAt,
)

internal fun Habit.toEntity(): HabitEntity = HabitEntity(
    id = id,
    name = name,
    type = type.name,
    schedule = ScheduleRuleCodec.encode(schedule),
    identityPhrase = identityPhrase,
    minimumVersion = minimumVersion,
    cue = cue,
    copingPlan = copingPlan,
    anchorHabitId = anchorHabitId,
    bundleText = bundleText,
    unit = unit,
    target = target,
    ceiling = ceiling,
    colorArgb = colorArgb,
    icon = icon,
    position = position,
    createdAt = createdAt,
    updatedAt = updatedAt,
    archivedAt = archivedAt,
    deletedAt = deletedAt,
)
