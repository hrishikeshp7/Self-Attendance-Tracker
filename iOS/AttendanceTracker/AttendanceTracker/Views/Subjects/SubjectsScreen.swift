import SwiftUI

/// Subjects management screen
struct SubjectsScreen: View {
    @ObservedObject var viewModel: AttendanceViewModel
    @State private var showingAddDialog = false
    @State private var showingEditDialog = false
    @State private var selectedSubject: Subject?
    @State private var currentFolder: Subject?
    
    private var displaySubjects: [Subject] {
        if let folder = currentFolder {
            return viewModel.allSubjectsIncludingFolders.filter { $0.parentSubjectId == folder.id }
        } else {
            return viewModel.allSubjectsIncludingFolders.filter { $0.parentSubjectId == nil }
        }
    }
    
    var body: some View {
        NavigationStack {
            VStack {
                if displaySubjects.isEmpty {
                    Spacer()
                    VStack(spacing: 8) {
                        Text(currentFolder != nil ? "No subjects in this folder" : "No subjects added yet")
                            .font(.title2)
                            .fontWeight(.semibold)
                        Text(currentFolder != nil ? "Tap + to add a subject to this folder" : "Tap + to add a subject or folder")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                    .padding()
                    Spacer()
                } else {
                    List {
                        ForEach(displaySubjects, id: \.id) { subject in
                            if subject.isFolder {
                                FolderListItem(
                                    folder: subject,
                                    onFolderClick: { currentFolder = subject },
                                    onEditClick: {
                                        selectedSubject = subject
                                        showingEditDialog = true
                                    },
                                    onDeleteClick: {
                                        viewModel.deleteSubject(subject)
                                    }
                                )
                            } else {
                                SubjectListItem(
                                    subject: subject,
                                    onEditClick: {
                                        selectedSubject = subject
                                        showingEditDialog = true
                                    },
                                    onDeleteClick: {
                                        viewModel.deleteSubject(subject)
                                    }
                                )
                            }
                        }
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle(currentFolder?.name ?? "Manage Subjects")
            .toolbar {
                if currentFolder != nil {
                    ToolbarItem(placement: .navigationBarLeading) {
                        Button(action: { currentFolder = nil }) {
                            Image(systemName: "chevron.left")
                        }
                    }
                }
                
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { showingAddDialog = true }) {
                        Image(systemName: "plus")
                    }
                }
            }
            .sheet(isPresented: $showingAddDialog) {
                AddSubjectSheet(
                    viewModel: viewModel,
                    selectedFolder: currentFolder,
                    isPresented: $showingAddDialog
                )
            }
            .sheet(isPresented: $showingEditDialog) {
                if let subject = selectedSubject {
                    EditSubjectSheet(
                        viewModel: viewModel,
                        subject: subject,
                        isPresented: $showingEditDialog
                    )
                }
            }
        }
    }
}

/// Folder list item view
struct FolderListItem: View {
    let folder: Subject
    let onFolderClick: () -> Void
    let onEditClick: () -> Void
    let onDeleteClick: () -> Void
    
    @State private var showDeleteConfirmation = false
    
    var body: some View {
        HStack {
            Image(systemName: "folder.fill")
                .foregroundColor(.accentColor)
                .font(.title2)
            
            VStack(alignment: .leading, spacing: 4) {
                Text(folder.name)
                    .font(.headline)
                Text("Folder")
                    .font(.caption)
                    .foregroundColor(.accentColor)
            }
            
            Spacer()
            
            Button(action: onEditClick) {
                Image(systemName: "pencil")
                    .foregroundColor(.primary)
            }
            .buttonStyle(PlainButtonStyle())
            
            Button(action: { showDeleteConfirmation = true }) {
                Image(systemName: "trash")
                    .foregroundColor(.absentRed)
            }
            .buttonStyle(PlainButtonStyle())
        }
        .contentShape(Rectangle())
        .onTapGesture { onFolderClick() }
        .alert("Delete Folder", isPresented: $showDeleteConfirmation) {
            Button("Cancel", role: .cancel) {}
            Button("Delete", role: .destructive) { onDeleteClick() }
        } message: {
            Text("Are you sure you want to delete '\(folder.name)'? This action cannot be undone.")
        }
    }
}

/// Subject list item view
struct SubjectListItem: View {
    let subject: Subject
    let onEditClick: () -> Void
    let onDeleteClick: () -> Void
    
    @State private var showDeleteConfirmation = false
    
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(subject.name)
                    .font(.headline)
                
                Text("Present: \(subject.presentLectures) | Absent: \(subject.absentLectures)")
                    .font(.caption)
                    .foregroundColor(.secondary)
                
                Text(String(format: "%.1f%% (Required: %d%%)", subject.currentAttendancePercentage, subject.requiredAttendance))
                    .font(.caption)
                    .foregroundColor(subject.isAboveRequired ? .presentGreen : .absentRed)
            }
            
            Spacer()
            
            Button(action: onEditClick) {
                Image(systemName: "pencil")
                    .foregroundColor(.primary)
            }
            .buttonStyle(PlainButtonStyle())
            
            Button(action: { showDeleteConfirmation = true }) {
                Image(systemName: "trash")
                    .foregroundColor(.absentRed)
            }
            .buttonStyle(PlainButtonStyle())
        }
        .alert("Delete Subject", isPresented: $showDeleteConfirmation) {
            Button("Cancel", role: .cancel) {}
            Button("Delete", role: .destructive) { onDeleteClick() }
        } message: {
            Text("Are you sure you want to delete '\(subject.name)'? This action cannot be undone.")
        }
    }
}

/// Sheet for editing a subject
struct EditSubjectSheet: View {
    @ObservedObject var viewModel: AttendanceViewModel
    let subject: Subject
    @Binding var isPresented: Bool
    
    @State private var name: String
    @State private var requiredAttendance: String
    @State private var presentLectures: String
    @State private var absentLectures: String
    
    init(viewModel: AttendanceViewModel, subject: Subject, isPresented: Binding<Bool>) {
        self.viewModel = viewModel
        self.subject = subject
        self._isPresented = isPresented
        self._name = State(initialValue: subject.name)
        self._requiredAttendance = State(initialValue: String(subject.requiredAttendance))
        self._presentLectures = State(initialValue: String(subject.presentLectures))
        self._absentLectures = State(initialValue: String(subject.absentLectures))
    }
    
    var body: some View {
        NavigationStack {
            Form {
                TextField(subject.isFolder ? "Folder Name" : "Subject Name", text: $name)
                
                if !subject.isFolder {
                    TextField("Required Attendance (%)", text: $requiredAttendance)
                        .keyboardType(.numberPad)
                    
                    Section("Attendance Counts") {
                        TextField("Present", text: $presentLectures)
                            .keyboardType(.numberPad)
                        TextField("Absent", text: $absentLectures)
                            .keyboardType(.numberPad)
                    }
                }
            }
            .navigationTitle(subject.isFolder ? "Edit Folder" : "Edit Subject")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        isPresented = false
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        if !name.trimmingCharacters(in: .whitespaces).isEmpty {
                            subject.name = name.trimmingCharacters(in: .whitespaces)
                            
                            if !subject.isFolder {
                                let required = Int(requiredAttendance) ?? 75
                                subject.requiredAttendance = min(max(required, 0), 100)
                                
                                let present = Int(presentLectures) ?? 0
                                let absent = Int(absentLectures) ?? 0
                                viewModel.updateAttendanceCounts(
                                    subjectId: subject.id,
                                    present: present,
                                    absent: absent
                                )
                            }
                            
                            viewModel.updateSubject(subject)
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
    SubjectsScreen(viewModel: AttendanceViewModel(modelContext: try! ModelContainer(for: Subject.self, AttendanceRecord.self, ScheduleEntry.self).mainContext))
}
