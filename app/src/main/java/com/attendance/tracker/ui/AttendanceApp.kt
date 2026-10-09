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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import com.attendance.tracker.ui.screens.schedule.WeeklyCalendarScreen
import com.attendance.tracker.ui.screens.settings.CalendarSyncScreen
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
        // Each screen's own Scaffold/TopAppBar applies the status-bar inset; applying it here
        // too stacked two insets and left a big empty band at the top.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        // At large font scales the labels wrap mid-word, so show only the selected one
                        alwaysShowLabel = LocalDensity.current.fontScale <= 1.3f,
                        label = { Text(item.label, maxLines = 1, softWrap = false) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true,
                        onClick = {
                            // From a sub-screen (e.g. calendar, about) with this tab already in the
                            // back stack, pop back to it. navigate+restoreState would re-restore the
                            // sub-screen saved under this tab and the tab root would never show.
                            val onTabRoot = bottomNavItems.any { it.screen.route == currentDestination?.route }
                            if (!onTabRoot && navController.popBackStack(item.screen.route, false)) return@NavigationBarItem
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
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
            // No animation for bottom-tab switches — instant response
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable(Screen.Home.route) {
                // Home marks LocalDate.now(); make sure the cards show that same day even if
                // the app was left open past midnight.
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshToday()
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                HomeScreen(
                    subjects = subjects,
                    allSubjects = subjectsMap,
                    todayAttendance = todayAttendance,
                    scheduleEntries = scheduleEntries,
                    onMarkAttendance = { subjectId, status ->
                        viewModel.markAttendance(subjectId, status)
                    },
                    onAddExtraClass = { subjectId, status ->
                        viewModel.addExtraClass(subjectId, status)
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
                    onAddExtraClass = { status, date ->
                        viewModel.addExtraClass(subjectId, status, date)
                    },
                    onClearAttendance = { date ->
                        viewModel.clearAttendance(subjectId, date)
                    },
                    onSetDayCounts = { date, present, absent ->
                        viewModel.setDayCounts(subjectId, date, present, absent)
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
                    initialEditSubject = subjectToEdit,
                    openAddDialog = showAddSubjectOnSubjectsScreen,
                    onInitialActionConsumed = {
                        subjectToEdit = null
                        showAddSubjectOnSubjectsScreen = false
                    }
                )
            }

            composable(Screen.Schedule.route) {
                WeeklyCalendarScreen(
                    subjects = subjects,
                    allSubjects = subjectsMap,
                    scheduleEntries = scheduleEntries,
                    onAddLecture = { subjectId, day, start, end ->
                        viewModel.addLectureSlot(subjectId, day, start, end)
                    },
                    onUpdateLecture = { entry, subjectId, day, start, end ->
                        viewModel.updateLectureSlot(entry, subjectId, day, start, end)
                    },
                    onDeleteLecture = { entry ->
                        viewModel.removeScheduleEntry(entry)
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToAbout = {
                        navController.navigate(Screen.About.route)
                    },
                    onNavigateToCustomizations = {
                        navController.navigate(Screen.Customizations.route)
                    },
                    onNavigateToBackupRestore = {
                        navController.navigate(Screen.BackupRestore.route)
                    },
                    onNavigateToCalendarSync = {
                        navController.navigate(Screen.CalendarSync.route)
                    }
                )
            }

            composable(
                Screen.CalendarSync.route,
                enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(200)) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(200)) }
            ) {
                CalendarSyncScreen(
                    scheduleEntries = scheduleEntries,
                    allSubjects = subjectsMap,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(
                Screen.Customizations.route,
                enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(200)) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(200)) }
            ) {
                com.attendance.tracker.ui.screens.customizations.CustomizationsScreen(
                    currentThemeMode = themePreference?.themeMode ?: com.attendance.tracker.data.model.ThemeMode.SYSTEM,
                    onThemeModeChange = { mode ->
                        viewModel.updateThemeMode(mode)
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
