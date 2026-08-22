/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.habit

import dev.sprout.core.model.HabitType
import dev.sprout.core.model.Reminder
import dev.sprout.core.model.ScheduleRule
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the creation flow refuses to let through.
 *
 * The cue and the coping plan being *required* is the deliberate friction in this app — see
 * docs/02-app-design.md, "the plan is the product". If these tests ever get relaxed, that is a
 * product decision, not a cleanup.
 */
private fun reminderWith(mask: Int) = Reminder(
    habitId = "habit",
    time = LocalTime.NOON,
    daysMask = mask,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)

class HabitDraftTest {

    private val anchor = LocalDate.of(2026, 1, 5)

    @Test
    fun `a habit needs a name`() {
        assertFalse(HabitDraft().canLeave(CreationStep.WHAT))
        assertFalse(HabitDraft(name = "   ").canLeave(CreationStep.WHAT))
        assertTrue(HabitDraft(name = "Morning run").canLeave(CreationStep.WHAT))
    }

    @Test
    fun `the plan is required, and it is the only thing that is`() {
        val named = HabitDraft(name = "Morning run")

        assertFalse(named.canLeave(CreationStep.CUE))
        assertFalse(named.canLeave(CreationStep.COPING))

        // The genuinely optional steps never block, even completely untouched.
        assertTrue(named.canLeave(CreationStep.SMALLEST))
        assertTrue(named.canLeave(CreationStep.REMINDER))

        val planned = named.copy(cue = "it's 7am", copingPlan = "after dinner")
        assertTrue(planned.canLeave(CreationStep.CUE))
        assertTrue(planned.canLeave(CreationStep.COPING))
    }

    @Test
    fun `a measurable habit needs a target above zero`() {
        val counted = HabitDraft(name = "Read", type = HabitType.DO_NUMERIC)
        assertFalse(counted.canLeave(CreationStep.WHAT))
        assertFalse(counted.copy(target = "nonsense").canLeave(CreationStep.WHAT))
        assertFalse(counted.copy(target = "0").canLeave(CreationStep.WHAT))
        assertTrue(counted.copy(target = "20").canLeave(CreationStep.WHAT))
    }

    @Test
    fun `picking specific days without picking any is not a schedule`() {
        val draft = HabitDraft(name = "Gym", scheduleKind = ScheduleKind.SPECIFIC_DAYS)
        assertFalse(draft.canLeave(CreationStep.SCHEDULE))

        val withDays = draft.copy(specificDays = setOf(DayOfWeek.MONDAY))
        assertTrue(withDays.canLeave(CreationStep.SCHEDULE))
    }

    @Test
    fun `each schedule kind builds its rule`() {
        val base = HabitDraft(name = "Gym")

        assertEquals(ScheduleRule.Daily, base.scheduleRule(anchor))
        assertEquals(
            ScheduleRule.SpecificDays(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)),
            base.copy(
                scheduleKind = ScheduleKind.SPECIFIC_DAYS,
                specificDays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            ).scheduleRule(anchor),
        )
        assertEquals(
            ScheduleRule.TimesPerWeek(4),
            base.copy(scheduleKind = ScheduleKind.TIMES_PER_WEEK, timesPerWeek = 4)
                .scheduleRule(anchor),
        )
        assertEquals(
            ScheduleRule.EveryNDays(3, anchor),
            base.copy(scheduleKind = ScheduleKind.EVERY_N_DAYS, everyNDays = 3)
                .scheduleRule(anchor),
        )
    }

    @Test
    fun `a reminder is confined to the days the habit is actually scheduled`() {
        val everyDay = HabitDraft(name = "Meditate")
        assertEquals(Reminder.ALL_DAYS, everyDay.reminderDaysMask())

        val mwf = everyDay.copy(
            scheduleKind = ScheduleKind.SPECIFIC_DAYS,
            specificDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        )
        val reminder = reminderWith(mwf.reminderDaysMask())
        assertTrue(reminder.firesOn(DayOfWeek.MONDAY))
        assertFalse(reminder.firesOn(DayOfWeek.TUESDAY))
    }
}
