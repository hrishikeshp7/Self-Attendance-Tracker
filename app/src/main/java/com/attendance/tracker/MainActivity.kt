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
import com.attendance.tracker.ui.AttendanceApp
import com.attendance.tracker.ui.AttendanceViewModel
import com.attendance.tracker.ui.theme.AttendanceTrackerTheme
import com.attendance.tracker.utils.AppShortcutsHelper
import com.attendance.tracker.utils.NotificationHelper

class MainActivity : ComponentActivity() {
    
    // Permission request launcher
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Check if critical permissions were denied
        val notificationsDenied = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] == false
        } else {
            false
        }
        
        // Note: We don't block functionality if permissions are denied.
        // Features requiring permissions will handle their own permission checks.
        // This initial request is to improve first-time user experience.
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request necessary permissions
        requestNecessaryPermissions()
        
        // Initialize notification channels
        NotificationHelper.createNotificationChannels(this)
        
        // Initialize app shortcuts
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            AppShortcutsHelper.updateShortcuts(this)
        }
        
        // Handle shortcut intent
        val shortcutAction = intent.getStringExtra("shortcut_action")
        
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
                    AttendanceApp(
                        viewModel = viewModel,
                        shortcutAction = shortcutAction
                    )
                }
            }
        }
    }
    
    private fun requestNecessaryPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        
        // Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        // Calendar permissions
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CALENDAR
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.READ_CALENDAR)
        }
        
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_CALENDAR
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.WRITE_CALENDAR)
        }
        
        // Request permissions if needed
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}
