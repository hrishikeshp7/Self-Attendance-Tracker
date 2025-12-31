import SwiftUI
import SwiftData

/// Helper for creating preview containers safely
enum PreviewHelpers {
    /// Creates a preview model container with in-memory storage
    static func previewContainer() -> ModelContainer {
        let schema = Schema([Subject.self, AttendanceRecord.self, ScheduleEntry.self])
        let config = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
        
        do {
            return try ModelContainer(for: schema, configurations: [config])
        } catch {
            fatalError("Failed to create preview container: \(error)")
        }
    }
    
    /// Creates a preview view model
    static func previewViewModel() -> AttendanceViewModel {
        let container = previewContainer()
        return AttendanceViewModel(modelContext: container.mainContext)
    }
    
    /// Sample subject for previews
    static var sampleSubject: Subject {
        Subject(
            name: "Mathematics",
            requiredAttendance: 75,
            totalLectures: 20,
            presentLectures: 16,
            absentLectures: 4
        )
    }
}

/// Preview modifier for adding a model container
struct PreviewContainerModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .modelContainer(PreviewHelpers.previewContainer())
    }
}

extension View {
    /// Adds a preview model container to the view
    func previewContainer() -> some View {
        modifier(PreviewContainerModifier())
    }
}
