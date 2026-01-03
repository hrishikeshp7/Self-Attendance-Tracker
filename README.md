# Attendance Tracker

An Android application for tracking and managing class attendance. Built with Kotlin, Jetpack Compose, and Room Database.

[![Build and Release APK](https://github.com/hrishikeshp7/Self-Attendance-Tracker/actions/workflows/build-release.yml/badge.svg)](https://github.com/hrishikeshp7/Self-Attendance-Tracker/actions/workflows/build-release.yml)

## Features

### 📱 Home Screen
- View all subjects with their attendance statistics
- **Visual pie chart** showing attendance percentage at a glance
- Mark attendance for each subject with three options:
  - **Present** - Mark as attended
  - **Absent** - Mark as missed
  - **No Class** - Mark as no class scheduled
- Real-time attendance percentage calculation
- Visual indicators for attendance status (green for above required, red for below)

### 🌙 Dark Mode Support
- Automatic dark mode based on system settings
- Beautiful Material 3 theming in both light and dark modes
- Dynamic colors on Android 12+ devices

### 📅 Calendar View
- Monthly calendar with attendance history
- Color-coded days showing attendance status
- View detailed attendance records for any selected day
- Easy month navigation
- **Predictive analytics dashboard** showing:
  - Current and predicted semester-end attendance
  - Attendance streaks (current and longest)
  - Weekly trend graphs
  - Remaining classes and recommendations

### 📚 Subject Management
- Add new subjects with custom names
- Set required attendance percentage for each subject (default: 75%)
- Edit subject details including:
  - Subject name
  - Required attendance percentage
  - Present/Absent lecture counts
- Delete subjects with confirmation

### 📆 Weekly Schedule
- Set which subjects occur on which days of the week
- Toggle subjects on/off for each day
- Visual schedule overview

### ⚙️ Settings
- View and modify required attendance baseline for each subject
- Individual settings per subject
- Preview how current attendance compares to requirements
- **Push Notifications and Reminders**:
  - Enable/disable notifications for upcoming classes
  - Configurable reminder time (5-60 minutes before class)
  - Low attendance warnings when percentage drops below threshold
  - Customizable warning threshold percentage

### 📊 Predictive Analytics
- **Smart attendance predictions**:
  - Calculate how many classes needed to reach required attendance
  - Predict semester-end attendance based on schedule
  - Visualize attendance trends with weekly graphs
  - Track attendance streaks (current and best)
  - See remaining scheduled classes for the semester
- **Actionable insights**:
  - "Attend next X classes" recommendations
  - "You can skip X classes" safe bunking calculator
  - Visual trend indicators for performance tracking

### 📤 Data Export and Backup
- **Export attendance records**:
  - Export to CSV format for spreadsheet analysis
  - Export to PDF format for printing or sharing
  - Subject-wise summary export
  - Share exported files via Android's share intent
- **Cloud backup and restore**:
  - Secure backup to Google Drive
  - Easy restore from previous backups
  - Automatic database backup
  - Keep your data safe across devices

### 🏠 Home Screen Widgets
- **Multiple widget options**:
  - Compact widget for quick attendance overview
  - Large widget with visual statistics
  - Interactive widget with quick action buttons
- **Quick attendance marking**:
  - Mark Present/Absent/No Class directly from widget
  - Real-time updates with ViewModel integration
  - Beautiful Material 3 design

### 🚀 App Shortcuts
- **Quick actions from launcher**:
  - "Mark Today's Attendance" shortcut
  - "Open Calendar" shortcut
  - "Add New Subject" shortcut
- Long-press app icon to access shortcuts
- Fast access to common tasks

### 📆 Calendar Integration
- **Sync with device calendar**:
  - Export class schedule to device calendar
  - Create recurring events for weekly schedule
  - Import calendar events to auto-populate schedule
  - Customizable event duration and timing
- **Calendar permissions**:
  - Request READ_CALENDAR and WRITE_CALENDAR permissions
  - Secure access to calendar data
  - Proper error handling

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Database**: Room (SQLite)
- **Architecture**: MVVM (Model-View-ViewModel)
- **Navigation**: Jetpack Navigation Compose
- **Background Tasks**: WorkManager for notification scheduling
- **Widgets**: Glance for AppWidgets
- **Export**: iText7 for PDF generation
- **Cloud**: Google Drive API for backup/restore
- **Calendar**: Android Calendar Provider API
- **Minimum SDK**: 26 (Android 8.0)
- **Target SDK**: 36 (Android 16)

## Project Structure

```
app/src/main/java/com/attendance/tracker/
├── data/
│   ├── database/
│   │   ├── AttendanceDatabase.kt    # Room database configuration
│   │   ├── Converters.kt            # Type converters for Room
│   │   ├── AttendanceDao.kt         # DAO for attendance records
│   │   ├── ScheduleDao.kt           # DAO for schedule entries
│   │   └── SubjectDao.kt            # DAO for subjects
│   ├── model/
│   │   ├── Subject.kt               # Subject data model
│   │   ├── AttendanceRecord.kt      # Attendance record model
│   │   └── ScheduleEntry.kt         # Schedule entry model
│   └── repository/
│       └── AttendanceRepository.kt  # Repository pattern implementation
├── ui/
│   ├── components/
│   │   ├── SubjectCard.kt           # Reusable subject card component
│   │   └── CalendarView.kt          # Custom calendar component
│   ├── screens/
│   │   ├── home/HomeScreen.kt       # Home screen
│   │   ├── calendar/CalendarScreen.kt
│   │   ├── subjects/SubjectsScreen.kt
│   │   ├── schedule/ScheduleScreen.kt
│   │   └── settings/SettingsScreen.kt
│   ├── theme/
│   │   ├── Color.kt                 # App color definitions
│   │   ├── Theme.kt                 # Material theme setup
│   │   └── Type.kt                  # Typography definitions
│   ├── AttendanceApp.kt             # Main app composable with navigation
│   ├── AttendanceViewModel.kt       # Main ViewModel
│   └── Screen.kt                    # Navigation routes
└── MainActivity.kt                  # Main activity entry point
```

## Building the Project

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 17
- Android SDK 36

### Build Steps

1. Clone the repository:
```bash
git clone https://github.com/hrishikeshp7/Self-Attendance-Tracker.git
cd Self-Attendance-Tracker
```

2. Open the project in Android Studio

3. Sync Gradle files

4. Build and run on an emulator or physical device:
```bash
./gradlew assembleDebug
```

Or use Android Studio's Run button.

### Release Builds and Keystore Setup

To ensure that users can upgrade between releases without "app not installed - conflicts with other package" errors, all release builds **must be signed with the same keystore**. 

#### For Repository Maintainers

If you're building releases via GitHub Actions, configure the following secrets:

1. Generate a keystore file once (only needs to be done once):
```bash
keytool -genkeypair -v -keystore release.keystore -alias release \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass YourSecurePassword -keypass YourSecureKeyPassword \
  -dname "CN=AttendanceTracker, OU=Development, O=AttendanceTracker, L=City, ST=State, C=US"
```

2. Encode the keystore to base64:
```bash
base64 release.keystore > release.keystore.base64
```

3. Add the following secrets to your GitHub repository (Settings → Secrets and variables → Actions):
   - `RELEASE_KEYSTORE_BASE64`: Contents of `release.keystore.base64`
   - `RELEASE_KEYSTORE_PASSWORD`: The password you used for `-storepass`
   - `RELEASE_KEY_ALIAS`: The alias you used (e.g., "release")
   - `RELEASE_KEY_PASSWORD`: The password you used for `-keypass`

4. **Important**: Keep the original `release.keystore` file in a secure location as a backup. If you lose it, you won't be able to update the app for existing users.

#### For Local Release Builds

If building locally, you can:
1. Use the same keystore file and set environment variables:
```bash
export KEYSTORE_FILE=path/to/release.keystore
export KEYSTORE_PASSWORD=your_password
export KEY_ALIAS=release
export KEY_PASSWORD=your_key_password
./gradlew assembleRelease
```

2. Or let the build script use the default temporary keystore (not recommended for production).

## Usage Guide

### Adding Your First Subject
1. Open the app
2. Tap the **+** floating action button on the home screen
3. Enter the subject name and required attendance percentage
4. Tap "Add"

### Marking Daily Attendance
1. On the home screen, each subject shows three buttons: Present, Absent, No Class
2. Tap the appropriate button for each subject
3. The attendance statistics update automatically

### Setting Up Weekly Schedule
1. Navigate to the "Schedule" tab
2. Select a day of the week
3. Toggle on/off which subjects occur on that day

### Viewing Attendance History
1. Navigate to the "Calendar" tab
2. Browse through months using the arrow buttons
3. Tap on any date to see attendance records for that day
4. Color indicators show:
   - 🟢 Green: At least one class marked as Present
   - 🔴 Red: At least one class marked as Absent
   - ⚫ Gray: No Class marked

### Editing Subject Settings
1. Navigate to the "Subjects" tab to edit subject details
2. Or use the "Settings" tab to adjust only the required attendance percentage

### Using Home Screen Widgets
1. Long-press on home screen
2. Select "Widgets"
3. Find "Attendance Tracker" widgets
4. Choose from:
   - Compact widget for overview
   - Interactive widget for quick marking
   - Large widget for detailed stats
5. Drag the widget to your home screen
6. Use Present/Absent/No Class buttons on interactive widget to mark attendance

### Using App Shortcuts
1. Long-press the app icon on your launcher
2. Select quick action:
   - "Mark Today's Attendance"
   - "Open Calendar"
   - "Add New Subject"

### Exporting Data
1. Navigate to Settings → Export & Backup
2. Tap "Export to CSV/PDF"
3. Select format (CSV, PDF, or Summary)
4. Tap "Export & Share"
5. Choose app to share the exported file

### Cloud Backup
1. Navigate to Settings → Export & Backup
2. Tap "Backup" under Cloud Backup section
3. Sign in with your Google account
4. Your database will be backed up to Google Drive

### Restoring from Backup
1. Navigate to Settings → Export & Backup
2. Tap "Restore" under Cloud Backup section
3. Sign in with your Google account
4. Select a backup to restore
5. Confirm restoration (current data will be replaced)

### Calendar Sync
1. Navigate to Settings → Export & Backup
2. Tap "Export Schedule to Calendar"
3. Grant calendar permissions if prompted
4. Your class schedule will be exported as recurring events
5. Events are created for the next 3 months

## Download

You can download the latest APK from the [Releases page](https://github.com/hrishikeshp7/Self-Attendance-Tracker/releases).

### Automated Releases
APK releases are automatically created:
- Via manual workflow dispatch from the Actions tab

## Author

Made with ❤️ by [hrishikeshp7](https://github.com/hrishikeshp7)

## License

This project is open source and available under the MIT License.

