package com.attendance.tracker.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.attendance.tracker.data.model.AttendanceStatus
import com.attendance.tracker.ui.screens.calendar.CalendarScreen
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
    BottomNavItem(Screen.Calendar, Icons.Default.CalendarMonth, "Calendar"),
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
    val scheduleEntries by viewModel.scheduleEntries.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val todayAttendance by viewModel.todayAttendance.collectAsState()
    val attendanceRecords by viewModel.attendanceRecords.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()

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
                            navController.navigate(item.screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
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
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    subjects = subjects,
                    todayAttendance = todayAttendance,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onMarkAttendance = { subjectId, status ->
                        viewModel.markAttendance(subjectId, status)
                    },
                    onUndo = { viewModel.undo() },
                    onRedo = { viewModel.redo() },
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
                    }
                )
            }

            composable(Screen.Calendar.route) {
                // Load attendance for selected month when entering calendar screen
                LaunchedEffect(selectedMonth) {
                    viewModel.loadAttendanceForMonth(selectedMonth)
                }

                CalendarScreen(
                    selectedMonth = selectedMonth,
                    selectedDate = selectedDate,
                    attendanceRecords = attendanceRecords,
                    subjects = subjects,
                    onDateSelected = { date ->
                        viewModel.setSelectedDate(date)
                    },
                    onMonthChanged = { month ->
                        viewModel.setSelectedMonth(month)
                    },
                    onMarkAttendance = { subjectId, status, date ->
                        viewModel.markAttendance(subjectId, status, date)
                    }
                )
            }

            composable(Screen.Subjects.route) {
                SubjectsScreen(
                    subjects = allSubjectsIncludingFolders,
                    onAddSubject = { name, required ->
                        viewModel.addSubject(name, required)
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
                    onUpdateRequiredAttendance = { subjectId, required ->
                        viewModel.updateRequiredAttendance(subjectId, required)
                    }
                )
            }
        }
    }
}
