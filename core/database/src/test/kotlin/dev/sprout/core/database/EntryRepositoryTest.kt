/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.database

import dev.sprout.core.model.EntrySource
import dev.sprout.core.model.EntryStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class EntryRepositoryTest {

    private val db = inMemoryDatabase()
    private val habits = repositories(db).first
    private val entries = repositories(db).second

    @After fun tearDown() = db.close()

    @Test
    fun `logging the same day twice updates one row instead of creating two`() = runTest {
        val h = habits.save(habit())

        val first = entries.log(h.id, TEST_START, EntryStatus.DONE, source = EntrySource.WIDGET)
        val second = entries.log(h.id, TEST_START, EntryStatus.DONE_MIN, source = EntrySource.NOTIFICATION)

        // Same row, reused id — this is what stops a widget tap and a notification action racing.
        assertEquals(first.id, second.id)
        assertEquals(1, entries.observeForHabit(h.id).first().size)
        assertEquals(EntryStatus.DONE_MIN, entries.find(h.id, TEST_START)?.status)
        assertEquals(EntrySource.NOTIFICATION, entries.find(h.id, TEST_START)?.source)
    }

    @Test
    fun `the original created_at is preserved when a day is re-logged`() = runTest {
        val h = habits.save(habit())
        val first = entries.log(h.id, TEST_START, EntryStatus.DONE)
        val second = entries.log(h.id, TEST_START, EntryStatus.SKIP)
        assertEquals(first.createdAt, second.createdAt)
    }

    @Test
    fun `clearing a day hides it, and logging again brings it back`() = runTest {
        val h = habits.save(habit())
        entries.log(h.id, TEST_START, EntryStatus.DONE)

        entries.clear(h.id, TEST_START)
        assertNull(entries.find(h.id, TEST_START))

        entries.log(h.id, TEST_START, EntryStatus.DONE)
        assertNotNull(entries.find(h.id, TEST_START), "re-logging must clear the tombstone")
    }

    @Test
    fun `entries are scoped to their habit`() = runTest {
        val a = habits.save(habit(name = "Run"))
        val b = habits.save(habit(name = "Read"))
        entries.log(a.id, TEST_START, EntryStatus.DONE)

        assertEquals(1, entries.observeForHabit(a.id).first().size)
        assertEquals(0, entries.observeForHabit(b.id).first().size)
    }

    @Test
    fun `deleting a habit cascades to its entries`() = runTest {
        val h = habits.save(habit())
        entries.log(h.id, TEST_START, EntryStatus.DONE)

        // A tombstoned habit keeps its entries; they are hidden with it, not destroyed.
        habits.delete(h.id)
        assertEquals(1, entries.observeForHabit(h.id).first().size)
    }

    @Test
    fun `a measurable entry keeps its value`() = runTest {
        val h = habits.save(habit())
        entries.log(h.id, TEST_START, EntryStatus.DONE, value = 2.5, note = "two and a half litres")
        val stored = entries.find(h.id, TEST_START)
        assertEquals(2.5, stored?.value)
        assertEquals("two and a half litres", stored?.note)
    }

    @Test
    fun `toggling marks the day done, and toggling again un-marks it`() = runTest {
        val h = habits.save(habit())

        entries.toggle(h.id, TEST_START)
        assertEquals(EntryStatus.DONE, entries.find(h.id, TEST_START)?.status)

        entries.toggle(h.id, TEST_START)
        assertNull(entries.find(h.id, TEST_START))
    }

    @Test
    fun `toggling a skipped day completes it rather than clearing it`() = runTest {
        val h = habits.save(habit())
        entries.log(h.id, TEST_START, EntryStatus.SKIP)

        // A skip is not a completion, so there is nothing to undo — the tap means "actually, I did it".
        entries.toggle(h.id, TEST_START)
        assertEquals(EntryStatus.DONE, entries.find(h.id, TEST_START)?.status)
    }

    @Test
    fun `toggling records where the tap came from`() = runTest {
        val h = habits.save(habit())
        entries.toggle(h.id, TEST_START, EntrySource.NOTIFICATION)
        assertEquals(EntrySource.NOTIFICATION, entries.find(h.id, TEST_START)?.source)
    }
}
