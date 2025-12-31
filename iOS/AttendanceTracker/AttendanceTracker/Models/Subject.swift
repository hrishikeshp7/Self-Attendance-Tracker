import Foundation
import SwiftData

/// Represents a subject/course for attendance tracking
@Model
final class Subject {
    @Attribute(.unique) var id: UUID
    var name: String
    var requiredAttendance: Int
    var totalLectures: Int
    var presentLectures: Int
    var absentLectures: Int
    var parentSubjectId: UUID?
    var isFolder: Bool
    
    init(
        id: UUID = UUID(),
        name: String,
        requiredAttendance: Int = 75,
        totalLectures: Int = 0,
        presentLectures: Int = 0,
        absentLectures: Int = 0,
        parentSubjectId: UUID? = nil,
        isFolder: Bool = false
    ) {
        self.id = id
        self.name = name
        self.requiredAttendance = requiredAttendance
        self.totalLectures = totalLectures
        self.presentLectures = presentLectures
        self.absentLectures = absentLectures
        self.parentSubjectId = parentSubjectId
        self.isFolder = isFolder
    }
    
    var currentAttendancePercentage: Float {
        guard totalLectures > 0 else { return 0 }
        return (Float(presentLectures) / Float(totalLectures)) * 100
    }
    
    var isAboveRequired: Bool {
        currentAttendancePercentage >= Float(requiredAttendance)
    }
    
    /// Get the display name for a subject, including folder name if applicable
    func getDisplayName(allSubjects: [Subject]) -> String {
        if let parentId = parentSubjectId,
           let folder = allSubjects.first(where: { $0.id == parentId }) {
            return "\(folder.name) / \(name)"
        }
        return name
    }
}
