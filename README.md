# Attendance Tracker

An Android application for tracking and managing class attendance. Built with Kotlin, Jetpack Compose, and Room Database.

[![Build and Release APK](https://github.com/hrishikeshp7/web-sf-hf/actions/workflows/build-release.yml/badge.svg)](https://github.com/hrishikeshp7/web-sf-hf/actions/workflows/build-release.yml)

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

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Database**: Room (SQLite)
- **Architecture**: MVVM (Model-View-ViewModel)
- **Navigation**: Jetpack Navigation Compose
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
git clone https://github.com/hrishikeshp7/web-sf-hf.git
cd web-sf-hf
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

## Download

You can download the latest APK from the [Releases page](https://github.com/hrishikeshp7/web-sf-hf/releases).

### Automated Releases
APK releases are automatically created:
- Via manual workflow dispatch from the Actions tab

## Author

Made with ❤️ by [hrishikeshp7](https://github.com/hrishikeshp7)

## License

This project is open source and available under the MIT License.

