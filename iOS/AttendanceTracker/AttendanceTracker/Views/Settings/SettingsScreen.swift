import SwiftUI

/// Settings screen for managing attendance requirements
struct SettingsScreen: View {
    @ObservedObject var viewModel: AttendanceViewModel
    @State private var selectedSubject: Subject?
    @State private var showingEditDialog = false
    
    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                // Header
                VStack(alignment: .leading, spacing: 8) {
                    Text("Required Attendance Settings")
                        .font(.headline)
                    
                    Text("Set the minimum attendance percentage required for each subject")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                .padding(.horizontal)
                .padding(.top)
                
                if viewModel.subjects.isEmpty {
                    VStack {
                        Spacer()
                        Text("No subjects to configure")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                        Spacer()
                    }
                    .frame(maxWidth: .infinity)
                } else {
                    List {
                        ForEach(viewModel.subjects, id: \.id) { subject in
                            RequiredAttendanceItem(
                                subject: subject,
                                allSubjects: viewModel.allSubjectsIncludingFolders,
                                onEditClick: {
                                    selectedSubject = subject
                                    showingEditDialog = true
                                }
                            )
                        }
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle("Settings")
            .sheet(isPresented: $showingEditDialog) {
                if let subject = selectedSubject {
                    EditRequiredAttendanceSheet(
                        viewModel: viewModel,
                        subject: subject,
                        isPresented: $showingEditDialog
                    )
                }
            }
        }
    }
}

/// Individual subject item showing required attendance
struct RequiredAttendanceItem: View {
    let subject: Subject
    let allSubjects: [Subject]
    let onEditClick: () -> Void
    
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 4) {
                Text(subject.getDisplayName(allSubjects: allSubjects))
                    .font(.headline)
                
                Text("Required: \(subject.requiredAttendance)%")
                    .font(.subheadline)
                    .foregroundColor(.accentColor)
                
                Text(String(format: "Current: %.1f%%", subject.currentAttendancePercentage))
                    .font(.caption)
                    .foregroundColor(subject.isAboveRequired ? .presentGreen : .absentRed)
            }
            
            Spacer()
            
            Button(action: onEditClick) {
                Image(systemName: "pencil")
                    .foregroundColor(.primary)
            }
            .buttonStyle(PlainButtonStyle())
        }
        .padding(.vertical, 4)
    }
}

/// Sheet for editing required attendance percentage
struct EditRequiredAttendanceSheet: View {
    @ObservedObject var viewModel: AttendanceViewModel
    let subject: Subject
    @Binding var isPresented: Bool
    
    @State private var requiredAttendance: String
    
    init(viewModel: AttendanceViewModel, subject: Subject, isPresented: Binding<Bool>) {
        self.viewModel = viewModel
        self.subject = subject
        self._isPresented = isPresented
        self._requiredAttendance = State(initialValue: String(subject.requiredAttendance))
    }
    
    private var newRequired: Int {
        Int(requiredAttendance) ?? 0
    }
    
    private var wouldBeAbove: Bool {
        subject.currentAttendancePercentage >= Float(newRequired)
    }
    
    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text(subject.name)
                        .font(.headline)
                }
                
                Section("Required Attendance") {
                    TextField("Required Attendance (%)", text: $requiredAttendance)
                        .keyboardType(.numberPad)
                    
                    Text("Enter a value between 0 and 100")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                
                Section("Preview") {
                    HStack {
                        Image(systemName: wouldBeAbove ? "checkmark.circle.fill" : "xmark.circle.fill")
                            .foregroundColor(wouldBeAbove ? .presentGreen : .absentRed)
                        Text(wouldBeAbove ? "Current attendance meets requirement" : "Current attendance below requirement")
                            .font(.subheadline)
                            .foregroundColor(wouldBeAbove ? .presentGreen : .absentRed)
                    }
                }
            }
            .navigationTitle("Set Required Attendance")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        isPresented = false
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        let required = min(max(newRequired, 0), 100)
                        viewModel.updateRequiredAttendance(subjectId: subject.id, required: required)
                        isPresented = false
                    }
                }
            }
        }
    }
}

#Preview {
    SettingsScreen(viewModel: AttendanceViewModel(modelContext: try! ModelContainer(for: Subject.self, AttendanceRecord.self, ScheduleEntry.self).mainContext))
}
