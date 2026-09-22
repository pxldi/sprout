/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.sprout.core.database.repository.HabitRepository
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Each migration against the schema JSON committed for the version it starts from.
 *
 * The rows are written with raw SQL, because the entity classes only know the newest schema.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SproutDatabase::class.java,
    )

    @Test
    fun `a habit from version 1 is public with no alias after the upgrade to 2`() {
        helper.createDatabase(DB, 1).use { it.execSQL(INSERT_V1_HABIT) }

        val db = helper.runMigrationsAndValidate(DB, 2, true, MIGRATION_1_2)

        db.query("SELECT is_private, alias FROM habit WHERE id = '$HABIT_ID'").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
            assertTrue(it.isNull(1))
        }
    }

    @Test
    fun `the app's migration list opens a version 1 file with every habit field intact`() = runTest {
        helper.createDatabase(DB, 1).use { it.execSQL(INSERT_V1_HABIT) }

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), SproutDatabase::class.java, DB)
            .addAllMigrations()
            .allowMainThreadQueries()
            .build()
        helper.closeWhenFinished(db)
        val habit = HabitRepository(db.habitDao(), fixedClock()).find(HABIT_ID)

        assertEquals("Read", habit?.name)
        assertEquals("I've poured the first coffee", habit?.cue)
        assertEquals(2, habit?.position)
        assertFalse(habit?.isPrivate ?: true)
        assertNull(habit?.alias)
    }

    private companion object {
        const val DB = "migration-test.db"
        const val HABIT_ID = "5b0e6c1a-0000-4000-8000-000000000001"

        val INSERT_V1_HABIT = """
            INSERT INTO habit (id, name, type, schedule, cue_text, position, created_at, updated_at)
            VALUES ('$HABIT_ID', 'Read', 'DO_BOOL', 'daily', 'I''ve poured the first coffee', 2,
                    1767600000000, 1767600000000)
        """.trimIndent()
    }
}
