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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.sprout.core.model.HabitType
import dev.sprout.core.ui.R

internal typealias Edit = ((HabitDraft) -> HabitDraft) -> Unit

/**
 * The controls behind the creation wizard's questions, without the questions.
 *
 * Split out because editing asks for exactly the same answers in a completely different voice.
 * The wizard's prompts are written for someone who has not decided yet — "Everyone misses.
 * Deciding the fallback now is what keeps a missed morning from becoming a missed week" — and
 * repeating that at somebody who is changing a reminder time by ten minutes would be a lecture.
 * The fields are shared; the framing around them is not.
 */
@Composable
internal fun NameAndTypeFields(draft: HabitDraft, onEdit: Edit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = { name -> onEdit { it.copy(name = name) } },
            label = { Text(stringResource(R.string.create_name_label)) },
            placeholder = { Text(stringResource(R.string.create_name_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TypePicker(draft, onEdit)
        if (draft.type == HabitType.DO_NUMERIC) {
            NumericFields(draft, onEdit)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypePicker(draft: HabitDraft, onEdit: Edit) {
    val types = listOf(
        HabitType.DO_BOOL to R.string.create_type_do,
        HabitType.DO_NUMERIC to R.string.create_type_count,
        HabitType.AVOID to R.string.create_type_avoid,
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
}

@Composable
private fun NumericFields(draft: HabitDraft, onEdit: Edit) {
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

@Composable
internal fun SmallestField(draft: HabitDraft, onEdit: Edit) {
    OutlinedTextField(
        value = draft.minimumVersion,
        onValueChange = { min -> onEdit { it.copy(minimumVersion = min) } },
        label = { Text(stringResource(R.string.create_smallest_label)) },
        placeholder = { Text(stringResource(R.string.create_smallest_hint)) },
        supportingText = { Text(stringResource(R.string.create_optional)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The if-half of an implementation intention, with the then-half read back underneath.
 *
 * The sentence is shown whole because that is the form the effect was measured in — the prefix
 * and the echo are UI, so the stored cue stays translatable and survives a rename.
 */
@Composable
internal fun CueField(draft: HabitDraft, onEdit: Edit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
internal fun CopingField(draft: HabitDraft, onEdit: Edit) {
    OutlinedTextField(
        value = draft.copingPlan,
        onValueChange = { plan -> onEdit { it.copy(copingPlan = plan) } },
        prefix = { Text(stringResource(R.string.create_coping_prefix)) },
        placeholder = { Text(stringResource(R.string.create_coping_hint)) },
        modifier = Modifier.fillMaxWidth(),
    )
}
