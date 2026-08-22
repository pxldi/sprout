/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.habit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sprout.core.ui.R

@Composable
public fun CreateHabitRoute(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HabitCreationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Leave once the habit is actually stored, not when the button is tapped. Navigating on the
    // tap would show Today without the new habit on it, for as long as the write takes.
    LaunchedEffect(state.savedHabitId) {
        if (state.savedHabitId != null) onFinished()
    }

    CreateHabitScreen(
        state = state,
        onEdit = viewModel::edit,
        onNext = viewModel::next,
        onBack = { if (!viewModel.back()) onFinished() },
        onSave = viewModel::save,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun CreateHabitScreen(
    state: HabitCreationUiState,
    onEdit: ((HabitDraft) -> HabitDraft) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The system back gesture walks back through the questions before it leaves the flow.
    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.create_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.create_back),
                        )
                    }
                },
            )
        },
        bottomBar = {
            CreationNav(state = state, onNext = onNext, onBack = onBack, onSave = onSave)
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StepHeader(state.step)
            CreationStepContent(step = state.step, draft = state.draft, onEdit = onEdit)
        }
    }
}

@Composable
private fun StepHeader(step: CreationStep) {
    val total = CreationStep.entries.size
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LinearProgressIndicator(
            progress = { (step.ordinal + 1f) / total },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.create_step, step.ordinal + 1, total),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CreationStepContent(
    step: CreationStep,
    draft: HabitDraft,
    onEdit: ((HabitDraft) -> HabitDraft) -> Unit,
) {
    when (step) {
        CreationStep.WHAT -> WhatStep(draft, onEdit)
        CreationStep.SMALLEST -> SmallestStep(draft, onEdit)
        CreationStep.CUE -> CueStep(draft, onEdit)
        CreationStep.COPING -> CopingStep(draft, onEdit)
        CreationStep.SCHEDULE -> ScheduleStep(draft, onEdit)
        CreationStep.REMINDER -> ReminderStep(draft, onEdit)
    }
}

@Composable
private fun CreationNav(
    state: HabitCreationUiState,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onBack, enabled = !state.step.isFirst) {
            Text(stringResource(R.string.create_back))
        }
        Button(
            onClick = if (state.step.isLast) onSave else onNext,
            enabled = state.canAdvance && !state.isSaving,
        ) {
            Text(
                stringResource(
                    if (state.step.isLast) R.string.create_save else R.string.create_next,
                ),
            )
        }
    }
}
