package com.attendance.tracker.ui

import com.attendance.tracker.data.model.AttendanceStatus
import java.time.LocalDate

/**
 * Manages undo/redo operations for attendance marking
 */
class UndoRedoManager {
    private val undoStack = mutableListOf<AttendanceAction>()
    private val redoStack = mutableListOf<AttendanceAction>()
    
    val canUndo: Boolean
        get() = undoStack.isNotEmpty()
    
    val canRedo: Boolean
        get() = redoStack.isNotEmpty()
    
    /**
     * Records a new action. Clears redo stack as new actions invalidate redo history.
     */
    fun recordAction(action: AttendanceAction) {
        undoStack.add(action)
        redoStack.clear()
    }
    
    /**
     * Returns the last action to undo, or null if nothing to undo
     */
    fun undo(): AttendanceAction? {
        if (undoStack.isEmpty()) return null
        
        val action = undoStack.removeAt(undoStack.lastIndex)
        redoStack.add(action)
        return action
    }
    
    /**
     * Returns the last action to redo, or null if nothing to redo
     */
    fun redo(): AttendanceAction? {
        if (redoStack.isEmpty()) return null
        
        val action = redoStack.removeAt(redoStack.lastIndex)
        undoStack.add(action)
        return action
    }
    
    /**
     * Clears all undo/redo history
     */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}

/**
 * Represents an attendance marking action that can be undone/redone
 */
data class AttendanceAction(
    val subjectId: Long,
    val date: LocalDate,
    val oldStatus: AttendanceStatus?,  // null if no previous status
    val newStatus: AttendanceStatus,
    val oldPresentCount: Int,
    val oldAbsentCount: Int,
    val oldTotalCount: Int
)
