/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.database

import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Private habits. Every habit that existed before stays public and has no alias. */
internal val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE habit ADD COLUMN is_private INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE habit ADD COLUMN alias TEXT")
    }
}

/** Every migration, in order. The app and the tests open the database through this one list. */
internal fun <T : RoomDatabase> RoomDatabase.Builder<T>.addAllMigrations(): RoomDatabase.Builder<T> =
    addMigrations(MIGRATION_1_2)
