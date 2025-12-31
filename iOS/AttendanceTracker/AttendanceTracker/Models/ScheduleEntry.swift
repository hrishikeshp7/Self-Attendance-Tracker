import Foundation
import SwiftData

/// Represents a day of the week
enum DayOfWeek: Int, Codable, CaseIterable {
    case monday = 0
    case tuesday = 1
    case wednesday = 2
    case thursday = 3
    case friday = 4
    case saturday = 5
    case sunday = 6
    
    var shortName: String {
        switch self {
        case .monday: return "Mon"
        case .tuesday: return "Tue"
        case .wednesday: return "Wed"
        case .thursday: return "Thu"
        case .friday: return "Fri"
        case .saturday: return "Sat"
        case .sunday: return "Sun"
        }
    }
    
    var fullName: String {
        switch self {
        case .monday: return "Monday"
        case .tuesday: return "Tuesday"
        case .wednesday: return "Wednesday"
        case .thursday: return "Thursday"
        case .friday: return "Friday"
        case .saturday: return "Saturday"
        case .sunday: return "Sunday"
        }
    }
}

/// Represents a weekly schedule entry - which subjects are on which days
@Model
final class ScheduleEntry {
    @Attribute(.unique) var id: UUID
    var subjectId: UUID
    var dayOfWeek: DayOfWeek
    var isScheduled: Bool
    
    init(
        id: UUID = UUID(),
        subjectId: UUID,
        dayOfWeek: DayOfWeek,
        isScheduled: Bool = true
    ) {
        self.id = id
        self.subjectId = subjectId
        self.dayOfWeek = dayOfWeek
        self.isScheduled = isScheduled
    }
}
