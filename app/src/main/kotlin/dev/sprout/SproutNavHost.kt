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
            // popBackStack, not navigate: finishing the flow returns to the Today already on the
            // stack, so its state — and its scroll position — survive.
            CreateHabitRoute(onFinished = { navController.popBackStack() })
        }
    }
}
