package com.attendance.tracker.ui

import com.attendance.tracker.data.model.AttendanceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UndoRedoManagerTest {

    private fun action(newStatus: AttendanceStatus = AttendanceStatus.PRESENT) = AttendanceAction(
        subjectId = 1L,
        date = LocalDate.of(2024, 1, 1),
        oldStatus = null,
        oldCount = 0,
        newStatus = newStatus,
        newCount = 1,
        oldPresentCount = 0,
        oldAbsentCount = 0
    )

    @Test
    fun `cannot undo or redo an empty history`() {
        val manager = UndoRedoManager()
        assertFalse(manager.canUndo)
        assertFalse(manager.canRedo)
        assertNull(manager.undo())
        assertNull(manager.redo())
    }

    @Test
    fun `recording an action makes it undoable and moves it to the redo stack once undone`() {
        val manager = UndoRedoManager()
        manager.recordAction(action())
        assertTrue(manager.canUndo)
        assertFalse(manager.canRedo)

        val undone = manager.undo()
        assertEquals(1L, undone?.subjectId)
        assertFalse(manager.canUndo)
        assertTrue(manager.canRedo)
    }

    @Test
    fun `a new action clears the redo stack`() {
        val manager = UndoRedoManager()
        manager.recordAction(action())
        manager.undo()
        assertTrue(manager.canRedo)

        manager.recordAction(action(AttendanceStatus.ABSENT))
        assertFalse(manager.canRedo)
    }

    @Test
    fun `undo and redo restore actions in LIFO order`() {
        val manager = UndoRedoManager()
        val first = action(AttendanceStatus.PRESENT)
        val second = action(AttendanceStatus.ABSENT)
        manager.recordAction(first)
        manager.recordAction(second)

        assertEquals(second.newStatus, manager.undo()?.newStatus)
        assertEquals(first.newStatus, manager.undo()?.newStatus)
        assertNull(manager.undo())

        assertEquals(first.newStatus, manager.redo()?.newStatus)
        assertEquals(second.newStatus, manager.redo()?.newStatus)
    }
}
