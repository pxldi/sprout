/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Says that reminders will not appear, and offers the one thing that would fix it.
 *
 * Deliberately not `errorContainer`: the palette reserves red for something the user must
 * correct, and a permission they declined is a choice, not a mistake. Bark says "you should
 * know this" without shouting.
 *
 * Renders nothing when [access] is granted, so callers can hand it the state unconditionally.
 */
@Composable
public fun NotificationAccessNotice(
    access: NotificationAccess,
    onFix: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (access == NotificationAccess.GRANTED) return

    val blocked = access == NotificationAccess.BLOCKED
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(
                    if (blocked) R.string.perm_notify_blocked else R.string.perm_notify_needed,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onFix) {
                Text(
                    stringResource(
                        if (blocked) R.string.perm_open_settings else R.string.perm_allow,
                    ),
                )
            }
        }
    }
}

/**
 * Offers minute-accurate reminders, and is honest that they are not the default.
 *
 * Much quieter than [NotificationAccessNotice] and shown only once notifications are sorted:
 * this is a refinement to something that already works, and stacking two permission cards on a
 * screen whose whole point is a single question would undo the flow.
 *
 * Renders nothing when exact alarms are already allowed.
 */
@Composable
public fun ExactAlarmNotice(
    access: ExactAlarmAccess,
    onFix: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (access == ExactAlarmAccess.ALLOWED) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.perm_exact_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = onFix,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.perm_exact_action),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * Says that reminders the user has already set are not arriving.
 *
 * Separate from [NotificationAccessNotice] because the situations are not the same. That one
 * interrupts someone in the act of setting a reminder and can reasonably ask; this one reports a
 * state they have been living in, possibly for weeks, on a screen they opened for another reason.
 * So it never offers the dialog — see [ReminderPermissions.openNotificationSettings].
 *
 * Shown whenever the two settings disagree with each other: a reminder that is switched on and
 * notifications that are switched off. The other way out is to turn the reminder off, which will
 * be possible once habits can be edited.
 */
@Composable
public fun RemindersSilencedBanner(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.perm_reminders_silenced),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.perm_open_settings))
            }
        }
    }
}
