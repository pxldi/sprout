/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.scoring

import java.time.LocalDate

/**
 * The growth metaphor the whole app is named for.
 *
 * A plant instead of a flame, deliberately: fire signals loss when it goes out, and Duolingo
 * found the flame misread in some markets. A plant that is only a sprout is not failing — it is
 * a sprout. Nothing here can regress to [SEED] from a single miss, because [HabitProgress.strength]
 * itself never resets.
 */
public enum class PlantStage { SEED, SPROUT, SAPLING, TREE, INGRAINED }

/**
 * [INGRAINED] requires both high strength *and* time served: the median habit takes 59–66 days to
 * become automatic (Lally 2010), so strength alone must not be able to buy it. A perfect fortnight
 * is a [TREE], and that is already worth saying.
 */
public fun HabitProgress.plantStage(): PlantStage = stageOf(strength, closedOccasions)

/**
 * The stage this habit had reached *before* the occasion containing [date] was judged.
 *
 * Exists so a screen can tell that a completion moved the plant on, which is the one moment worth
 * saying out loud. Read off the strength each occasion recorded as the scorer walked past it, so
 * it is the same arithmetic as [plantStage] rather than a second one that can drift from it.
 *
 * [PlantStage.SEED] when nothing preceded it: before the first occasion there was no plant.
 */
public fun HabitProgress.stageBefore(date: LocalDate): PlantStage {
    val index = occasions.indexOfFirst { date in it.occasion }
    if (index <= 0) return PlantStage.SEED
    val before = occasions.subList(0, index)
    return stageOf(
        strength = before.last().strength,
        closed = before.count { it.outcome != OccasionOutcome.OPEN },
    )
}

private fun stageOf(strength: Double, closed: Int): PlantStage = when {
    strength >= INGRAINED_STRENGTH && closed >= INGRAINED_OCCASIONS -> PlantStage.INGRAINED
    strength >= TREE_STRENGTH -> PlantStage.TREE
    strength >= SAPLING_STRENGTH -> PlantStage.SAPLING
    strength >= SPROUT_STRENGTH -> PlantStage.SPROUT
    else -> PlantStage.SEED
}

private const val SPROUT_STRENGTH = 15.0
private const val SAPLING_STRENGTH = 40.0
private const val TREE_STRENGTH = 70.0
private const val INGRAINED_STRENGTH = 85.0

/** The upper end of Lally's median time-to-automaticity. */
private const val INGRAINED_OCCASIONS = 66
