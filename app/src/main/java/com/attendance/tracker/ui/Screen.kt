package com.attendance.tracker.ui

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Calendar : Screen("calendar")
    data object SubjectCalendar : Screen("subject_calendar/{subjectId}") {
        fun createRoute(subjectId: Long) = "subject_calendar/$subjectId"
    }
    data object Subjects : Screen("subjects")
    data object Schedule : Screen("schedule")
    data object Settings : Screen("settings")
    data object Customizations : Screen("customizations")
    data object About : Screen("about")
    data object ExportBackup : Screen("export_backup")
}
