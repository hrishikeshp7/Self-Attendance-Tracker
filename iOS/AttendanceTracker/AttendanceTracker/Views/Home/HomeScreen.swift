import SwiftUI

/// Home screen showing all subjects with attendance tracking
struct HomeScreen: View {
    @ObservedObject var viewModel: AttendanceViewModel
    @State private var showingAddSubject = false
    
    private var dateFormatter: DateFormatter {
        let formatter = DateFormatter()
        formatter.dateFormat = "EEEE, MMMM d, yyyy"
        return formatter
    }
    
    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Today's Date Header
                Text(dateFormatter.string(from: Date()))
                    .font(.headline)
                    .foregroundColor(.accentColor)
                    .padding()
                
                if viewModel.subjects.isEmpty {
                    // Empty State
                    Spacer()
                    VStack(spacing: 8) {
                        Text("No subjects added yet")
                            .font(.title2)
                            .fontWeight(.semibold)
                        Text("Tap the + button to add your first subject")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                    .padding()
                    Spacer()
                } else {
                    // Subject List
                    ScrollView {
                        LazyVStack(spacing: 16) {
                            ForEach(viewModel.subjects, id: \.id) { subject in
                                SubjectCard(
                                    subject: subject,
                                    allSubjects: viewModel.allSubjectsIncludingFolders,
                                    currentStatus: viewModel.todayAttendance[subject.id],
                                    onMarkPresent: {
                                        viewModel.markAttendance(subjectId: subject.id, status: .present)
                                    },
                                    onMarkAbsent: {
                                        viewModel.markAttendance(subjectId: subject.id, status: .absent)
                                    },
                                    onMarkNoClass: {
                                        viewModel.markAttendance(subjectId: subject.id, status: .noClass)
                                    },
                                    onEditClick: {}
                                )
                            }
                        }
                        .padding()
                    }
                }
                
                // GitHub Footer
                GitHubFooter()
            }
            .navigationTitle("Attendance Tracker")
            .toolbar {
                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    // Undo button
                    Button(action: { viewModel.undo() }) {
                        Image(systemName: "arrow.uturn.backward")
                    }
                    .disabled(!viewModel.canUndo)
                    
                    // Redo button
                    Button(action: { viewModel.redo() }) {
                        Image(systemName: "arrow.uturn.forward")
                    }
                    .disabled(!viewModel.canRedo)
                    
                    // Add button
                    Button(action: { showingAddSubject = true }) {
                        Image(systemName: "plus")
                    }
                }
            }
            .sheet(isPresented: $showingAddSubject) {
                AddSubjectSheet(
                    viewModel: viewModel,
                    selectedFolder: nil,
                    isPresented: $showingAddSubject
                )
            }
        }
    }
}

/// Sheet for adding a new subject
struct AddSubjectSheet: View {
    @ObservedObject var viewModel: AttendanceViewModel
    let selectedFolder: Subject?
    @Binding var isPresented: Bool
    
    @State private var name = ""
    @State private var requiredAttendance = "75"
    @State private var isFolder = false
    
    var body: some View {
        NavigationStack {
            Form {
                if selectedFolder == nil {
                    Toggle("Create as folder", isOn: $isFolder)
                }
                
                TextField(isFolder ? "Folder Name" : "Subject Name", text: $name)
                
                if !isFolder {
                    TextField("Required Attendance (%)", text: $requiredAttendance)
                        .keyboardType(.numberPad)
                }
            }
            .navigationTitle(
                isFolder ? "Add Folder" :
                    (selectedFolder != nil ? "Add Subject to \(selectedFolder!.name)" : "Add Subject")
            )
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        isPresented = false
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add") {
                        if !name.trimmingCharacters(in: .whitespaces).isEmpty {
                            if isFolder {
                                viewModel.addSubjectFolder(name: name.trimmingCharacters(in: .whitespaces))
                            } else {
                                let required = Int(requiredAttendance) ?? 75
                                if let folder = selectedFolder {
                                    viewModel.addSubSubject(
                                        name: name.trimmingCharacters(in: .whitespaces),
                                        parentSubjectId: folder.id,
                                        requiredAttendance: min(max(required, 0), 100)
                                    )
                                } else {
                                    viewModel.addSubject(
                                        name: name.trimmingCharacters(in: .whitespaces),
                                        requiredAttendance: min(max(required, 0), 100)
                                    )
                                }
                            }
                            isPresented = false
                        }
                    }
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
    }
}

#Preview {
    HomeScreen(viewModel: PreviewHelpers.previewViewModel())
        .previewContainer()
}
