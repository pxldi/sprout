/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * The JSON backup file.
 *
 * The file is the user's copy of their data, so its shape is written out field by field in the
 * records below instead of derived from the domain classes. A renamed Kotlin property must not
 * change the file, and a file written today must import in every later version. The golden
 * fixture in the tests pins the exact bytes.
 *
 * Instants, dates and times are ISO-8601 strings and schedules are [ScheduleRuleCodec] strings,
 * so the file reads without the app.
 *
 * Bump [VERSION] when a field is added whose loss would matter, such as a privacy flag. Older
 * apps refuse a newer file instead of importing it without that field.
 */
public object BackupCodec {

    public const val FORMAT: String = "sprout-backup"
    public const val VERSION: Int = 2

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    public fun encode(backup: Backup, exportedAt: Instant): String = json.encodeToString(
        BackupFile(
            format = FORMAT,
            version = VERSION,
            exportedAt = exportedAt.toString(),
            habits = backup.habits.map { it.toRecord() },
            entries = backup.entries.map { it.toRecord() },
            reminders = backup.reminders.map { it.toRecord() },
            lapses = backup.lapses.map { it.toRecord() },
        ),
    )

    /** @throws BackupFormatException if [text] is not a backup this version can read. */
    public fun decode(text: String): Backup {
        if (readVersion(text) > VERSION) throw BackupFormatException(BackupFormatException.Reason.NEWER_VERSION)
        return readRows(text)
    }

    /** Read before the rows, so a later version's file is refused instead of called damaged. */
    private fun readVersion(text: String): Int {
        // SerializationException is an IllegalArgumentException, so this covers text that is
        // not JSON as well as JSON that is not an object.
        val header = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException(BackupFormatException.Reason.NOT_A_BACKUP, e)
        }
        val format = (header[FORMAT_KEY] as? JsonPrimitive)?.contentOrNull
        val version = (header[VERSION_KEY] as? JsonPrimitive)?.intOrNull
        if (format != FORMAT || version == null) throw BackupFormatException(BackupFormatException.Reason.NOT_A_BACKUP)
        return version
    }

    private fun readRows(text: String): Backup = try {
        val file = json.decodeFromString<BackupFile>(text)
        Backup(
            habits = file.habits.map { it.toDomain() },
            entries = file.entries.map { it.toDomain() },
            reminders = file.reminders.map { it.toDomain() },
            lapses = file.lapses.map { it.toDomain() },
        )
    } catch (e: IllegalArgumentException) {
        // Missing or unknown fields, unknown enum names and malformed schedule strings.
        throw BackupFormatException(BackupFormatException.Reason.DAMAGED, e)
    } catch (e: DateTimeException) {
        // Malformed dates and times, and day numbers outside 1 to 7.
        throw BackupFormatException(BackupFormatException.Reason.DAMAGED, e)
    }

    private const val FORMAT_KEY = "format"
    private const val VERSION_KEY = "version"
}

public class BackupFormatException(
    public val reason: Reason,
    cause: Throwable? = null,
) : Exception("Backup not readable: $reason", cause) {
    public enum class Reason {
        /** Not JSON, or JSON that is not a Sprout backup. */
        NOT_A_BACKUP,

        /** Written by a later version of the app, in a format this one does not know. */
        NEWER_VERSION,

        /** A Sprout backup with a field this version cannot read. */
        DAMAGED,
    }
}

@Serializable
private data class BackupFile(
    val format: String,
    val version: Int,
    val exportedAt: String,
    val habits: List<HabitRecord>,
    val entries: List<EntryRecord>,
    val reminders: List<ReminderRecord>,
    val lapses: List<LapseRecord>,
)

@Serializable
private data class HabitRecord(
    val id: String,
    val name: String,
    val type: String,
    val schedule: String,
    val identityPhrase: String? = null,
    val minimumVersion: String? = null,
    val cue: String? = null,
    val copingPlan: String? = null,
    val anchorHabitId: String? = null,
    val bundleText: String? = null,
    val unit: String? = null,
    val target: Double? = null,
    val ceiling: Double? = null,
    val colorArgb: Int? = null,
    val icon: String? = null,
    val position: Int = 0,
    /** Version 2. A version 1 file has neither field, and its habits import as public. */
    @SerialName("private") val isPrivate: Boolean = false,
    val alias: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val archivedAt: String? = null,
    val deletedAt: String? = null,
)

@Serializable
private data class EntryRecord(
    val id: String,
    val habitId: String,
    val date: String,
    val status: String,
    val value: Double? = null,
    val note: String? = null,
    val source: String,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
)

@Serializable
private data class ReminderRecord(
    val id: String,
    val habitId: String,
    val time: String,
    /** ISO day numbers, Monday = 1, as in the `days:` schedule. */
    val days: List<Int>,
    val leadMinutes: Int = 0,
    val enabled: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
)

@Serializable
private data class LapseRecord(
    val id: String,
    val habitId: String,
    val at: String,
    val triggers: List<String> = emptyList(),
    val amount: Double? = null,
    val note: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
)

private fun Habit.toRecord() = HabitRecord(
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
    isPrivate = isPrivate,
    alias = alias,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    archivedAt = archivedAt?.toString(),
    deletedAt = deletedAt?.toString(),
)

private fun HabitRecord.toDomain() = Habit(
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
    isPrivate = isPrivate,
    alias = alias,
    createdAt = Instant.parse(createdAt),
    updatedAt = Instant.parse(updatedAt),
    archivedAt = archivedAt?.let(Instant::parse),
    deletedAt = deletedAt?.let(Instant::parse),
)

private fun Entry.toRecord() = EntryRecord(
    id = id,
    habitId = habitId,
    date = date.toString(),
    status = status.name,
    value = value,
    note = note,
    source = source.name,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    deletedAt = deletedAt?.toString(),
)

private fun EntryRecord.toDomain() = Entry(
    id = id,
    habitId = habitId,
    date = LocalDate.parse(date),
    status = EntryStatus.valueOf(status),
    value = value,
    note = note,
    source = EntrySource.valueOf(source),
    createdAt = Instant.parse(createdAt),
    updatedAt = Instant.parse(updatedAt),
    deletedAt = deletedAt?.let(Instant::parse),
)

private fun Reminder.toRecord() = ReminderRecord(
    id = id,
    habitId = habitId,
    time = time.toString(),
    days = DayOfWeek.entries.filter { firesOn(it) }.map { it.value },
    leadMinutes = leadMinutes,
    enabled = enabled,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    deletedAt = deletedAt?.toString(),
)

private fun ReminderRecord.toDomain() = Reminder(
    id = id,
    habitId = habitId,
    time = LocalTime.parse(time),
    daysMask = Reminder.maskOf(days.map(DayOfWeek::of).toSet()),
    leadMinutes = leadMinutes,
    enabled = enabled,
    createdAt = Instant.parse(createdAt),
    updatedAt = Instant.parse(updatedAt),
    deletedAt = deletedAt?.let(Instant::parse),
)

private fun Lapse.toRecord() = LapseRecord(
    id = id,
    habitId = habitId,
    at = at.toString(),
    triggers = triggers.map { it.name }.sorted(),
    amount = amount,
    note = note,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    deletedAt = deletedAt?.toString(),
)

private fun LapseRecord.toDomain() = Lapse(
    id = id,
    habitId = habitId,
    at = Instant.parse(at),
    triggers = triggers.mapTo(mutableSetOf(), LapseTrigger::valueOf),
    amount = amount,
    note = note,
    createdAt = Instant.parse(createdAt),
    updatedAt = Instant.parse(updatedAt),
    deletedAt = deletedAt?.let(Instant::parse),
)
