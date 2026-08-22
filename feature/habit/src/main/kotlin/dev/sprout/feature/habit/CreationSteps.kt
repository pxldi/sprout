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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.sprout.core.model.HabitType
import dev.sprout.core.ui.ExactAlarmNotice
import dev.sprout.core.ui.NotificationAccess
import dev.sprout.core.ui.NotificationAccessNotice
import dev.sprout.core.ui.R
import dev.sprout.core.ui.ReminderPermissions

internal typealias Edit = ((HabitDraft) -> HabitDraft) -> Unit

/** The question, then anything explaining why it is being asked. Same shape on every step. */
@Composable
internal fun StepPrompt(title: String, body: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        body?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WhatStep(draft: HabitDraft, onEdit: Edit) {
    val types = listOf(
        HabitType.DO_BOOL to R.string.create_type_do,
        HabitType.DO_NUMERIC to R.string.create_type_count,
        HabitType.AVOID to R.string.create_type_avoid,
    )
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepPrompt(stringResource(R.string.create_what_title))

        OutlinedTextField(
            value = draft.name,
            onValueChange = { name -> onEdit { it.copy(name = name) } },
            label = { Text(stringResource(R.string.create_name_label)) },
            placeholder = { Text(stringResource(R.string.create_name_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            types.forEachIndexed { index, (type, label) ->
                SegmentedButton(
                    selected = draft.type == type,
                    onClick = { onEdit { it.copy(type = type) } },
                    shape = SegmentedButtonDefaults.itemShape(index, types.size),
                ) { Text(stringResource(label)) }
            }
        }

        if (draft.type == HabitType.DO_NUMERIC) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft.target,
                    onValueChange = { target -> onEdit { it.copy(target = target) } },
                    label = { Text(stringResource(R.string.create_target_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = draft.unit,
                    onValueChange = { unit -> onEdit { it.copy(unit = unit) } },
                    label = { Text(stringResource(R.string.create_unit_label)) },
                    placeholder = { Text(stringResource(R.string.create_unit_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        OutlinedTextField(
            value = draft.identityPhrase,
            onValueChange = { phrase -> onEdit { it.copy(identityPhrase = phrase) } },
            label = { Text(stringResource(R.string.create_identity_label)) },
            placeholder = { Text(stringResource(R.string.create_identity_hint)) },
            supportingText = { Text(stringResource(R.string.create_identity_help)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun SmallestStep(draft: HabitDraft, onEdit: Edit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepPrompt(
            title = stringResource(R.string.create_smallest_title),
            body = stringResource(R.string.create_smallest_body),
        )
        OutlinedTextField(
            value = draft.minimumVersion,
            onValueChange = { min -> onEdit { it.copy(minimumVersion = min) } },
            label = { Text(stringResource(R.string.create_smallest_label)) },
            placeholder = { Text(stringResource(R.string.create_smallest_hint)) },
            supportingText = { Text(stringResource(R.string.create_optional)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The if-half of an implementation intention. The then-half is the habit itself, shown back to
 * the user so the sentence reads whole — that is the form the effect was measured in.
 */
@Composable
internal fun CueStep(draft: HabitDraft, onEdit: Edit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepPrompt(
            title = stringResource(R.string.create_cue_title),
            body = stringResource(R.string.create_cue_body),
        )
        OutlinedTextField(
            value = draft.cue,
            onValueChange = { cue -> onEdit { it.copy(cue = cue) } },
            prefix = { Text(stringResource(R.string.create_cue_prefix)) },
            placeholder = { Text(stringResource(R.string.create_cue_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.create_cue_then, draft.name.ifBlank { "…" }),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun CopingStep(draft: HabitDraft, onEdit: Edit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepPrompt(
            title = stringResource(R.string.create_coping_title),
            body = stringResource(R.string.create_coping_body),
        )
        OutlinedTextField(
            value = draft.copingPlan,
            onValueChange = { plan -> onEdit { it.copy(copingPlan = plan) } },
            prefix = { Text(stringResource(R.string.create_coping_prefix)) },
            placeholder = { Text(stringResource(R.string.create_coping_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The only step that has to ask Android for anything.
 *
 * The permission is requested the moment the switch goes on, because that is the one instant it
 * explains itself: the user has just asked to be reminded, so a dialog about notifications is an
 * answer rather than an interruption. Asking on entering the step would prompt people who are
 * about to decline, and asking after saving would prompt them on a screen that has moved on.
 *
 * The cost is that abandoning the wizard here still spends one of the two asks Android allows.
 * That is the price of asking in context, and cheaper than the alternative: a reminder the user
 * set, that never arrives, and never says why.
 */
@Composable
internal fun ReminderStep(draft: HabitDraft, permissions: ReminderPermissions, onEdit: Edit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepPrompt(
            title = stringResource(R.string.create_reminder_title),
            body = stringResource(R.string.create_reminder_body),
        )
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
 * throw the user straight out to Settings — a hijack, not a prompt. The notice on the step
 * offers that trip as something they choose instead.
 */
private fun onReminderToggled(on: Boolean, permissions: ReminderPermissions, onEdit: Edit) {
    onEdit { it.copy(reminderEnabled = on) }
    if (on && permissions.notifications == NotificationAccess.ASKABLE) {
        permissions.resolveNotifications()
    }
}
