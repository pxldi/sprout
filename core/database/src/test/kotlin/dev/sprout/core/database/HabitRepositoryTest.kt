/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.database

import app.cash.turbine.test
import dev.sprout.core.model.ScheduleRule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class HabitRepositoryTest {

    private val db = inMemoryDatabase()
    private val habits = repositories(db).first

    @After fun tearDown() = db.close()

    @Test
    fun `a habit survives a round trip through the database, plan and all`() = runTest {
        val saved = habits.save(habit())
        val loaded = habits.find(saved.id)

        assertEquals(saved.name, loaded?.name)
        assertEquals("I'm someone who runs in the morning", loaded?.identityPhrase)
        assertEquals("If I miss the morning, then I run after dinner", loaded?.copingPlan)
        assertEquals(ScheduleRule.Daily, loaded?.schedule)
    }

    @Test
    fun `a non-trivial schedule survives the encode-decode round trip in a real column`() = runTest {
        val rule = ScheduleRule.SpecificDays(setOf(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY))
        val saved = habits.save(habit(schedule = rule))
        assertEquals(rule, habits.find(saved.id)?.schedule)
    }

    @Test
    fun `saving stamps updated_at from the clock, so callers cannot forget it`() = runTest {
        val stale = habit().copy(updatedAt = TEST_NOW.minusSeconds(9999))
        assertEquals(TEST_NOW, habits.save(stale).updatedAt)
    }

    @Test
    fun `active habits exclude archived ones but the habit itself is still there`() = runTest {
        val saved = habits.save(habit())
        habits.archive(saved.id)

        habits.observeActive().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(habits.find(saved.id)?.isArchived == true)
    }

    @Test
    fun `deleting tombstones the row rather than removing it`() = runTest {
        val saved = habits.save(habit())
        habits.delete(saved.id)

        // Invisible to every read path...
        assertNull(habits.find(saved.id))
        // ...but the row is still on disk, carrying its tombstone for sync to propagate.
        val raw = db.query("SELECT deleted_at FROM habit WHERE id = ?", arrayOf(saved.id))
        raw.use {
            assertTrue(it.moveToFirst())
            assertEquals(TEST_NOW.toEpochMilli(), it.getLong(0))
        }
    }

    @Test
    fun `reordering assigns positions in the order given`() = runTest {
        val a = habits.save(habit(name = "A"))
        val b = habits.save(habit(name = "B"))
        val c = habits.save(habit(name = "C"))

        habits.reorder(listOf(c.id, a.id, b.id))

        habits.observeActive().test {
            assertEquals(listOf("C", "A", "B"), awaitItem().map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observers see a newly saved habit without being asked to refresh`() = runTest {
        habits.observeActive().test {
            assertTrue(awaitItem().isEmpty())
            habits.save(habit(name = "Meditate"))
            assertEquals(listOf("Meditate"), awaitItem().map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
