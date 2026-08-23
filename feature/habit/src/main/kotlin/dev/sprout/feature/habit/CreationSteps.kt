/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.habit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sprout.core.ui.R
import dev.sprout.core.ui.ReminderPermissions

/**
 * One question per screen, and the reason it is being asked.
 *
 * Each step is its prompt plus the matching fields from [HabitFields.kt] — the fields are shared
 * with the edit form, the prompts are not. This file is only the wizard's voice.
 */

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

@Composable
internal fun WhatStep(draft: HabitDraft, onEdit: Edit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepPrompt(stringResource(R.string.create_what_title))
        NameAndTypeFields(draft, onEdit)
    }
}

@Composable
internal fun SmallestStep(draft: HabitDraft, onEdit: Edit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepPrompt(
            title = stringResource(R.string.create_smallest_title),
            body = stringResource(R.string.create_smallest_body),
        )
        SmallestField(draft, onEdit)
    }
}

@Composable
internal fun CueStep(draft: HabitDraft, onEdit: Edit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepPrompt(
            title = stringResource(R.string.create_cue_title),
            body = stringResource(R.string.create_cue_body),
        )
        CueField(draft, onEdit)
    }
}

@Composable
internal fun CopingStep(draft: HabitDraft, onEdit: Edit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepPrompt(
            title = stringResource(R.string.create_coping_title),
            body = stringResource(R.string.create_coping_body),
        )
        CopingField(draft, onEdit)
    }
}

@Composable
internal fun ScheduleStep(draft: HabitDraft, onEdit: Edit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StepPrompt(stringResource(R.string.create_schedule_title))
        SchedulePicker(draft, onEdit)
    }
}

@Composable
internal fun ReminderStep(draft: HabitDraft, permissions: ReminderPermissions, onEdit: Edit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepPrompt(
            title = stringResource(R.string.create_reminder_title),
            body = stringResource(R.string.create_reminder_body),
        )
        ReminderFields(draft, permissions, onEdit)
    }
}
