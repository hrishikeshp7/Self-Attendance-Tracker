import SwiftUI

/// Weekly schedule screen
struct ScheduleScreen: View {
    @ObservedObject var viewModel: AttendanceViewModel
    @State private var selectedDay: DayOfWeek = .monday
    
    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Day Selector
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 12) {
                        ForEach(DayOfWeek.allCases, id: \.self) { day in
                            DayTab(
                                day: day,
                                isSelected: selectedDay == day,
                                onTap: { selectedDay = day }
                            )
                        }
                    }
                    .padding()
                }
                
                // Day Content
                DayScheduleContent(
                    day: selectedDay,
                    subjects: viewModel.subjects,
                    allSubjects: viewModel.allSubjectsIncludingFolders,
                    scheduleEntries: viewModel.scheduleEntries,
                    onAddScheduleEntry: { subjectId, day in
                        viewModel.addScheduleEntry(subjectId: subjectId, dayOfWeek: day)
                    },
                    onRemoveScheduleEntry: { entry in
                        viewModel.removeScheduleEntry(entry)
                    }
                )
            }
            .navigationTitle("Weekly Schedule")
        }
    }
}

/// Tab button for day selection
struct DayTab: View {
    let day: DayOfWeek
    let isSelected: Bool
    let onTap: () -> Void
    
    var body: some View {
        Button(action: onTap) {
            Text(day.shortName)
                .font(.subheadline)
                .fontWeight(isSelected ? .bold : .medium)
                .foregroundColor(isSelected ? .white : .primary)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(isSelected ? Color.accentColor : Color(.secondarySystemBackground))
                .cornerRadius(20)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

/// Content view for a specific day's schedule
struct DayScheduleContent: View {
    let day: DayOfWeek
    let subjects: [Subject]
    let allSubjects: [Subject]
    let scheduleEntries: [ScheduleEntry]
    let onAddScheduleEntry: (UUID, DayOfWeek) -> Void
    let onRemoveScheduleEntry: (ScheduleEntry) -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(day.fullName)
                .font(.title2)
                .fontWeight(.bold)
                .padding(.horizontal)
            
            if subjects.isEmpty {
                VStack {
                    Spacer()
                    Text("Add subjects first to create a schedule")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                    Spacer()
                }
                .frame(maxWidth: .infinity)
            } else {
                List {
                    ForEach(subjects, id: \.id) { subject in
                        let isScheduled = scheduleEntries.contains(where: {
                            $0.subjectId == subject.id && $0.dayOfWeek == day
                        })
                        let entry = scheduleEntries.first(where: {
                            $0.subjectId == subject.id && $0.dayOfWeek == day
                        })
                        
                        ScheduleSubjectItem(
                            subject: subject,
                            allSubjects: allSubjects,
                            isScheduled: isScheduled,
                            onToggle: { checked in
                                if checked {
                                    onAddScheduleEntry(subject.id, day)
                                } else if let entry = entry {
                                    onRemoveScheduleEntry(entry)
                                }
                            }
                        )
                    }
                }
                .listStyle(.plain)
            }
        }
    }
}

/// Individual subject item in schedule
struct ScheduleSubjectItem: View {
    let subject: Subject
    let allSubjects: [Subject]
    let isScheduled: Bool
    let onToggle: (Bool) -> Void
    
    var body: some View {
        HStack {
            Text(subject.getDisplayName(allSubjects: allSubjects))
                .font(.body)
            
            Spacer()
            
            Toggle("", isOn: Binding(
                get: { isScheduled },
                set: { onToggle($0) }
            ))
            .labelsHidden()
        }
        .padding(.vertical, 4)
    }
}

#Preview {
    ScheduleScreen(viewModel: AttendanceViewModel(modelContext: try! ModelContainer(for: Subject.self, AttendanceRecord.self, ScheduleEntry.self).mainContext))
}
