import Foundation
import SwiftData

/// Represents the status of attendance
enum AttendanceStatus: String, Codable {
    case present = "PRESENT"
    case absent = "ABSENT"
    case noClass = "NO_CLASS"
}

/// Represents an attendance record for a specific subject on a specific date
@Model
final class AttendanceRecord {
    @Attribute(.unique) var id: UUID
    var subjectId: UUID
    var date: Date
    var status: AttendanceStatus
    
    init(
        id: UUID = UUID(),
        subjectId: UUID,
        date: Date,
        status: AttendanceStatus
    ) {
        self.id = id
        self.subjectId = subjectId
        self.date = date
        self.status = status
    }
}
