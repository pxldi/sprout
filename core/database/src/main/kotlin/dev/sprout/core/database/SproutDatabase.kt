/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.sprout.core.database.dao.EntryDao
import dev.sprout.core.database.dao.HabitDao
import dev.sprout.core.database.dao.LapseDao
import dev.sprout.core.database.dao.ReminderDao
import dev.sprout.core.database.entity.EntryEntity
import dev.sprout.core.database.entity.HabitEntity
import dev.sprout.core.database.entity.LapseEntity
import dev.sprout.core.database.entity.ReminderEntity

/**
 * Schemas are exported to `core/database/schemas` and committed. Migrations are written by hand
 * and tested; `fallbackToDestructiveMigration` is never acceptable here — this is the only copy
 * of data the user has, and it is the point of the app.
 */
@Database(
    entities = [
        HabitEntity::class,
        EntryEntity::class,
        LapseEntity::class,
        ReminderEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
internal abstract class SproutDatabase : RoomDatabase() {

    abstract fun habitDao(): HabitDao
    abstract fun entryDao(): EntryDao
    abstract fun lapseDao(): LapseDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        const val NAME: String = "sprout.db"

        fun build(context: Context): SproutDatabase =
            Room.databaseBuilder(context, SproutDatabase::class.java, NAME)
                .addCallback(EnforceForeignKeys)
                .build()
    }
}

/** SQLite disables foreign keys per connection by default; the cascade deletes depend on them. */
private object EnforceForeignKeys : RoomDatabase.Callback() {
    override fun onOpen(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys = ON")
    }
}
