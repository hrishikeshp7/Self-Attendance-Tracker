package com.attendance.tracker.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.attendance.tracker.data.model.AttendanceStatus
import com.attendance.tracker.ui.screens.about.AboutScreen
import com.attendance.tracker.ui.screens.backup.BackupRestoreScreen
import com.attendance.tracker.ui.screens.calendar.SubjectCalendarScreen
import com.attendance.tracker.ui.screens.home.HomeScreen
import com.attendance.tracker.ui.screens.schedule.ScheduleScreen
import com.attendance.tracker.ui.screens.settings.SettingsScreen
import com.attendance.tracker.ui.screens.subjects.SubjectsScreen

data class BottomNavItem(
    val screen: Screen,
    val icon: ImageVector,
    val label: String
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, Icons.Default.Home, "Home"),
    BottomNavItem(Screen.Subjects, Icons.Default.Subject, "Subjects"),
    BottomNavItem(Screen.Schedule, Icons.Default.Schedule, "Schedule"),
    BottomNavItem(Screen.Settings, Icons.Default.Settings, "Settings")
)

@Composable
fun AttendanceApp(
    viewModel: AttendanceViewModel = viewModel()
) {
    val navController = rememberNavController()
    
    // Collect state from ViewModel
    val subjects by viewModel.subjects.collectAsState()
    val allSubjectsIncludingFolders by viewModel.allSubjectsIncludingFolders.collectAsState()
    val subjectsMap by viewModel.subjectsMap.collectAsState()
    val scheduleEntries by viewModel.scheduleEntries.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val todayAttendance by viewModel.todayAttendance.collectAsState()
    val attendanceRecords by viewModel.attendanceRecords.collectAsState()
    val themePreference by viewModel.themePreference.collectAsState()

    // Variables for navigation to subjects screen
    var showAddSubjectOnSubjectsScreen by remember { mutableStateOf(false) }
    var subjectToEdit by remember { mutableStateOf<com.attendance.tracker.data.model.Subject?>(null) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true,
                        onClick = {
                            // Navigate to the selected screen
                            navController.navigate(item.screen.route) {
                                // Pop up to the start destination to clear intermediate screens
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                    inclusive = false
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
            // No animation for bottom-tab switches — instant response
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    subjects = subjects,
                    allSubjects = subjectsMap,
                    todayAttendance = todayAttendance,
                    scheduleEntries = scheduleEntries,
                    onMarkAttendance = { subjectId, status ->
                        viewModel.markAttendance(subjectId, status)
                    },
                    onClearAttendance = { subjectId ->
                        viewModel.clearAttendance(subjectId)
                    },
                    onAddSubject = {
                        showAddSubjectOnSubjectsScreen = true
                        navController.navigate(Screen.Subjects.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    },
                    onEditSubject = { subject ->
                        subjectToEdit = subject
                        navController.navigate(Screen.Subjects.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    },
                    onSubjectClick = { subject ->
                        navController.navigate(Screen.SubjectCalendar.createRoute(subject.id))
                    }
                )
            }

            composable(
                route = Screen.SubjectCalendar.route,
                arguments = listOf(navArgument("subjectId") { type = NavType.LongType }),
                enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(200)) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(200)) }
            ) { backStackEntry ->
                val subjectId = backStackEntry.arguments?.getLong("subjectId") ?: return@composable
                val subject = subjectsMap[subjectId] ?: return@composable
                
                // Load attendance for selected month when entering calendar screen
                LaunchedEffect(selectedMonth) {
                    viewModel.loadAttendanceForMonth(selectedMonth)
                }

                SubjectCalendarScreen(
                    subject = subject,
                    allSubjects = subjectsMap,
                    selectedMonth = selectedMonth,
                    selectedDate = selectedDate,
                    attendanceRecords = attendanceRecords,
                    onDateSelected = { date ->
                        viewModel.setSelectedDate(date)
                    },
                    onMonthChanged = { month ->
                        viewModel.setSelectedMonth(month)
                    },
                    onMarkAttendance = { status, date ->
                        viewModel.markAttendance(subjectId, status, date)
                    },
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.Subjects.route) {
                SubjectsScreen(
                    subjects = allSubjectsIncludingFolders,
                    allSubjects = subjectsMap,
                    onAddSubject = { name, required, parentId ->
                        if (parentId != null) {
                            viewModel.addSubSubject(name, parentId, required)
                        } else {
                            viewModel.addSubject(name, required)
                        }
                    },
                    onAddFolder = { name ->
                        viewModel.addSubjectFolder(name)
                    },
                    onUpdateSubject = { subject ->
                        viewModel.updateSubject(subject)
                    },
                    onDeleteSubject = { subject ->
                        viewModel.deleteSubject(subject)
                    },
                    onUpdateAttendanceCounts = { id, present, absent ->
                        viewModel.updateAttendanceCounts(id, present, absent)
                    }
                )
            }

            composable(Screen.Schedule.route) {
                ScheduleScreen(
                    subjects = subjects,
                    allSubjects = subjectsMap,
                    scheduleEntries = scheduleEntries,
                    onAddScheduleEntry = { subjectId, day ->
                        viewModel.addScheduleEntry(subjectId, day)
                    },
                    onRemoveScheduleEntry = { entry ->
                        viewModel.removeScheduleEntry(entry)
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    subjects = subjects,
                    allSubjects = subjectsMap,
                    onUpdateRequiredAttendance = { subjectId, required ->
                        viewModel.updateRequiredAttendance(subjectId, required)
                    },
                    onNavigateToAbout = {
                        navController.navigate(Screen.About.route)
                    },
                    onNavigateToCustomizations = {
                        navController.navigate(Screen.Customizations.route)
                    },
                    onNavigateToBackupRestore = {
                        navController.navigate(Screen.BackupRestore.route)
                    }
                )
            }

            composable(
                Screen.Customizations.route,
                enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(200)) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(200)) }
            ) {
                val currentPrimary = themePreference?.customPrimaryColor?.let { 
                    androidx.compose.ui.graphics.Color(it.toInt()) 
                }
                val currentSecondary = themePreference?.customSecondaryColor?.let { 
                    androidx.compose.ui.graphics.Color(it.toInt()) 
                }
                
                com.attendance.tracker.ui.screens.customizations.CustomizationsScreen(
                    currentThemeMode = themePreference?.themeMode ?: com.attendance.tracker.data.model.ThemeMode.SYSTEM,
                    currentPrimaryColor = currentPrimary,
                    currentSecondaryColor = currentSecondary,
                    onThemeModeChange = { mode ->
                        viewModel.updateThemeMode(mode)
                    },
                    onCustomColorsChange = { primary, secondary ->
                        viewModel.updateCustomColors(primary, secondary)
                    },
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(
                Screen.About.route,
                enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(200)) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(200)) }
            ) {
                AboutScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(
                Screen.BackupRestore.route,
                enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(200)) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(200)) }
            ) {
                BackupRestoreScreen(
                    viewModel = viewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
