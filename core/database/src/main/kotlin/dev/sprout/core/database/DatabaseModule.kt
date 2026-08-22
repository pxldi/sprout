/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.database

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.sprout.core.database.repository.EntryRepository
import dev.sprout.core.database.repository.HabitRepository
import dev.sprout.core.database.repository.LapseRepository
import dev.sprout.core.database.repository.ReminderRepository
import java.time.Clock
import javax.inject.Singleton

/**
 * The module's entire public surface: four repositories over domain types.
 *
 * Room itself — the database, DAOs and entities — is `internal`. That is not only tidiness:
 * `@Provides` functions must be public, because Kotlin mangles `internal` function names
 * (`database$core_database_debug`) and Hilt's generated Java cannot call them. Bundling the
 * repositories into one public aggregate keeps every Room type behind the module boundary while
 * giving Hilt public signatures to bind.
 */
public class SproutRepositories internal constructor(
    public val habits: HabitRepository,
    public val entries: EntryRepository,
    public val lapses: LapseRepository,
    public val reminders: ReminderRepository,
    private val close: () -> Unit = {},
) {
    /** Releases the underlying database. Only meaningful for the in-memory test stack. */
    public fun close(): Unit = close.invoke()
}

/**
 * An entirely in-memory stack, for tests in modules that cannot see this module's `internal`
 * Room types (which is all of them).
 *
 * Shipping a test hook in production code is a real cost, paid deliberately: the alternative is
 * making [SproutDatabase] and every DAO public purely so other modules' tests can build one.
 * Room's own `inMemoryDatabaseBuilder` makes the same trade.
 */
@VisibleForTesting
public fun inMemoryRepositories(context: Context, clock: Clock): SproutRepositories {
    val db = Room.inMemoryDatabaseBuilder(context, SproutDatabase::class.java)
        .allowMainThreadQueries()
        // Run queries on the calling thread. Without this, a suspending DAO call hops to Room's
        // own executor, which no test scheduler can track — so tests that assert after a write
        // race it instead of waiting for it.
        .setQueryExecutor(Runnable::run)
        .setTransactionExecutor(Runnable::run)
        .build()
    return SproutRepositories(
        habits = HabitRepository(db.habitDao(), clock),
        entries = EntryRepository(db.entryDao(), clock),
        lapses = LapseRepository(db.lapseDao(), clock),
        reminders = ReminderRepository(db.reminderDao(), clock),
        close = db::close,
    )
}

@Module
@InstallIn(SingletonComponent::class)
public object DatabaseModule {

    /** Injected rather than calling `Instant.now()` inline, so time is controllable in tests. */
    @Provides
    @Singleton
    public fun clock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    public fun repositories(
        @ApplicationContext context: Context,
        clock: Clock,
    ): SproutRepositories {
        val db = SproutDatabase.build(context)
        return SproutRepositories(
            habits = HabitRepository(db.habitDao(), clock),
            entries = EntryRepository(db.entryDao(), clock),
            lapses = LapseRepository(db.lapseDao(), clock),
            reminders = ReminderRepository(db.reminderDao(), clock),
        )
    }

    @Provides public fun habitRepository(all: SproutRepositories): HabitRepository = all.habits
    @Provides public fun entryRepository(all: SproutRepositories): EntryRepository = all.entries
    @Provides public fun lapseRepository(all: SproutRepositories): LapseRepository = all.lapses
    @Provides public fun reminderRepository(all: SproutRepositories): ReminderRepository = all.reminders
}
