package com.attendance.tracker.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.attendance.tracker.data.model.AttendanceRecord
import com.attendance.tracker.data.model.ScheduleEntry
import com.attendance.tracker.data.model.Subject

@Database(
    entities = [Subject::class, AttendanceRecord::class, ScheduleEntry::class, com.attendance.tracker.data.model.ThemePreference::class, com.attendance.tracker.data.model.NotificationPreferences::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AttendanceDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun themePreferenceDao(): ThemePreferenceDao
    abstract fun notificationPreferencesDao(): NotificationPreferencesDao

    companion object {
        @Volatile
        private var INSTANCE: AttendanceDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new columns to subjects table
                db.execSQL("ALTER TABLE subjects ADD COLUMN parentSubjectId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE subjects ADD COLUMN isFolder INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create theme_preferences table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS theme_preferences (
                        id INTEGER PRIMARY KEY NOT NULL,
                        themeMode TEXT NOT NULL,
                        customPrimaryColor INTEGER,
                        customSecondaryColor INTEGER
                    )
                """)
                // Insert default theme preference
                db.execSQL("INSERT INTO theme_preferences (id, themeMode) VALUES (1, 'SYSTEM')")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create notification_preferences table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS notification_preferences (
                        id INTEGER PRIMARY KEY NOT NULL,
                        notificationsEnabled INTEGER NOT NULL DEFAULT 1,
                        reminderMinutesBefore INTEGER NOT NULL DEFAULT 15,
                        lowAttendanceWarnings INTEGER NOT NULL DEFAULT 1,
                        lowAttendanceThreshold INTEGER NOT NULL DEFAULT 75
                    )
                """)
                // Insert default notification preferences
                db.execSQL("INSERT INTO notification_preferences (id, notificationsEnabled, reminderMinutesBefore, lowAttendanceWarnings, lowAttendanceThreshold) VALUES (1, 1, 15, 1, 75)")
            }
        }

        fun getDatabase(context: Context): AttendanceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AttendanceDatabase::class.java,
                    "attendance_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
