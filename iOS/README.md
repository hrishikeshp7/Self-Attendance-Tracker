# Attendance Tracker - iOS

An iOS application for tracking and managing class attendance. Built with Swift, SwiftUI, and SwiftData.

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
- Undo/Redo support for attendance marking

### 🌙 Dark Mode Support
- Automatic dark mode based on system settings
- Beautiful SwiftUI styling in both light and dark modes

### 📅 Calendar View
- Monthly calendar with attendance history
- Color-coded days showing attendance status
- View detailed attendance records for any selected day
- Easy month navigation
- Mark attendance for past dates

### 📚 Subject Management
- Add new subjects with custom names
- Set required attendance percentage for each subject (default: 75%)
- Edit subject details including:
  - Subject name
  - Required attendance percentage
  - Present/Absent lecture counts
- Delete subjects with confirmation
- **Folder organization** - Group subjects into folders (like "Pathology")

### 📆 Weekly Schedule
- Set which subjects occur on which days of the week
- Toggle subjects on/off for each day
- Visual schedule overview with swipeable day tabs

### ⚙️ Settings
- View and modify required attendance baseline for each subject
- Individual settings per subject
- Preview how current attendance compares to requirements

## Tech Stack

- **Language**: Swift 5.9+
- **UI Framework**: SwiftUI
- **Database**: SwiftData
- **Architecture**: MVVM (Model-View-ViewModel)
- **Minimum iOS**: iOS 17.0
- **Target Devices**: iPhone and iPad

## Project Structure

```
iOS/AttendanceTracker/AttendanceTracker/
├── Models/
│   ├── Subject.swift               # Subject data model
│   ├── AttendanceRecord.swift      # Attendance record model
│   └── ScheduleEntry.swift         # Schedule entry model
├── ViewModels/
│   └── AttendanceViewModel.swift   # Main ViewModel with business logic
├── Views/
│   ├── Components/
│   │   ├── SubjectCard.swift       # Reusable subject card component
│   │   ├── AttendanceChart.swift   # Pie chart component
│   │   ├── CalendarView.swift      # Custom calendar component
│   │   └── GitHubFooter.swift      # Footer with GitHub link
│   ├── Home/
│   │   └── HomeScreen.swift        # Home screen
│   ├── Calendar/
│   │   └── CalendarScreen.swift    # Calendar screen
│   ├── Subjects/
│   │   └── SubjectsScreen.swift    # Subjects management screen
│   ├── Schedule/
│   │   └── ScheduleScreen.swift    # Weekly schedule screen
│   └── Settings/
│       └── SettingsScreen.swift    # Settings screen
├── Theme/
│   └── AppColors.swift             # App color definitions
├── AttendanceTrackerApp.swift      # Main app entry point
└── ContentView.swift               # Main content view with tab navigation
```

## Building the Project

### Prerequisites
- Xcode 15.0 or newer
- macOS Sonoma or newer
- iOS 17.0+ SDK

### Build Steps

1. Clone the repository:
```bash
git clone https://github.com/hrishikeshp7/web-sf-hf.git
cd web-sf-hf/iOS/AttendanceTracker
```

2. Open the project in Xcode:
```bash
open AttendanceTracker.xcodeproj
```

3. Select your target device or simulator

4. Build and run (⌘R)

## Usage Guide

### Adding Your First Subject
1. Open the app
2. Tap the **+** button in the navigation bar
3. Enter the subject name and required attendance percentage
4. Tap "Add"

### Marking Daily Attendance
1. On the home screen, each subject shows three buttons: Present, Absent, No Class
2. Tap the appropriate button for each subject
3. The attendance statistics update automatically
4. Use the Undo/Redo buttons to correct mistakes

### Setting Up Weekly Schedule
1. Navigate to the "Schedule" tab
2. Select a day of the week from the tabs
3. Toggle on/off which subjects occur on that day

### Viewing Attendance History
1. Navigate to the "Calendar" tab
2. Browse through months using the arrow buttons
3. Tap on any date to see attendance records for that day
4. Color indicators show:
   - 🟢 Green: At least one class marked as Present
   - 🔴 Red: At least one class marked as Absent
   - ⚫ Gray: No Class marked

### Creating Folders
1. Navigate to the "Subjects" tab
2. Tap the + button
3. Toggle "Create as folder" switch ON
4. Enter folder name (e.g., "Pathology")
5. Tap "Add"
6. Tap on the folder to navigate into it and add subjects

### Editing Subject Settings
1. Navigate to the "Subjects" tab to edit subject details
2. Or use the "Settings" tab to adjust only the required attendance percentage

## Comparison with Android Version

This iOS app is a direct port of the Android Attendance Tracker app, maintaining feature parity:

| Feature | Android | iOS |
|---------|---------|-----|
| Language | Kotlin | Swift |
| UI Framework | Jetpack Compose | SwiftUI |
| Database | Room | SwiftData |
| Architecture | MVVM | MVVM |
| Dark Mode | ✅ | ✅ |
| Folders | ✅ | ✅ |
| Undo/Redo | ✅ | ✅ |
| Calendar | ✅ | ✅ |
| Schedule | ✅ | ✅ |

## Author

Made with ❤️ by [hrishikeshp7](https://github.com/hrishikeshp7)

## License

This project is open source and available under the MIT License.
