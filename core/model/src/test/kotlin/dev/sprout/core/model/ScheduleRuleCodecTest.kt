/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScheduleRuleCodecTest {

    private val all = listOf(
        ScheduleRule.Daily,
        ScheduleRule.SpecificDays(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
        ScheduleRule.EveryNDays(n = 3, anchor = LocalDate.of(2026, 1, 5)),
        ScheduleRule.TimesPerWeek(times = 4, weekStart = DayOfWeek.SUNDAY),
    )

    @Test
    fun `every rule survives a round trip`() {
        all.forEach { assertEquals(it, ScheduleRuleCodec.decode(ScheduleRuleCodec.encode(it))) }
    }

    @Test
    fun `the encoding is the documented one`() {
        assertEquals("daily", ScheduleRuleCodec.encode(all[0]))
        assertEquals("days:1,3,5", ScheduleRuleCodec.encode(all[1]))
        assertEquals("everyN:3:2026-01-05", ScheduleRuleCodec.encode(all[2]))
        assertEquals("perWeek:4:7", ScheduleRuleCodec.encode(all[3]))
    }

    @Test
    fun `day order does not affect the encoding`() {
        val a = ScheduleRule.SpecificDays(setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY))
        val b = ScheduleRule.SpecificDays(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
        assertEquals(ScheduleRuleCodec.encode(a), ScheduleRuleCodec.encode(b))
    }

    @Test
    fun `an unreadable rule fails loudly rather than silently defaulting to daily`() {
        assertFailsWith<IllegalArgumentException> { ScheduleRuleCodec.decode("weekly:3") }
        assertFailsWith<IllegalArgumentException> { ScheduleRuleCodec.decode("everyN:3") }
    }
}
