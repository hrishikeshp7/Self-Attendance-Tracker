# Folder Feature Testing Guide

## Overview
This document describes the folder/subject management feature and how to test it.

## Changes Made

### 1. Tab-Based Navigation
- The Subjects screen now displays folders as tabs
- The first tab is always "All" showing top-level subjects (subjects not in any folder)
- Each created folder appears as an additional tab
- Clicking on a folder tab shows only subjects within that folder

### 2. Adding Subjects to Folders
- When viewing a folder tab, clicking the + button automatically adds the subject to that folder
- The dialog title changes to show "Add Subject to [Folder Name]"
- The folder toggle switch is hidden when adding to a specific folder
- When in the "All" tab, you can still create folders or top-level subjects

### 3. Technical Implementation
- Updated `SubjectsScreen` to:
  - Filter subjects into folders and top-level subjects
  - Manage tab selection state
  - Display appropriate subjects based on selected tab
  - Pass selected folder context to the add dialog
  
- Updated `AddSubjectDialog` to:
  - Accept folders list and selected folder
  - Show/hide folder toggle based on context
  - Update dialog title based on context
  - Pass parentId when adding subject to folder

- Updated `AttendanceApp` to:
  - Handle the new callback signature with parentId
  - Call `addSubSubject` when parentId is provided
  - Call `addSubject` for top-level subjects

## Testing Instructions

### Test Case 1: Create a Folder
1. Open the app
2. Navigate to "Subjects" tab
3. Tap the + button
4. Toggle "Create as folder" switch to ON
5. Enter folder name (e.g., "Pathology")
6. Tap "Add"
7. **Expected**: A new tab appears with the folder name

### Test Case 2: Add Subject to Folder
1. Navigate to the folder tab you just created
2. Tap the + button
3. **Expected**: Dialog title shows "Add Subject to [Folder Name]"
4. **Expected**: Folder toggle is not visible
5. Enter subject name (e.g., "Pathology Lecture")
6. Set required attendance percentage
7. Tap "Add"
8. **Expected**: Subject appears in the folder tab

### Test Case 3: Add Multiple Subjects to Same Folder
1. While in the folder tab, tap + button again
2. Add another subject (e.g., "Pathology Lab")
3. **Expected**: Both subjects appear in the folder tab
4. Switch to "All" tab
5. **Expected**: Neither subject appears in "All" tab (they're in the folder)

### Test Case 4: Add Top-Level Subject
1. Navigate to "All" tab
2. Tap the + button
3. Toggle "Create as folder" to OFF
4. Enter subject name (e.g., "Mathematics")
5. Tap "Add"
6. **Expected**: Subject appears in "All" tab only

### Test Case 5: Edit Subject in Folder
1. Navigate to a folder tab
2. Tap Edit icon on a subject
3. Modify the subject details
4. Tap "Save"
5. **Expected**: Changes are reflected in the folder view

### Test Case 6: Delete Subject from Folder
1. Navigate to a folder tab
2. Tap Delete icon on a subject
3. Confirm deletion
4. **Expected**: Subject is removed from the folder

### Test Case 7: Empty Folder State
1. Navigate to a folder tab with no subjects
2. **Expected**: See message "No subjects in this folder"
3. **Expected**: See instruction "Tap + to add a subject to this folder"

### Test Case 8: Multiple Folders
1. Create 3-4 different folders
2. Add subjects to each folder
3. **Expected**: All folder tabs are visible (use ScrollableTabRow)
4. **Expected**: Each tab shows only its own subjects
5. **Expected**: Can scroll tabs horizontally if many folders exist

## Data Model

The existing `Subject` entity already supports the folder feature:
- `isFolder: Boolean` - true for folders, false for subjects
- `parentSubjectId: Long?` - null for top-level items, folder ID for subjects in folders

## Known Behavior

1. Folders themselves don't have attendance tracking (they're just containers)
2. Subjects in folders are excluded from the "All" tab (by design)
3. HomeScreen shows only actual subjects (not folders) for attendance marking
4. Deleting a folder doesn't auto-delete subjects within it (database cascade rules apply)

## Future Enhancements (Not Implemented)

- Drag and drop subjects between folders
- Move existing subjects into folders
- Nested folders (folders within folders)
- Folder-level statistics (aggregate attendance from all subjects)
