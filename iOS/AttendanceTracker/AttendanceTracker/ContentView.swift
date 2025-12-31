import SwiftUI
import SwiftData

/// Main content view with tab navigation
struct ContentView: View {
    @Environment(\.modelContext) private var modelContext
    @StateObject private var viewModel: AttendanceViewModel
    
    init() {
        // Initialize with a temporary context that will be replaced
        // The actual context is set in onAppear
        let container = try! ModelContainer(for: Subject.self, AttendanceRecord.self, ScheduleEntry.self)
        _viewModel = StateObject(wrappedValue: AttendanceViewModel(modelContext: container.mainContext))
    }
    
    var body: some View {
        TabView {
            HomeScreen(viewModel: viewModel)
                .tabItem {
                    Label("Home", systemImage: "house.fill")
                }
            
            CalendarScreen(viewModel: viewModel)
                .tabItem {
                    Label("Calendar", systemImage: "calendar")
                }
            
            SubjectsScreen(viewModel: viewModel)
                .tabItem {
                    Label("Subjects", systemImage: "book.fill")
                }
            
            ScheduleScreen(viewModel: viewModel)
                .tabItem {
                    Label("Schedule", systemImage: "clock.fill")
                }
            
            SettingsScreen(viewModel: viewModel)
                .tabItem {
                    Label("Settings", systemImage: "gear")
                }
        }
    }
}

#Preview {
    ContentView()
        .modelContainer(for: [Subject.self, AttendanceRecord.self, ScheduleEntry.self], inMemory: true)
}
