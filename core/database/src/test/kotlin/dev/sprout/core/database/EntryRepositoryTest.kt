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

    @Test
    fun `a note survives the day being un-ticked and ticked again`() = runTest {
        val h = habits.save(habit())
        entries.log(h.id, TEST_START, EntryStatus.DONE)
        entries.note(h.id, TEST_START, "shoulder was sore, went shorter")

        // Un-ticking tombstones the row; ticking again revives it. Neither tap knows or should
        // know anything about notes, and neither may take the user's sentence with it.
        entries.toggle(h.id, TEST_START)
        entries.toggle(h.id, TEST_START)

        assertEquals("shoulder was sore, went shorter", entries.find(h.id, TEST_START)?.note)
    }

    @Test
    fun `changing the day's status leaves the note alone`() = runTest {
        val h = habits.save(habit())
        entries.log(h.id, TEST_START, EntryStatus.DONE)
        entries.note(h.id, TEST_START, "did it before work")

        entries.log(h.id, TEST_START, EntryStatus.SKIP)

        val stored = entries.find(h.id, TEST_START)
        assertEquals(EntryStatus.SKIP, stored?.status)
        assertEquals("did it before work", stored?.note)
    }

    @Test
    fun `writing a note does not decide what the day was`() = runTest {
        val h = habits.save(habit())
        entries.log(h.id, TEST_START, EntryStatus.SKIP, source = EntrySource.WIDGET)

        entries.note(h.id, TEST_START, "away for work")

        val stored = entries.find(h.id, TEST_START)
        assertEquals(EntryStatus.SKIP, stored?.status)
        assertEquals(EntrySource.WIDGET, stored?.source)
    }

    @Test
    fun `a note on a day that was never logged is not a log`() = runTest {
        val h = habits.save(habit())

        entries.note(h.id, TEST_START, "meant to, did not")

        // The absence of a row is what a miss means. Conjuring one to hold a sentence would make
        // an unanswered day look answered, everywhere that reads the log.
        assertNull(entries.find(h.id, TEST_START))
        assertEquals(0, entries.observeForHabit(h.id).first().size)
    }

    @Test
    fun `a cleared day is not quietly revived by a note`() = runTest {
        val h = habits.save(habit())
        entries.log(h.id, TEST_START, EntryStatus.DONE)
        entries.clear(h.id, TEST_START)

        entries.note(h.id, TEST_START, "typed after it was cleared elsewhere")

        assertNull(entries.find(h.id, TEST_START), "a tombstoned day stays cleared")
    }

    @Test
    fun `emptying the field removes the note`() = runTest {
        val h = habits.save(habit())
        entries.log(h.id, TEST_START, EntryStatus.DONE)
        entries.note(h.id, TEST_START, "first thoughts")

        entries.note(h.id, TEST_START, "   ")

        val stored = entries.find(h.id, TEST_START)
        assertNull(stored?.note, "blank means remove, not store whitespace")
        assertEquals(EntryStatus.DONE, stored?.status, "removing a note is not un-logging the day")
    }

    @Test
    fun `a note is stored as written, less the stray whitespace around it`() = runTest {
        val h = habits.save(habit())
        entries.log(h.id, TEST_START, EntryStatus.DONE)

        entries.note(h.id, TEST_START, "  rained the whole way  ")

        assertEquals("rained the whole way", entries.find(h.id, TEST_START)?.note)
    }
}
