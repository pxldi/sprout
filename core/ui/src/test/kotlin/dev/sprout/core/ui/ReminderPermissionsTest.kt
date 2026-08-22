/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.ui

import android.os.Build
import org.junit.Test
import kotlin.test.assertEquals

private const val PIE = Build.VERSION_CODES.P
private const val TIRAMISU = Build.VERSION_CODES.TIRAMISU

/**
 * The three states a reminder permission can be in, and the one that is easy to get wrong.
 *
 * A fresh install on API 33+ and a user who has refused twice look identical from
 * `areNotificationsEnabled` alone — both say no. Telling them apart is the whole job here,
 * because getting it backwards means either a button that silently does nothing or a trip to
 * Settings that was never necessary.
 */
class ReminderPermissionsTest {

    private fun access(
        enabled: Boolean,
        sdkInt: Int = TIRAMISU,
        showRationale: Boolean = false,
        asked: Boolean = false,
    ) = notificationAccess(enabled, sdkInt, showRationale, asked)

    @Test
    fun `notifications that are on need nothing, on any version`() {
        assertEquals(NotificationAccess.GRANTED, access(enabled = true))
        assertEquals(NotificationAccess.GRANTED, access(enabled = true, sdkInt = PIE))
    }

    @Test
    fun `a fresh install on Tiramisu is asked, not sent to settings`() {
        // Notifications are off because nobody has been asked yet, which reads exactly like a
        // refusal until `asked` is taken into account.
        assertEquals(NotificationAccess.ASKABLE, access(enabled = false))
    }

    @Test
    fun `one refusal is still worth asking again`() {
        // Android reports a rationale precisely while it will still show the dialog.
        assertEquals(
            NotificationAccess.ASKABLE,
            access(enabled = false, showRationale = true, asked = true),
        )
    }

    @Test
    fun `a second refusal leaves only settings`() {
        assertEquals(
            NotificationAccess.BLOCKED,
            access(enabled = false, showRationale = false, asked = true),
        )
    }

    @Test
    fun `before Tiramisu there is no dialog to show`() {
        // Notifications are on by default there, so off means the user turned them off in
        // Settings — and no runtime request exists that would turn them back on.
        assertEquals(NotificationAccess.BLOCKED, access(enabled = false, sdkInt = PIE))
    }
}
