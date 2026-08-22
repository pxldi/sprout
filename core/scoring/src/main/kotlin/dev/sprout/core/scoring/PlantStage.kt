/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout.core.scoring

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
public fun HabitProgress.plantStage(): PlantStage = when {
    strength >= INGRAINED_STRENGTH && closedOccasions >= INGRAINED_OCCASIONS -> PlantStage.INGRAINED
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
