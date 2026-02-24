package com.attendance.tracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.attendance.tracker.notification.NotificationHelper
import com.attendance.tracker.notification.ReminderScheduler
import com.attendance.tracker.ui.AttendanceApp
import com.attendance.tracker.ui.AttendanceViewModel
import com.attendance.tracker.ui.theme.AttendanceTrackerTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            ReminderScheduler.scheduleDailyReminder(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NotificationHelper.createNotificationChannels(this)
        setupNotifications()

        setContent {
            val viewModel: AttendanceViewModel = viewModel()
            val themePreference by viewModel.themePreference.collectAsState(initial = null)
            
            val themeMode = themePreference?.themeMode ?: com.attendance.tracker.data.model.ThemeMode.SYSTEM
            val customPrimary = themePreference?.customPrimaryColor?.let { Color(it.toInt()) }
            val customSecondary = themePreference?.customSecondaryColor?.let { Color(it.toInt()) }
            
            AttendanceTrackerTheme(
                themeMode = themeMode,
                customPrimaryColor = customPrimary,
                customSecondaryColor = customSecondary
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AttendanceApp(viewModel = viewModel)
                }
            }
        }
    }

    private fun setupNotifications() {
        if (!NotificationHelper.areNotificationsEnabled(this)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    ReminderScheduler.scheduleDailyReminder(this)
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            ReminderScheduler.scheduleDailyReminder(this)
        }
    }
}
