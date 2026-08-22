/*
 * Copyright (C) 2026 The Sprout contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package dev.sprout

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.sprout.feature.habit.CreateHabitRoute
import dev.sprout.feature.today.TodayRoute

private const val TODAY_ROUTE = "today"
private const val CREATE_HABIT_ROUTE = "habit/new"

@Composable
internal fun SproutNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = TODAY_ROUTE) {
        composable(TODAY_ROUTE) {
            TodayRoute(onAddHabit = { navController.navigate(CREATE_HABIT_ROUTE) })
        }
        composable(CREATE_HABIT_ROUTE) {
            // Popping *this* destination by name, rather than bare popBackStack(), which pops
            // whatever happens to be on top. onFinished can fire more than once — a system back
            // racing the exit transition, or a save landing as the user backs out — and a second
            // bare pop would take Today with it and leave the NavHost with an empty stack, which
            // renders as a blank white screen. Named and inclusive, a repeat call is a no-op.
            CreateHabitRoute(
                onFinished = { navController.popBackStack(CREATE_HABIT_ROUTE, inclusive = true) },
            )
        }
    }
}
