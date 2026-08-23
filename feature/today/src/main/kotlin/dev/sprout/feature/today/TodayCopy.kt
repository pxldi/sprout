/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.feature.today

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.sprout.core.scoring.PlantStage
import dev.sprout.core.ui.R

/*
 * Where the screen's typed values become sentences.
 *
 * Kept apart from the screen on purpose. Everything Today says about a habit is decided by the
 * ViewModel as a value carrying real numbers, and turned into words in exactly one place — so the
 * copy rules in docs/02-app-design.md can be reviewed by reading these two functions and the one
 * strings file, without reading a Compose tree.
 */

/** Maps a claim to its wording. The numbers travel with the value; only the sentence is here. */
@Composable
internal fun ShineLine.text(): String = when (this) {
    ShineLine.FirstEver -> stringResource(R.string.shine_first)
    is ShineLine.StageUp -> stringResource(
        when (stage) {
            PlantStage.SPROUT -> R.string.shine_stage_sprout
            PlantStage.SAPLING -> R.string.shine_stage_sapling
            PlantStage.TREE -> R.string.shine_stage_tree
            // SEED is where every habit starts, so nothing ever grows *into* it and the
            // generator cannot produce one. Worded for the last stage, which it can.
            PlantStage.INGRAINED, PlantStage.SEED -> R.string.shine_stage_ingrained
        },
    )
    is ShineLine.LongestRun -> pluralStringResource(R.plurals.shine_run, run, run)
    is ShineLine.BestWeek -> pluralStringResource(R.plurals.shine_week_best, completions, completions)
    is ShineLine.TimesThisWeek -> pluralStringResource(R.plurals.shine_week_count, times, times)
}

internal fun GentleNote.stringRes(): Int = when (this) {
    GentleNote.MISSED_YESTERDAY -> R.string.note_missed_yesterday
    GentleNote.REPAIRABLE -> R.string.note_repairable
    GentleNote.BOUNCED_BACK -> R.string.note_bounced_back
}
