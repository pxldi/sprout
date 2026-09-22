/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HabitTest {

    @Test
    fun `a private habit with an alias goes by the alias in the app`() {
        assertEquals("Evenings", habit(isPrivate = true, alias = "Evenings").displayName)
    }

    @Test
    fun `a private habit without an alias goes by its name in the app`() {
        assertEquals("No alcohol", habit(isPrivate = true).displayName)
    }

    @Test
    fun `an alias left on a public habit is not shown`() {
        assertEquals("No alcohol", habit(alias = "Evenings").displayName)
    }

    @Test
    fun `a public habit shows its name outside the app`() {
        assertEquals("No alcohol", habit().outsideName)
    }

    @Test
    fun `a private habit shows only its alias outside the app`() {
        assertEquals("Evenings", habit(isPrivate = true, alias = "Evenings").outsideName)
    }

    @Test
    fun `a private habit without an alias has no name outside the app`() {
        assertNull(habit(isPrivate = true).outsideName)
    }

    private fun habit(isPrivate: Boolean = false, alias: String? = null) = Habit(
        name = "No alcohol",
        type = HabitType.AVOID,
        schedule = ScheduleRule.Daily,
        isPrivate = isPrivate,
        alias = alias,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
