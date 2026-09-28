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
    version = 5,
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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add count column to attendance_records table
                db.execSQL("ALTER TABLE attendance_records ADD COLUMN count INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add start/end time to schedule_entries (stored as minutes since midnight,
                // matching Converters.fromLocalTime). Existing rows default to 9–10 AM —
                // the new weekly calendar view is where a user re-sets the real time.
                db.execSQL("ALTER TABLE schedule_entries ADD COLUMN startTime INTEGER NOT NULL DEFAULT 540")
                db.execSQL("ALTER TABLE schedule_entries ADD COLUMN endTime INTEGER NOT NULL DEFAULT 600")

                // The (subjectId, dayOfWeek) index used to be UNIQUE, limiting a subject to one
                // slot per day. Multiple slots per day are now allowed (e.g. lecture + lab), so
                // the index is recreated without the uniqueness constraint, keeping the same
                // auto-generated name Room expects for this column list.
                db.execSQL("DROP INDEX IF EXISTS index_schedule_entries_subjectId_dayOfWeek")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_schedule_entries_subjectId_dayOfWeek " +
                        "ON schedule_entries (subjectId, dayOfWeek)"
                )
            }
        }

        fun getDatabase(context: Context): AttendanceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AttendanceDatabase::class.java,
                    "attendance_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
