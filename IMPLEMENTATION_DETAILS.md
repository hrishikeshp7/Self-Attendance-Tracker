# Folder Feature - Before and After

## Problem Statement
Users could create folders but couldn't add subjects to them. Folders appeared in a flat list with no clear way to organize subjects within them.

## Before (Previous Behavior)

### Subjects Screen Layout
```
┌─────────────────────────────────────┐
│  Manage Subjects              [+]   │
├─────────────────────────────────────┤
│                                     │
│  ┌─────────────────────────────┐   │
│  │ Pathology (Folder)          │   │
│  │ [Edit] [Delete]             │   │
│  └─────────────────────────────┘   │
│                                     │
│  ┌─────────────────────────────┐   │
│  │ Mathematics                 │   │
│  │ Present: 10 | Absent: 2     │   │
│  │ 83.3% (Required: 75%)       │   │
│  │ [Edit] [Delete]             │   │
│  └─────────────────────────────┘   │
│                                     │
│  ┌─────────────────────────────┐   │
│  │ Physics                     │   │
│  │ Present: 15 | Absent: 3     │   │
│  │ 83.3% (Required: 75%)       │   │
│  │ [Edit] [Delete]             │   │
│  └─────────────────────────────┘   │
│                                     │
└─────────────────────────────────────┘
```

### Issues
- Folders shown in flat list with subjects
- No way to see which subjects belong to which folder
- No way to add subjects TO a folder
- Clicking + button always created top-level subjects
- Folders were essentially non-functional

## After (New Behavior)

### Subjects Screen Layout with Tabs
```
┌─────────────────────────────────────┐
│  Manage Subjects              [+]   │
├─────────────────────────────────────┤
│ [ All ] [ Pathology ] [ Anatomy ]   │ ← Folder Tabs
├─────────────────────────────────────┤
│                                     │
│  When "All" tab is selected:        │
│  ┌─────────────────────────────┐   │
│  │ Mathematics                 │   │
│  │ Present: 10 | Absent: 2     │   │
│  │ 83.3% (Required: 75%)       │   │
│  │ [Edit] [Delete]             │   │
│  └─────────────────────────────┘   │
│                                     │
│  ┌─────────────────────────────┐   │
│  │ Physics                     │   │
│  │ Present: 15 | Absent: 3     │   │
│  │ 83.3% (Required: 75%)       │   │
│  │ [Edit] [Delete]             │   │
│  └─────────────────────────────┘   │
│                                     │
└─────────────────────────────────────┘
```

```
┌─────────────────────────────────────┐
│  Manage Subjects              [+]   │
├─────────────────────────────────────┤
│ [ All ] [Pathology] [ Anatomy ]     │ ← Pathology selected
├─────────────────────────────────────┤
│                                     │
│  When "Pathology" tab is selected:  │
│  ┌─────────────────────────────┐   │
│  │ Pathology - Lecture         │   │
│  │ Present: 8 | Absent: 1      │   │
│  │ 88.9% (Required: 75%)       │   │
│  │ [Edit] [Delete]             │   │
│  └─────────────────────────────┘   │
│                                     │
│  ┌─────────────────────────────┐   │
│  │ Pathology - Lab             │   │
│  │ Present: 6 | Absent: 2      │   │
│  │ 75.0% (Required: 75%)       │   │
│  │ [Edit] [Delete]             │   │
│  └─────────────────────────────┘   │
│                                     │
└─────────────────────────────────────┘
```

### Add Subject Dialog - Context Aware

**When in "All" tab:**
```
┌─────────────────────────────────────┐
│  Add Subject                        │
├─────────────────────────────────────┤
│                                     │
│  Create as folder     [ Toggle ]    │
│                                     │
│  Subject Name: ___________________  │
│                                     │
│  Required Attendance (%): _____     │
│                                     │
│              [Cancel]  [Add]        │
└─────────────────────────────────────┘
```

**When in "Pathology" tab:**
```
┌─────────────────────────────────────┐
│  Add Subject to Pathology           │
├─────────────────────────────────────┤
│                                     │
│  Subject Name: ___________________  │
│                                     │
│  Required Attendance (%): _____     │
│                                     │
│              [Cancel]  [Add]        │
└─────────────────────────────────────┘
```
(Note: Folder toggle is hidden)

## Key Improvements

### 1. Clear Organization
- Subjects are now organized by folders
- Each folder has its own dedicated tab
- "All" tab shows subjects not assigned to any folder

### 2. Context-Aware Actions
- Adding a subject while in a folder tab automatically assigns it to that folder
- Dialog title changes to reflect the context
- No manual folder selection needed

### 3. Better UX
- Folders are now functional organizational units
- Easy navigation between folders via tabs
- ScrollableTabRow handles many folders gracefully
- Clear empty states with helpful messages

### 4. Data Integrity
- Uses existing database schema (no migrations needed)
- `parentSubjectId` properly set when adding to folders
- Folders marked with `isFolder = true`
- Top-level subjects have `parentSubjectId = null`

## Code Flow

### Adding a Subject to a Folder

1. User navigates to "Pathology" tab
2. User taps + button
3. `SubjectsScreen` detects selected tab is folder
4. `selectedFolder` is set to the Pathology folder object
5. `AddSubjectDialog` receives `selectedFolder`
6. Dialog shows "Add Subject to Pathology"
7. User enters subject name and attendance %
8. Dialog calls `onConfirm(name, required, selectedFolder.id)`
9. `SubjectsScreen` forwards to `onAddSubject` callback
10. `AttendanceApp` calls `viewModel.addSubSubject(name, parentId, required)`
11. ViewModel creates Subject with `parentSubjectId = parentId`
12. Database saves subject with folder relationship
13. Subject appears in the Pathology tab

## Architecture

```
┌──────────────────┐
│  SubjectsScreen  │  ← UI Layer (manages tabs, state)
└────────┬─────────┘
         │
         │ onAddSubject(name, required, parentId)
         │
         ▼
┌──────────────────┐
│  AttendanceApp   │  ← Navigation Layer (routes callbacks)
└────────┬─────────┘
         │
         │ addSubject() or addSubSubject()
         │
         ▼
┌──────────────────┐
│  ViewModel       │  ← Business Logic Layer
└────────┬─────────┘
         │
         │ insertSubject(Subject)
         │
         ▼
┌──────────────────┐
│  Repository      │  ← Data Layer
└────────┬─────────┘
         │
         │ insertSubject()
         │
         ▼
┌──────────────────┐
│  SubjectDao      │  ← Database Layer
└──────────────────┘
```

## Database Relationships

```
Subject Table
┌─────────────────────────────────────┐
│ id (PK)                             │
│ name                                │
│ requiredAttendance                  │
│ totalLectures                       │
│ presentLectures                     │
│ absentLectures                      │
│ parentSubjectId (FK, nullable)  ←───┼── References id
│ isFolder (Boolean)                  │
└─────────────────────────────────────┘

Examples:
┌────┬─────────────────┬──────────┬──────────────────┐
│ id │ name            │ isFolder │ parentSubjectId  │
├────┼─────────────────┼──────────┼──────────────────┤
│ 1  │ Pathology       │ true     │ null             │ ← Folder
│ 2  │ Path - Lecture  │ false    │ 1                │ ← In Pathology
│ 3  │ Path - Lab      │ false    │ 1                │ ← In Pathology
│ 4  │ Mathematics     │ false    │ null             │ ← Top-level
│ 5  │ Anatomy         │ true     │ null             │ ← Folder
│ 6  │ Anat - Lecture  │ false    │ 5                │ ← In Anatomy
└────┴─────────────────┴──────────┴──────────────────┘
```

## Summary

The implementation successfully addresses the problem statement:
- ✅ Folders now appear as tabs (not in flat list)
- ✅ Users can add subjects to specific folders
- ✅ Folder contents are clearly separated
- ✅ Context-aware UI reduces user errors
- ✅ No breaking changes to existing features
