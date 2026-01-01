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
    entities = [Subject::class, AttendanceRecord::class, ScheduleEntry::class, com.attendance.tracker.data.model.ThemePreference::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AttendanceDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun themePreferenceDao(): ThemePreferenceDao

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

        fun getDatabase(context: Context): AttendanceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AttendanceDatabase::class.java,
                    "attendance_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
