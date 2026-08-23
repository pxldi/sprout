/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val TODAY: LocalDate = LocalDate.of(2026, 3, 2)

class ShineHistoryTest {

    private fun history() = temporaryShineHistory(
        File.createTempFile("shine", ".preferences_pb").also { it.delete() },
    )

    @Test
    fun `nothing has been said yet`() = runTest {
        assertTrue(history().shown.first().isEmpty())
    }

    @Test
    fun `what was said is remembered, per habit and per kind`() = runTest {
        val store = history()
        store.record("run-habit", "run", TODAY)
        store.record("run-habit", "week_best", TODAY.minusDays(3))
        store.record("read-habit", "run", TODAY.minusDays(9))

        val shown = store.shown.first()
        assertEquals(TODAY, shown[ShineHistory.keyOf("run-habit", "run")])
        assertEquals(TODAY.minusDays(3), shown[ShineHistory.keyOf("run-habit", "week_best")])
        // The same kind for a different habit is a different memory, or one busy habit would
        // silence the same praise for every other one.
        assertEquals(TODAY.minusDays(9), shown[ShineHistory.keyOf("read-habit", "run")])
    }

    @Test
    fun `saying the same thing again moves the date rather than adding a second entry`() = runTest {
        val store = history()
        store.record("run-habit", "run", TODAY.minusDays(20))
        store.record("run-habit", "run", TODAY)

        val shown = store.shown.first()
        assertEquals(1, shown.size)
        assertEquals(TODAY, shown[ShineHistory.keyOf("run-habit", "run")])
    }

    @Test
    fun `a deleted habit's memory is dropped`() = runTest {
        val store = history()
        store.record("kept", "run", TODAY)
        store.record("deleted", "run", TODAY)

        store.forget("deleted")

        val shown = store.shown.first()
        assertEquals(TODAY, shown[ShineHistory.keyOf("kept", "run")])
        assertNull(shown[ShineHistory.keyOf("deleted", "run")])
    }

    @Test
    fun `an unreadable file reads as nothing said, rather than throwing`() = runTest {
        // A write killed halfway leaves a file that cannot be parsed. Today derives its entire
        // list from a flow combining this one, so a throw here would take the screen with it —
        // and all that is actually lost is the app's memory of its own compliments.
        val file = File.createTempFile("shine", ".preferences_pb")
        file.writeBytes(byteArrayOf(0x07, 0x21, 0x63, 0x00, 0x11))

        assertTrue(temporaryShineHistory(file).shown.first().isEmpty())
    }

    @Test
    fun `a value that is not the map it should be reads as nothing said`() = runTest {
        // One DataStore, shared: a second instance over the same file is an error by design.
        val file = File.createTempFile("shine", ".preferences_pb").also { it.delete() }
        val raw = PreferenceDataStoreFactory.create { file }
        val store = ShineHistory(raw)
        store.record("run-habit", "run", TODAY)

        // The key is spelled out rather than borrowed, so a rename has to be a deliberate act.
        raw.edit { it[stringPreferencesKey("shine_shown")] = "not json at all" }

        assertTrue(store.shown.first().isEmpty())
    }
}
