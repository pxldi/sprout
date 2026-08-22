/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.database

import androidx.room.TypeConverter
import dev.sprout.core.model.LapseTrigger
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Stored representations are chosen to be stable and greppable, not compact:
 * dates as epoch-day, times as second-of-day, instants as epoch-millis, enums as names.
 *
 * Enum *names* rather than ordinals on purpose — reordering an enum must not silently reinterpret
 * every existing row.
 */
internal class Converters {

    @TypeConverter fun dateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()
    @TypeConverter fun epochDayToDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter fun timeToSecondOfDay(value: LocalTime?): Int? = value?.toSecondOfDay()
    @TypeConverter fun secondOfDayToTime(value: Int?): LocalTime? =
        value?.let { LocalTime.ofSecondOfDay(it.toLong()) }

    @TypeConverter fun instantToMillis(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun millisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun triggersToString(value: Set<LapseTrigger>?): String? =
        value?.map { it.name }?.sorted()?.joinToString(",")

    @TypeConverter
    fun stringToTriggers(value: String?): Set<LapseTrigger>? = value
        ?.split(',')
        ?.filter { it.isNotBlank() }
        ?.mapTo(mutableSetOf()) { LapseTrigger.valueOf(it) }
}
