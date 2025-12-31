import SwiftUI
import SwiftData

/// Main content view with tab navigation
struct ContentView: View {
    @Environment(\.modelContext) private var modelContext
    @State private var viewModel: AttendanceViewModel?
    
    var body: some View {
        Group {
            if let viewModel = viewModel {
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
            } else {
                ProgressView("Loading...")
            }
        }
        .onAppear {
            if viewModel == nil {
                viewModel = AttendanceViewModel(modelContext: modelContext)
            }
        }
    }
}

#Preview {
    ContentView()
        .modelContainer(for: [Subject.self, AttendanceRecord.self, ScheduleEntry.self], inMemory: true)
}
