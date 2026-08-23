/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.habit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.sprout.core.ui.ExactAlarmNotice
import dev.sprout.core.ui.NotificationAccess
import dev.sprout.core.ui.NotificationAccessNotice
import dev.sprout.core.ui.R
import dev.sprout.core.ui.ReminderPermissions

/**
 * The reminder switch, its time, and whatever Android still needs to be asked.
 *
 * The permission is requested the moment the switch goes on, because that is the one instant it
 * explains itself: the user has just asked to be reminded, so a dialog about notifications is an
 * answer rather than an interruption. Asking on entering the screen would prompt people who are
 * about to decline, and asking after saving would prompt them on a screen that has moved on.
 *
 * The cost is that turning the switch on and then leaving still spends one of the two asks
 * Android allows. That is the price of asking in context, and cheaper than the alternative: a
 * reminder the user set, that never arrives, and never says why.
 */
@Composable
internal fun ReminderFields(draft: HabitDraft, permissions: ReminderPermissions, onEdit: Edit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Toggled from the whole row rather than from the switch. A bare Compose Switch is its
        // own accessibility node with nothing in it, and the "Remind me" beside it is a second
        // node a screen reader never connects to the first; `toggleable` merges the two so the
        // control announces with its label. It is also a much larger target for everyone else.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = draft.reminderEnabled,
                    role = Role.Switch,
                    onValueChange = { on -> onReminderToggled(on, permissions, onEdit) },
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.create_reminder_switch))
            Switch(checked = draft.reminderEnabled, onCheckedChange = null)
        }
        if (draft.reminderEnabled) {
            ReminderTimeField(draft, onEdit)
            NotificationAccessNotice(
                access = permissions.notifications,
                onFix = permissions::resolveNotifications,
            )
            // Punctuality is only worth offering once something can appear at all.
            if (permissions.notifications == NotificationAccess.GRANTED) {
                ExactAlarmNotice(
                    access = permissions.exactAlarms,
                    onFix = permissions::resolveExactAlarms,
                )
            }
        }
    }
}

/**
 * Turning the reminder on is also when the app asks to be allowed to show one.
 *
 * Only while Android would still draw the dialog. Once it would not, flipping a switch would
 * throw the user straight out to Settings — a hijack, not a prompt. The notice underneath offers
 * that trip as something they choose instead.
 */
private fun onReminderToggled(on: Boolean, permissions: ReminderPermissions, onEdit: Edit) {
    onEdit { it.copy(reminderEnabled = on) }
    if (on && permissions.notifications == NotificationAccess.ASKABLE) {
        permissions.resolveNotifications()
    }
}
