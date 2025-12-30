# Fix: Subjects Not Showing in Folders

## Issue Description
After creating a folder and adding subjects to it, the subjects were not visible in the folder view. The folder displayed "No subjects in this folder" even after successfully adding subjects to it.

## Root Cause
The issue was in `AttendanceViewModel.kt` where the `allSubjectsIncludingFolders` StateFlow was using `repository.topLevelSubjects` which only fetches subjects where `parentSubjectId IS NULL`.

When a subject is added to a folder:
1. The subject is created with `parentSubjectId = folderId` (NOT NULL)
2. The query `getTopLevelSubjects()` excludes subjects with non-null `parentSubjectId`
3. Therefore, subjects inside folders were never fetched from the database
4. When the user clicked on a folder, `SubjectsScreen` tried to filter subjects with `parentSubjectId == folderId`
5. Since those subjects were never fetched, the filter found nothing → "No subjects in this folder"

## The Fix
**File**: `app/src/main/java/com/attendance/tracker/ui/AttendanceViewModel.kt`

**Change**: Line 39
```kotlin
// Before
val allSubjectsIncludingFolders: StateFlow<List<Subject>> = repository.topLevelSubjects
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

// After
val allSubjectsIncludingFolders: StateFlow<List<Subject>> = repository.allSubjects
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

**Why This Works**:
- `getAllSubjects()` fetches ALL subjects from the database (folders, top-level subjects, and nested subjects)
- The `SubjectsScreen` already has correct filtering logic:
  - When viewing top-level: filters for `parentSubjectId == null`
  - When viewing a folder: filters for `parentSubjectId == currentFolder.id`
- Now that all subjects are available, the filtering works correctly
- Home screen is unaffected because it uses `repository.actualSubjects` which filters by `isFolder = false`

## Testing Instructions

### Prerequisites
1. Build and run the app on an Android device or emulator

### Test Case 1: Create Folder and Add Subjects
1. Open the app and navigate to the **Subjects** tab
2. Tap the **+** button
3. Toggle **"Create as folder"** ON
4. Enter folder name: "Test Folder"
5. Tap **Add**
6. **Expected**: Folder appears in the list with a folder icon (📁)

7. Tap on the "Test Folder" to open it
8. **Expected**: You navigate into the folder view with a back button in the toolbar
9. **Expected**: Message says "No subjects in this folder"

10. Tap the **+** button (while inside the folder)
11. **Expected**: Dialog title shows "Add Subject to Test Folder"
12. Enter subject name: "Test Subject 1"
13. Enter required attendance: 75
14. Tap **Add**
15. **Expected**: ✅ Subject now appears in the folder view (this was previously broken)

16. Tap **+** again and add another subject: "Test Subject 2"
17. **Expected**: ✅ Both subjects appear in the folder

### Test Case 2: Verify Home Screen Shows Folder Subjects
1. Navigate to the **Home** tab
2. **Expected**: ✅ Both "Test Subject 1" and "Test Subject 2" appear for attendance marking
3. **Expected**: The folder itself ("Test Folder") does NOT appear on home (correct behavior)

### Test Case 3: Verify Top-Level Subjects Still Work
1. Navigate to the **Subjects** tab
2. Tap the back button to exit the folder view (return to top level)
3. Tap the **+** button
4. Toggle **"Create as folder"** OFF
5. Enter subject name: "Top Level Subject"
6. Enter required attendance: 80
7. Tap **Add**
8. **Expected**: ✅ Subject appears in the top-level list
9. **Expected**: The folder and the top-level subject both appear in the list

### Test Case 4: Edit and Delete Operations
1. While in the folder view, tap **Edit** on "Test Subject 1"
2. Change the name to "Test Subject 1 - Modified"
3. Tap **Save**
4. **Expected**: ✅ Name is updated correctly

5. Tap **Delete** on "Test Subject 2"
6. Confirm deletion
7. **Expected**: ✅ Subject is removed from the folder

8. Navigate to **Home** tab
9. **Expected**: ✅ Only "Test Subject 1 - Modified" appears (Test Subject 2 is gone)

### Test Case 5: Multiple Folders
1. Navigate to **Subjects** tab (top level)
2. Create another folder: "Folder 2"
3. Open "Folder 2"
4. Add a subject: "Subject in Folder 2"
5. **Expected**: ✅ Subject appears in Folder 2

6. Navigate back to top level
7. Open "Test Folder"
8. **Expected**: ✅ Only subjects from Test Folder appear (not subjects from Folder 2)

## Impact Analysis

### What Changed
- ✅ Subjects in folders now display correctly
- ✅ All existing functionality preserved
- ✅ Home screen behavior unchanged (shows all non-folder subjects)
- ✅ Top-level subject management unchanged
- ✅ Folder creation unchanged

### What Didn't Change
- Database schema (no migrations needed)
- UI components (no visual changes)
- Navigation flow (no changes to user interaction)
- Any other screens (Home, Calendar, Schedule, Settings)

## Technical Details

### Database Queries
The fix leverages existing queries in `SubjectDao.kt`:

```kotlin
// Returns ALL subjects (used by fix)
@Query("SELECT * FROM subjects ORDER BY name ASC")
fun getAllSubjects(): Flow<List<Subject>>

// Returns only top-level subjects (was incorrectly used before)
@Query("SELECT * FROM subjects WHERE parentSubjectId IS NULL ORDER BY name ASC")
fun getTopLevelSubjects(): Flow<List<Subject>>

// Returns only actual subjects (used by Home screen)
@Query("SELECT * FROM subjects WHERE isFolder = 0 ORDER BY name ASC")
fun getActualSubjects(): Flow<List<Subject>>
```

### Data Flow
```
Database → SubjectDao.getAllSubjects() 
         → AttendanceRepository.allSubjects
         → AttendanceViewModel.allSubjectsIncludingFolders
         → AttendanceApp.allSubjectsIncludingFolders
         → SubjectsScreen (with filtering)
```

### Filtering Logic in SubjectsScreen
```kotlin
val displaySubjects = if (currentFolder == null) {
    // Top-level view: show only items with no parent
    subjects.filter { it.parentSubjectId == null }
} else {
    // Folder view: show only items with this parent
    subjects.filter { it.parentSubjectId == currentFolder?.id }
}
```

This filtering logic was already correct, it just needed all subjects to be available to filter!

## Summary
This was a **one-line fix** that resolved the issue by ensuring all subjects (including those in folders) are fetched from the database and made available to the SubjectsScreen for proper filtering. The existing UI and filtering logic already handled the display correctly once the data was available.

**Status**: ✅ Fixed and ready for testing
