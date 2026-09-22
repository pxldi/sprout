/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BackupCodecTest {

    @Test
    fun `the committed version 1 file decodes to the known rows`() {
        assertEquals(sample, BackupCodec.decode(fixture("backup-v1.json")))
    }

    /**
     * Pins the exact bytes. A renamed field or a changed date format fails here, before any
     * backup written by an earlier build stops importing.
     */
    @Test
    fun `encoding the known rows gives the committed file byte for byte`() {
        assertEquals(fixture("backup-v1.json").trimEnd('\n'), BackupCodec.encode(sample, exportedAt))
    }

    @Test
    fun `every row survives a round trip`() {
        assertEquals(sample, BackupCodec.decode(BackupCodec.encode(sample, exportedAt)))
    }

    @Test
    fun `a reminder keeps exactly its days`() {
        val weekdays = Reminder.maskOf(
            setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
        )
        val decoded = BackupCodec.decode(BackupCodec.encode(sample, exportedAt)).reminders.single()
        assertEquals(weekdays, decoded.daysMask)
    }

    @Test
    fun `text that is not JSON is not a backup`() {
        assertReason(BackupFormatException.Reason.NOT_A_BACKUP, "habits,entries\nread,1")
    }

    @Test
    fun `JSON that is not an object is not a backup`() {
        assertReason(BackupFormatException.Reason.NOT_A_BACKUP, "[1, 2, 3]")
    }

    @Test
    fun `JSON from another app is not a backup`() {
        assertReason(BackupFormatException.Reason.NOT_A_BACKUP, """{"format": "loop", "version": 1}""")
    }

    @Test
    fun `a format field that is not text is not a backup`() {
        assertReason(BackupFormatException.Reason.NOT_A_BACKUP, """{"format": {}, "version": 1}""")
    }

    @Test
    fun `a file from a later version is refused, not half imported`() {
        val later = fixture("backup-v1.json").replace("\"version\": 1", "\"version\": 2")
        assertReason(BackupFormatException.Reason.NEWER_VERSION, later)
    }

    @Test
    fun `a status this version does not know marks the file as damaged`() {
        val odd = fixture("backup-v1.json").replace("\"DONE_MIN\"", "\"HALF_DONE\"")
        assertReason(BackupFormatException.Reason.DAMAGED, odd)
    }

    @Test
    fun `a field this version does not know marks the file as damaged`() {
        val odd = fixture("backup-v1.json").replace("\"position\": 0,", "\"position\": 0, \"mood\": 3,")
        assertReason(BackupFormatException.Reason.DAMAGED, odd)
    }

    @Test
    fun `live counts leave out tombstones`() {
        assertEquals(2, sample.liveHabitCount)
        assertEquals(2, sample.liveEntryCount)
    }

    private fun assertReason(reason: BackupFormatException.Reason, text: String) {
        val e = assertFailsWith<BackupFormatException> { BackupCodec.decode(text) }
        assertEquals(reason, e.reason)
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader.getResource(name)) { "missing fixture $name" }.readText()
}

private val exportedAt: Instant = Instant.parse("2026-09-22T06:00:00Z")
private val t0: Instant = Instant.parse("2026-09-01T07:00:00Z")
private val t1: Instant = Instant.parse("2026-09-10T18:30:00Z")

private const val READ = "5b0e6c1a-0000-4000-8000-000000000001"
private const val NO_ALCOHOL = "5b0e6c1a-0000-4000-8000-000000000002"
private const val DELETED = "5b0e6c1a-0000-4000-8000-000000000003"

internal val sample = Backup(
    habits = listOf(
        Habit(
            id = READ,
            name = "Read",
            type = HabitType.DO_BOOL,
            schedule = ScheduleRule.SpecificDays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)),
            identityPhrase = "I'm a reader",
            minimumVersion = "One page",
            cue = "I've poured the first coffee",
            copingPlan = "I'll read one page before bed",
            unit = "pages",
            target = 20.0,
            colorArgb = 0xFF4C7A3D.toInt(),
            icon = "book",
            position = 0,
            createdAt = t0,
            updatedAt = t1,
        ),
        Habit(
            id = NO_ALCOHOL,
            name = "No alcohol",
            type = HabitType.AVOID,
            schedule = ScheduleRule.Daily,
            position = 1,
            createdAt = t0,
            updatedAt = t1,
            archivedAt = t1,
        ),
        Habit(
            id = DELETED,
            name = "Stretch",
            type = HabitType.DO_BOOL,
            schedule = ScheduleRule.EveryNDays(n = 3, anchor = LocalDate.of(2026, 9, 1)),
            position = 2,
            createdAt = t0,
            updatedAt = t1,
            deletedAt = t1,
        ),
    ),
    entries = listOf(
        Entry(
            id = "e1",
            habitId = READ,
            date = LocalDate.of(2026, 9, 3),
            status = EntryStatus.DONE,
            value = 24.0,
            note = "Finished the chapter on the train.",
            createdAt = t0,
            updatedAt = t0,
        ),
        Entry(
            id = "e2",
            habitId = READ,
            date = LocalDate.of(2026, 9, 7),
            status = EntryStatus.DONE_MIN,
            source = EntrySource.NOTIFICATION,
            createdAt = t1,
            updatedAt = t1,
        ),
        Entry(
            id = "e3",
            habitId = READ,
            date = LocalDate.of(2026, 9, 10),
            status = EntryStatus.SKIP,
            createdAt = t1,
            updatedAt = t1,
            deletedAt = t1,
        ),
    ),
    reminders = listOf(
        Reminder(
            id = "r1",
            habitId = READ,
            time = LocalTime.of(7, 30),
            daysMask = Reminder.maskOf(
                setOf(
                    DayOfWeek.MONDAY,
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY,
                    DayOfWeek.FRIDAY,
                ),
            ),
            leadMinutes = 30,
            enabled = false,
            createdAt = t0,
            updatedAt = t1,
        ),
    ),
    lapses = listOf(
        Lapse(
            id = "l1",
            habitId = NO_ALCOHOL,
            at = Instant.parse("2026-09-05T21:15:00Z"),
            triggers = setOf(LapseTrigger.STRESS, LapseTrigger.SOCIAL),
            note = "Work dinner.",
            createdAt = t1,
            updatedAt = t1,
        ),
    ),
)
