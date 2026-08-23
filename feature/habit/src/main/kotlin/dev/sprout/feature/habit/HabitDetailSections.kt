/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.habit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sprout.core.model.Habit
import dev.sprout.core.scoring.HabitProgress
import dev.sprout.core.scoring.PlantStage
import dev.sprout.core.scoring.StreakState
import dev.sprout.core.scoring.plantStage
import dev.sprout.core.ui.R
import dev.sprout.core.ui.StrengthRing
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

private val RING_SIZE = 72.dp

/** A quiet label over a divider. The same shape the edit form uses, so the two screens rhyme. */
@Composable
internal fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        HorizontalDivider()
        content()
    }
}

/**
 * The habit as it stands: how strong, how grown, and who it says they are.
 *
 * The plant stage is named rather than only drawn. "Sapling" is a fact about a habit five weeks
 * old; a bare 46 out of 100 invites reading it as a mark out of a hundred.
 */
@Composable
internal fun DetailHeader(detail: HabitDetail, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val strength = detail.progress.strength
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StrengthRing(
            strength = strength,
            label = context.getString(
                R.string.habit_strength_description,
                detail.habit.name,
                strength.roundToInt(),
            ),
            size = RING_SIZE,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(detail.progress.plantStage().labelRes()),
                style = MaterialTheme.typography.titleLarge,
            )
            detail.habit.identityPhrase?.let {
                Text(
                    text = stringResource(R.string.detail_identity, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun PlantStage.labelRes(): Int = when (this) {
    PlantStage.SEED -> R.string.stage_seed
    PlantStage.SPROUT -> R.string.stage_sprout
    PlantStage.SAPLING -> R.string.stage_sapling
    PlantStage.TREE -> R.string.stage_tree
    PlantStage.INGRAINED -> R.string.stage_ingrained
}

/**
 * Three numbers side by side, deliberately.
 *
 * No single one of them owns the story: a current run of zero sits next to a best run that is
 * permanent and a fraction whose denominator is the chances that really existed. That is the
 * whole reason they are shown together — see docs/02-app-design.md.
 */
@Composable
internal fun RunsRow(progress: HabitProgress, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Stat("${progress.currentRun}", stringResource(R.string.detail_runs_current))
            Stat("${progress.bestRun}", stringResource(R.string.detail_runs_best))
            // Omitted rather than shown as "0 / 0", which is the same noise Today already
            // refuses to print: a habit with no chances behind it has nothing to report here.
            if (progress.recentChances > 0) {
                Stat(
                    value = stringResource(
                        R.string.detail_runs_fraction,
                        progress.recentCompletions,
                        progress.recentChances,
                    ),
                    label = stringResource(R.string.detail_runs_recent),
                )
            }
        }
        // Same sentence Today uses, and for the same reason: a paused run is described as
        // repairable, never as expiring.
        if (progress.streakState == StreakState.PAUSED && progress.currentRun > 0) {
            Quiet(stringResource(R.string.note_repairable))
        }
        if (progress.restDaysAvailable > 0) {
            Quiet(
                pluralStringResource(
                    R.plurals.detail_rest_days,
                    progress.restDaysAvailable,
                    progress.restDaysAvailable,
                ),
            )
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Quiet(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The if-then plan, written back out as sentences.
 *
 * Only the situations are stored, so the sentence around them is assembled here — which keeps it
 * translatable and keeps it correct after the habit is renamed.
 */
@Composable
internal fun PlanCard(habit: Habit, modifier: Modifier = Modifier) {
    val lines = listOfNotNull(
        habit.cue?.let { stringResource(R.string.detail_plan_cue, it, habit.name) },
        habit.copingPlan?.let { stringResource(R.string.detail_plan_coping, it) },
        habit.minimumVersion?.let { stringResource(R.string.detail_plan_smallest, it) },
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (lines.isEmpty()) {
            Quiet(stringResource(R.string.detail_plan_empty))
        } else {
            lines.forEach { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
internal fun NotesList(notes: List<DatedNote>, modifier: Modifier = Modifier) {
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        notes.forEach { note ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = note.date.format(formatter),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text = note.note, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** States the two consequences and nothing else. Restoring is the edit screen's job. */
@Composable
internal fun ArchivedCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Text(
            text = stringResource(R.string.edit_archived_body),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(16.dp),
        )
    }
}
