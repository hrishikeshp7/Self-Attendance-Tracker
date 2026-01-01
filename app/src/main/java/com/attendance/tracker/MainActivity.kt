package com.attendance.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.attendance.tracker.ui.AttendanceApp
import com.attendance.tracker.ui.AttendanceViewModel
import com.attendance.tracker.ui.theme.AttendanceTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create notification channel
        com.attendance.tracker.notification.AttendanceNotificationManager.createNotificationChannel(this)
        
        setContent {
            val viewModel: AttendanceViewModel = viewModel()
            val themePreference by viewModel.themePreference.collectAsState(initial = null)
            
            val themeMode = themePreference?.themeMode ?: com.attendance.tracker.data.model.ThemeMode.SYSTEM
            val customPrimary = themePreference?.customPrimaryColor?.let { Color(it.toULong()) }
            val customSecondary = themePreference?.customSecondaryColor?.let { Color(it.toULong()) }
            
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
}
