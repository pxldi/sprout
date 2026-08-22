/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.habit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.sprout.core.model.HabitType
import dev.sprout.core.ui.R

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

@Composable
internal fun ReminderStep(draft: HabitDraft, onEdit: Edit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepPrompt(
            title = stringResource(R.string.create_reminder_title),
            body = stringResource(R.string.create_reminder_body),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.create_reminder_switch))
            Switch(
                checked = draft.reminderEnabled,
                onCheckedChange = { on -> onEdit { it.copy(reminderEnabled = on) } },
            )
        }
        if (draft.reminderEnabled) {
            ReminderTimeField(draft, onEdit)
        }
    }
}
