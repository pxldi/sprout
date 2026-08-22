/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.scheduling

import dev.sprout.core.model.Reminder
import dev.sprout.core.model.ScheduleRule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val UTC = ZoneId.of("UTC")
private val BERLIN = ZoneId.of("Europe/Berlin")

private fun reminderAt(
    time: LocalTime,
    id: String = "r1",
    habitId: String = "h1",
    daysMask: Int = Reminder.ALL_DAYS,
    leadMinutes: Int = 0,
    enabled: Boolean = true,
) = Reminder(
    id = id,
    habitId = habitId,
    time = time,
    daysMask = daysMask,
    leadMinutes = leadMinutes,
    enabled = enabled,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)

class ReminderCalendarTest {

    /** Monday. */
    private val anchor = LocalDate.of(2026, 1, 5)
    private val eightAm = reminderAt(LocalTime.of(8, 0))
    private val daily = ScheduleRule.Daily

    private fun next(target: ReminderTarget, now: String, zone: ZoneId = UTC) =
        ReminderCalendar.nextFireFor(target, Instant.parse(now), zone)

    @Test
    fun `a time already past today moves to tomorrow`() {
        val at = next(ReminderTarget(eightAm, daily), "2026-01-05T12:00:00Z")
        assertEquals(Instant.parse("2026-01-06T08:00:00Z"), at)
    }

    @Test
    fun `a time still to come fires today`() {
        val at = next(ReminderTarget(eightAm, daily), "2026-01-05T06:00:00Z")
        assertEquals(Instant.parse("2026-01-05T08:00:00Z"), at)
    }

    @Test
    fun `the exact instant of the alarm counts as past`() {
        // Otherwise firing would reschedule to the same instant and spin.
        val at = next(ReminderTarget(eightAm, daily), "2026-01-05T08:00:00Z")
        assertEquals(Instant.parse("2026-01-06T08:00:00Z"), at)
    }

    @Test
    fun `a weekday mask skips the days it excludes`() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)
        val target = ReminderTarget(
            reminderAt(LocalTime.of(8, 0), daysMask = Reminder.maskOf(days)),
            ScheduleRule.SpecificDays(days),
        )
        assertEquals(
            Instant.parse("2026-01-08T08:00:00Z"),
            next(target, "2026-01-05T12:00:00Z"),
        )
    }

    @Test
    fun `an every-n-days habit is held to its schedule, not to its mask`() {
        // Saved with an all-days mask because no weekday pattern describes it. Going by the mask
        // alone this would nag on the 6th; the habit is not due until the 8th.
        val target = ReminderTarget(eightAm, ScheduleRule.EveryNDays(3, anchor))
        assertEquals(
            Instant.parse("2026-01-08T08:00:00Z"),
            next(target, "2026-01-05T12:00:00Z"),
        )
    }

    @Test
    fun `a weekly-target habit does remind every day`() {
        // Every day is a real chance to do a 3x/week habit, so the mask is the only constraint.
        val target = ReminderTarget(eightAm, ScheduleRule.TimesPerWeek(3))
        assertEquals(
            Instant.parse("2026-01-06T08:00:00Z"),
            next(target, "2026-01-05T12:00:00Z"),
        )
    }

    @Test
    fun `lead time can push the fire onto the previous day`() {
        val target = ReminderTarget(
            reminderAt(LocalTime.of(0, 15), leadMinutes = 30),
            daily,
        )
        // The 6th's occasion is prompted at 23:45 on the 5th.
        assertEquals(
            Instant.parse("2026-01-05T23:45:00Z"),
            next(target, "2026-01-05T12:00:00Z"),
        )
    }

    @Test
    fun `a time inside the spring-forward gap fires just after it`() {
        // 2026-03-29, Berlin jumps 02:00 to 03:00. 02:30 does not exist that day.
        val target = ReminderTarget(reminderAt(LocalTime.of(2, 30)), daily)
        val at = next(target, "2026-03-28T12:00:00Z", BERLIN)
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"), at)
        assertEquals(LocalTime.of(3, 30), at?.atZone(BERLIN)?.toLocalTime())
    }

    @Test
    fun `a disabled reminder never fires`() {
        val target = ReminderTarget(reminderAt(LocalTime.of(8, 0), enabled = false), daily)
        assertNull(next(target, "2026-01-05T06:00:00Z"))
    }

    @Test
    fun `habits sharing a minute come back as one wake-up`() {
        val targets = listOf(
            ReminderTarget(reminderAt(LocalTime.of(8, 0), id = "r1", habitId = "h1"), daily),
            ReminderTarget(reminderAt(LocalTime.of(8, 0), id = "r2", habitId = "h2"), daily),
            ReminderTarget(reminderAt(LocalTime.of(9, 0), id = "r3", habitId = "h3"), daily),
        )
        val fire = ReminderCalendar.nextFire(targets, Instant.parse("2026-01-05T06:00:00Z"), UTC)

        assertEquals(Instant.parse("2026-01-05T08:00:00Z"), fire?.at)
        assertEquals(listOf("h1", "h2"), fire?.due?.map { it.habitId })
    }

    @Test
    fun `nothing scheduled is not an alarm`() {
        assertNull(ReminderCalendar.nextFire(emptyList(), Instant.parse("2026-01-05T06:00:00Z"), UTC))
    }
}
