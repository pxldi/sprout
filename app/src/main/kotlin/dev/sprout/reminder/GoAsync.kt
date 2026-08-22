/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.reminder

import android.content.BroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runs suspending work from `onReceive` and holds the process alive until it finishes.
 *
 * Without [BroadcastReceiver.goAsync] the process is a candidate for death the moment `onReceive`
 * returns, and a database write launched into a bare scope would be killed mid-flight — the exact
 * failure where the notification's Done button appears to work and nothing is recorded.
 *
 * The budget is around ten seconds, which a couple of local queries never approach. [finish] is in
 * a `finally` so a thrown exception still releases the process rather than leaking it.
 */
internal fun goAsyncWork(receiver: BroadcastReceiver, block: suspend () -> Unit) {
    val pending = receiver.goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}
