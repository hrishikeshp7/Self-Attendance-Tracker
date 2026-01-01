package com.attendance.tracker.ui

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Calendar : Screen("calendar")
    data object Subjects : Screen("subjects")
    data object Schedule : Screen("schedule")
    data object Settings : Screen("settings")
    data object Customizations : Screen("customizations")
    data object About : Screen("about")
}
