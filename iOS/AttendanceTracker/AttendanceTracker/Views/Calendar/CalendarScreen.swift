import SwiftUI

/// Calendar screen for viewing and editing attendance history
struct CalendarScreen: View {
    @ObservedObject var viewModel: AttendanceViewModel
    
    private var dateFormatter: DateFormatter {
        let formatter = DateFormatter()
        formatter.dateFormat = "EEEE, MMMM d"
        return formatter
    }
    
    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Calendar View
                CalendarView(
                    selectedMonth: viewModel.selectedMonth,
                    selectedDate: viewModel.selectedDate,
                    attendanceRecords: viewModel.attendanceRecords,
                    onDateSelected: { date in
                        viewModel.setSelectedDate(date)
                    },
                    onMonthChanged: { month in
                        viewModel.setSelectedMonth(month)
                    }
                )
                .padding(.top)
                
                Divider()
                    .padding(.vertical, 8)
                
                // Selected Date Header
                Text(dateFormatter.string(from: viewModel.selectedDate))
                    .font(.headline)
                    .padding(.horizontal)
                    .frame(maxWidth: .infinity, alignment: .leading)
                
                // Attendance List for Selected Date
                if viewModel.subjects.isEmpty {
                    VStack {
                        Spacer()
                        Text("No subjects added yet")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                        Spacer()
                    }
                    .frame(maxWidth: .infinity)
                    .padding()
                } else {
                    ScrollView {
                        LazyVStack(spacing: 8) {
                            ForEach(viewModel.subjects, id: \.id) { subject in
                                let record = viewModel.attendanceRecords.first(where: { 
                                    $0.subjectId == subject.id && 
                                    Calendar.current.isDate($0.date, inSameDayAs: viewModel.selectedDate)
                                })
                                
                                CalendarAttendanceItem(
                                    subject: subject,
                                    allSubjects: viewModel.allSubjectsIncludingFolders,
                                    currentStatus: record?.status,
                                    onMarkPresent: {
                                        viewModel.markAttendance(subjectId: subject.id, status: .present, date: viewModel.selectedDate)
                                    },
                                    onMarkAbsent: {
                                        viewModel.markAttendance(subjectId: subject.id, status: .absent, date: viewModel.selectedDate)
                                    },
                                    onMarkNoClass: {
                                        viewModel.markAttendance(subjectId: subject.id, status: .noClass, date: viewModel.selectedDate)
                                    }
                                )
                            }
                        }
                        .padding()
                    }
                }
            }
            .navigationTitle("Calendar")
            .onAppear {
                viewModel.loadAttendanceForMonth(viewModel.selectedMonth)
            }
        }
    }
}

/// Individual attendance item for calendar screen
struct CalendarAttendanceItem: View {
    let subject: Subject
    let allSubjects: [Subject]
    let currentStatus: AttendanceStatus?
    let onMarkPresent: () -> Void
    let onMarkAbsent: () -> Void
    let onMarkNoClass: () -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(subject.getDisplayName(allSubjects: allSubjects))
                .font(.headline)
            
            HStack(spacing: 8) {
                CalendarAttendanceButton(
                    text: "Present",
                    isSelected: currentStatus == .present,
                    color: .presentGreen,
                    action: onMarkPresent
                )
                
                CalendarAttendanceButton(
                    text: "Absent",
                    isSelected: currentStatus == .absent,
                    color: .absentRed,
                    action: onMarkAbsent
                )
                
                CalendarAttendanceButton(
                    text: "No Class",
                    isSelected: currentStatus == .noClass,
                    color: .noClassGray,
                    action: onMarkNoClass
                )
            }
        }
        .padding()
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
    }
}

/// Button for marking attendance in calendar view
struct CalendarAttendanceButton: View {
    let text: String
    let isSelected: Bool
    let color: Color
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            Text(text)
                .font(.caption)
                .fontWeight(.medium)
                .foregroundColor(isSelected ? .white : color)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(isSelected ? color : color.opacity(0.2))
                .cornerRadius(8)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

#Preview {
    CalendarScreen(viewModel: AttendanceViewModel(modelContext: try! ModelContainer(for: Subject.self, AttendanceRecord.self, ScheduleEntry.self).mainContext))
}
