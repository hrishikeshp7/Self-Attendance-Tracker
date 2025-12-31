import Foundation
import SwiftData
import Combine

/// Action for undo/redo functionality
struct AttendanceAction {
    let subjectId: UUID
    let date: Date
    let oldStatus: AttendanceStatus?
    let newStatus: AttendanceStatus
    let oldPresentCount: Int
    let oldAbsentCount: Int
}

/// Manager for undo/redo functionality
class UndoRedoManager {
    private var undoStack: [AttendanceAction] = []
    private var redoStack: [AttendanceAction] = []
    
    var canUndo: Bool { !undoStack.isEmpty }
    var canRedo: Bool { !redoStack.isEmpty }
    
    func recordAction(_ action: AttendanceAction) {
        undoStack.append(action)
        redoStack.removeAll()
    }
    
    func undo() -> AttendanceAction? {
        guard let action = undoStack.popLast() else { return nil }
        redoStack.append(action)
        return action
    }
    
    func redo() -> AttendanceAction? {
        guard let action = redoStack.popLast() else { return nil }
        undoStack.append(action)
        return action
    }
}

/// Main ViewModel for the Attendance Tracker app
@MainActor
class AttendanceViewModel: ObservableObject {
    private let modelContext: ModelContext
    private let undoRedoManager = UndoRedoManager()
    
    @Published var subjects: [Subject] = []
    @Published var allSubjectsIncludingFolders: [Subject] = []
    @Published var scheduleEntries: [ScheduleEntry] = []
    @Published var selectedDate: Date = Date()
    @Published var selectedMonth: Date = Date()
    @Published var todayAttendance: [UUID: AttendanceStatus] = [:]
    @Published var attendanceRecords: [AttendanceRecord] = []
    @Published var canUndo: Bool = false
    @Published var canRedo: Bool = false
    
    init(modelContext: ModelContext) {
        self.modelContext = modelContext
        loadData()
        loadAttendanceForDate(Date())
    }
    
    // MARK: - Data Loading
    
    func loadData() {
        fetchSubjects()
        fetchScheduleEntries()
    }
    
    private func fetchSubjects() {
        do {
            let descriptor = FetchDescriptor<Subject>(sortBy: [SortDescriptor(\.name)])
            let allSubjects = try modelContext.fetch(descriptor)
            self.allSubjectsIncludingFolders = allSubjects
            self.subjects = allSubjects.filter { !$0.isFolder }
        } catch {
            print("Error fetching subjects: \(error)")
        }
    }
    
    private func fetchScheduleEntries() {
        do {
            let descriptor = FetchDescriptor<ScheduleEntry>()
            self.scheduleEntries = try modelContext.fetch(descriptor)
        } catch {
            print("Error fetching schedule entries: \(error)")
        }
    }
    
    func loadAttendanceForDate(_ date: Date) {
        do {
            let calendar = Calendar.current
            let startOfDay = calendar.startOfDay(for: date)
            let endOfDay = calendar.date(byAdding: .day, value: 1, to: startOfDay)!
            
            let descriptor = FetchDescriptor<AttendanceRecord>(
                predicate: #Predicate { record in
                    record.date >= startOfDay && record.date < endOfDay
                }
            )
            let records = try modelContext.fetch(descriptor)
            self.todayAttendance = Dictionary(uniqueKeysWithValues: records.map { ($0.subjectId, $0.status) })
        } catch {
            print("Error loading attendance: \(error)")
        }
    }
    
    func loadAttendanceForMonth(_ date: Date) {
        do {
            let calendar = Calendar.current
            let startOfMonth = calendar.date(from: calendar.dateComponents([.year, .month], from: date))!
            let endOfMonth = calendar.date(byAdding: DateComponents(month: 1, day: -1), to: startOfMonth)!
            
            let descriptor = FetchDescriptor<AttendanceRecord>(
                predicate: #Predicate { record in
                    record.date >= startOfMonth && record.date <= endOfMonth
                }
            )
            self.attendanceRecords = try modelContext.fetch(descriptor)
        } catch {
            print("Error loading month attendance: \(error)")
        }
    }
    
    func setSelectedDate(_ date: Date) {
        selectedDate = date
        loadAttendanceForDate(date)
    }
    
    func setSelectedMonth(_ date: Date) {
        selectedMonth = date
        loadAttendanceForMonth(date)
    }
    
    // MARK: - Subject Operations
    
    func addSubject(name: String, requiredAttendance: Int = 75) {
        let subject = Subject(name: name, requiredAttendance: requiredAttendance)
        modelContext.insert(subject)
        saveContext()
        fetchSubjects()
    }
    
    func addSubjectFolder(name: String) {
        let folder = Subject(name: name, isFolder: true)
        modelContext.insert(folder)
        saveContext()
        fetchSubjects()
    }
    
    func addSubSubject(name: String, parentSubjectId: UUID, requiredAttendance: Int = 75) {
        let subject = Subject(
            name: name,
            requiredAttendance: requiredAttendance,
            parentSubjectId: parentSubjectId,
            isFolder: false
        )
        modelContext.insert(subject)
        saveContext()
        fetchSubjects()
    }
    
    func updateSubject(_ subject: Subject) {
        saveContext()
        fetchSubjects()
    }
    
    func deleteSubject(_ subject: Subject) {
        modelContext.delete(subject)
        saveContext()
        fetchSubjects()
    }
    
    func updateRequiredAttendance(subjectId: UUID, required: Int) {
        if let subject = allSubjectsIncludingFolders.first(where: { $0.id == subjectId }) {
            subject.requiredAttendance = required
            saveContext()
            fetchSubjects()
        }
    }
    
    func updateAttendanceCounts(subjectId: UUID, present: Int, absent: Int) {
        if let subject = allSubjectsIncludingFolders.first(where: { $0.id == subjectId }) {
            subject.presentLectures = present
            subject.absentLectures = absent
            subject.totalLectures = present + absent
            saveContext()
            fetchSubjects()
        }
    }
    
    // MARK: - Attendance Operations
    
    func markAttendance(subjectId: UUID, status: AttendanceStatus, date: Date = Date()) {
        guard let subject = allSubjectsIncludingFolders.first(where: { $0.id == subjectId }) else { return }
        
        // Get existing record for this date
        let calendar = Calendar.current
        let startOfDay = calendar.startOfDay(for: date)
        let endOfDay = calendar.date(byAdding: .day, value: 1, to: startOfDay)!
        
        do {
            let descriptor = FetchDescriptor<AttendanceRecord>(
                predicate: #Predicate { record in
                    record.subjectId == subjectId && record.date >= startOfDay && record.date < endOfDay
                }
            )
            let existingRecords = try modelContext.fetch(descriptor)
            let oldRecord = existingRecords.first
            
            // Record action for undo/redo
            let action = AttendanceAction(
                subjectId: subjectId,
                date: date,
                oldStatus: oldRecord?.status,
                newStatus: status,
                oldPresentCount: subject.presentLectures,
                oldAbsentCount: subject.absentLectures
            )
            undoRedoManager.recordAction(action)
            updateUndoRedoState()
            
            // Update or create attendance record
            if let existingRecord = oldRecord {
                existingRecord.status = status
            } else {
                let record = AttendanceRecord(subjectId: subjectId, date: date, status: status)
                modelContext.insert(record)
            }
            
            // Update subject counts
            switch status {
            case .present:
                subject.presentLectures += 1
                subject.totalLectures += 1
            case .absent:
                subject.absentLectures += 1
                subject.totalLectures += 1
            case .noClass:
                break
            }
            
            saveContext()
            loadAttendanceForDate(date)
            fetchSubjects()
        } catch {
            print("Error marking attendance: \(error)")
        }
    }
    
    func undo() {
        guard let action = undoRedoManager.undo() else { return }
        
        if let subject = allSubjectsIncludingFolders.first(where: { $0.id == action.subjectId }) {
            // Restore attendance counts
            subject.presentLectures = action.oldPresentCount
            subject.absentLectures = action.oldAbsentCount
            subject.totalLectures = action.oldPresentCount + action.oldAbsentCount
            
            // Restore or delete attendance record
            let calendar = Calendar.current
            let startOfDay = calendar.startOfDay(for: action.date)
            let endOfDay = calendar.date(byAdding: .day, value: 1, to: startOfDay)!
            
            do {
                let descriptor = FetchDescriptor<AttendanceRecord>(
                    predicate: #Predicate { record in
                        record.subjectId == action.subjectId && record.date >= startOfDay && record.date < endOfDay
                    }
                )
                let records = try modelContext.fetch(descriptor)
                
                if let oldStatus = action.oldStatus, let record = records.first {
                    record.status = oldStatus
                } else if let record = records.first {
                    modelContext.delete(record)
                }
                
                saveContext()
                loadAttendanceForDate(action.date)
                fetchSubjects()
                updateUndoRedoState()
            } catch {
                print("Error in undo: \(error)")
            }
        }
    }
    
    func redo() {
        guard let action = undoRedoManager.redo() else { return }
        
        if let subject = allSubjectsIncludingFolders.first(where: { $0.id == action.subjectId }) {
            // Reapply the attendance
            let calendar = Calendar.current
            let startOfDay = calendar.startOfDay(for: action.date)
            let endOfDay = calendar.date(byAdding: .day, value: 1, to: startOfDay)!
            
            do {
                let descriptor = FetchDescriptor<AttendanceRecord>(
                    predicate: #Predicate { record in
                        record.subjectId == action.subjectId && record.date >= startOfDay && record.date < endOfDay
                    }
                )
                let records = try modelContext.fetch(descriptor)
                
                if let record = records.first {
                    record.status = action.newStatus
                } else {
                    let record = AttendanceRecord(subjectId: action.subjectId, date: action.date, status: action.newStatus)
                    modelContext.insert(record)
                }
                
                // Update subject counts
                switch action.newStatus {
                case .present:
                    subject.presentLectures += 1
                    subject.totalLectures += 1
                case .absent:
                    subject.absentLectures += 1
                    subject.totalLectures += 1
                case .noClass:
                    break
                }
                
                saveContext()
                loadAttendanceForDate(action.date)
                fetchSubjects()
                updateUndoRedoState()
            } catch {
                print("Error in redo: \(error)")
            }
        }
    }
    
    private func updateUndoRedoState() {
        canUndo = undoRedoManager.canUndo
        canRedo = undoRedoManager.canRedo
    }
    
    // MARK: - Schedule Operations
    
    func addScheduleEntry(subjectId: UUID, dayOfWeek: DayOfWeek) {
        let entry = ScheduleEntry(subjectId: subjectId, dayOfWeek: dayOfWeek)
        modelContext.insert(entry)
        saveContext()
        fetchScheduleEntries()
    }
    
    func removeScheduleEntry(_ entry: ScheduleEntry) {
        modelContext.delete(entry)
        saveContext()
        fetchScheduleEntries()
    }
    
    func getScheduleForSubject(_ subjectId: UUID) -> [ScheduleEntry] {
        scheduleEntries.filter { $0.subjectId == subjectId }
    }
    
    func getScheduleForDay(_ dayOfWeek: DayOfWeek) -> [ScheduleEntry] {
        scheduleEntries.filter { $0.dayOfWeek == dayOfWeek }
    }
    
    // MARK: - Helper
    
    private func saveContext() {
        do {
            try modelContext.save()
        } catch {
            print("Error saving context: \(error)")
        }
    }
}
