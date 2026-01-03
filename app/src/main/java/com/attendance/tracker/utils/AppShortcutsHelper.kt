package com.attendance.tracker.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import com.attendance.tracker.MainActivity
import com.attendance.tracker.R

/**
 * Helper for managing app shortcuts
 */
object AppShortcutsHelper {
    
    /**
     * Create and update dynamic app shortcuts
     */
    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun updateShortcuts(context: Context) {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
        
        val shortcuts = mutableListOf<ShortcutInfo>()
        
        // Mark Today's Attendance shortcut
        val markAttendanceIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("shortcut_action", "mark_attendance")
        }
        
        val markAttendanceShortcut = ShortcutInfo.Builder(context, "mark_attendance")
            .setShortLabel("Mark Attendance")
            .setLongLabel("Mark Today's Attendance")
            .setIcon(Icon.createWithResource(context, R.drawable.ic_launcher_foreground))
            .setIntent(markAttendanceIntent)
            .build()
        
        shortcuts.add(markAttendanceShortcut)
        
        // Open Calendar shortcut
        val calendarIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("shortcut_action", "open_calendar")
        }
        
        val calendarShortcut = ShortcutInfo.Builder(context, "open_calendar")
            .setShortLabel("Calendar")
            .setLongLabel("Open Calendar View")
            .setIcon(Icon.createWithResource(context, R.drawable.ic_launcher_foreground))
            .setIntent(calendarIntent)
            .build()
        
        shortcuts.add(calendarShortcut)
        
        // Add Subje shortcut
        val addSubjectIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra("shortcut_action", "add_subject")
        }
        
        val addSubjectShortcut = ShortcutInfo.Builder(context, "add_subject")
            .setShortLabel("Add Subject")
            .setLongLabel("Add New Subject")
            .setIcon(Icon.createWithResource(context, R.drawable.ic_launcher_foreground))
            .setIntent(addSubjectIntent)
            .build()
        
        shortcuts.add(addSubjectShortcut)
        
        // Set dynamic shortcuts
        shortcutManager.dynamicShortcuts = shortcuts
    }
    
    /**
     * Remove all dynamic shortcuts
     */
    @RequiresApi(Build.VERSION_CODES.N_MR1)
    fun removeAllShortcuts(context: Context) {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
        shortcutManager.removeAllDynamicShortcuts()
    }
}
