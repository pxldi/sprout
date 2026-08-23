/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.habit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.sprout.core.ui.R
import kotlin.math.roundToInt

@Composable
public fun HabitDetailRoute(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HabitDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The habit stopped existing — deleted from the edit screen, or from another device. Leaving
    // is the only honest thing left to do; there is nothing here to show about it.
    LaunchedEffect(state.finished) {
        if (state.finished) onBack()
    }

    HabitDetailScreen(
        state = state,
        onBack = onBack,
        onEdit = { state.detail?.let { onEdit(it.habit.id) } },
        modifier = modifier,
    )
}

@Composable
public fun HabitDetailScreen(
    state: HabitDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DetailTopBar(
                title = state.detail?.habit?.name.orEmpty(),
                showEdit = state.detail != null,
                onBack = onBack,
                onEdit = onEdit,
            )
        },
    ) { inner ->
        state.detail?.let { detail ->
            Column(
                modifier = Modifier
                    .padding(inner)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                DetailBody(detail)
            }
        }
    }
}

@Composable
private fun DetailBody(detail: HabitDetail) {
    if (detail.habit.isArchived) {
        ArchivedCard()
    }
    DetailHeader(detail)
    RunsRow(detail.progress)

    if (detail.hasEnoughToDraw) {
        DetailSection(stringResource(R.string.detail_curve_title)) {
            StrengthCurve(
                points = detail.curve,
                today = detail.today,
                label = curveLabel(detail),
            )
        }
        DetailSection(stringResource(R.string.detail_calendar_title)) {
            Calendar(detail)
        }
    } else {
        Text(
            text = stringResource(
                if (detail.hasLog) R.string.detail_early else R.string.detail_empty,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    DetailSection(stringResource(R.string.detail_plan_title)) {
        PlanCard(detail.habit)
    }

    if (detail.hasEnoughToDraw) {
        DetailSection(stringResource(R.string.detail_weekdays_title)) {
            WeekdayBars(tallies = detail.weekdays, weeks = detail.weeks)
        }
    }
    if (detail.notes.isNotEmpty()) {
        DetailSection(stringResource(R.string.detail_notes_title)) {
            NotesList(detail.notes)
        }
    }
}

@Composable
private fun Calendar(detail: HabitDetail) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HabitHeatmap(days = detail.days, label = calendarLabel(detail))
        HeatmapLegend()
        Text(
            text = pluralStringResource(R.plurals.detail_window, detail.weeks, detail.weeks),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The chart is decorative; these sentences are what a screen reader actually gets. */
@Composable
private fun curveLabel(detail: HabitDetail): String = pluralStringResource(
    R.plurals.detail_curve_description,
    detail.weeks,
    detail.weeks,
    detail.progress.strength.roundToInt(),
)

@Composable
private fun calendarLabel(detail: HabitDetail): String = pluralStringResource(
    R.plurals.detail_calendar_description,
    detail.weeks,
    detail.weeks,
    detail.days.count { it.mark == DayMark.DONE },
    detail.days.count { it.mark == DayMark.MISSED },
    detail.days.count { it.mark == DayMark.SKIPPED },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailTopBar(
    title: String,
    showEdit: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    TopAppBar(
        title = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.create_back),
                )
            }
        },
        actions = {
            // Nothing to edit until it is known what the habit is.
            if (showEdit) {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.action_edit),
                    )
                }
            }
        },
    )
}
