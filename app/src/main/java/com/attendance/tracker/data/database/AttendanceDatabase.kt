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
    entities = [Subject::class, AttendanceRecord::class, ScheduleEntry::class, com.attendance.tracker.data.model.ThemePreference::class, com.attendance.tracker.data.model.NotificationPreference::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AttendanceDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun themePreferenceDao(): ThemePreferenceDao
    abstract fun notificationPreferenceDao(): NotificationPreferenceDao

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
                        enabled INTEGER NOT NULL DEFAULT 0,
                        startTimeHour INTEGER NOT NULL DEFAULT 9,
                        startTimeMinute INTEGER NOT NULL DEFAULT 0,
                        message TEXT NOT NULL DEFAULT 'Time to mark your attendance!'
                    )
                """)
                // Insert default notification preference
                db.execSQL("INSERT INTO notification_preferences (id, enabled, startTimeHour, startTimeMinute, message) VALUES (1, 0, 9, 0, 'Time to mark your attendance!')")
                
                // Add new columns to schedule_entries table for time periods and week variations
                db.execSQL("ALTER TABLE schedule_entries ADD COLUMN startTimeHour INTEGER NOT NULL DEFAULT 9")
                db.execSQL("ALTER TABLE schedule_entries ADD COLUMN startTimeMinute INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE schedule_entries ADD COLUMN endTimeHour INTEGER NOT NULL DEFAULT 10")
                db.execSQL("ALTER TABLE schedule_entries ADD COLUMN endTimeMinute INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE schedule_entries ADD COLUMN weekNumber INTEGER NOT NULL DEFAULT 0")
                
                // Drop the old unique index on (subjectId, dayOfWeek) since we now allow multiple entries per subject/day for different weeks
                db.execSQL("DROP INDEX IF EXISTS index_schedule_entries_subjectId_dayOfWeek")
                
                // Create new index that includes weekNumber
                db.execSQL("CREATE INDEX IF NOT EXISTS index_schedule_entries_subjectId_dayOfWeek_weekNumber ON schedule_entries(subjectId, dayOfWeek, weekNumber)")
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
