/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.datastore

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
public object DatastoreModule {

    /**
     * One instance for the process.
     *
     * DataStore throws if a second one is opened over the same file, and this store is read by
     * Today and written from the same place — so a per-injection instance would be a crash
     * waiting for the second screen.
     */
    @Provides
    @Singleton
    public fun shineHistory(@ApplicationContext context: Context): ShineHistory =
        dev.sprout.core.datastore.shineHistory(context)
}
