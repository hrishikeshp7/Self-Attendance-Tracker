import SwiftUI

/// A card component for displaying subject information with attendance actions
struct SubjectCard: View {
    let subject: Subject
    let allSubjects: [Subject]
    let currentStatus: AttendanceStatus?
    let onMarkPresent: () -> Void
    let onMarkAbsent: () -> Void
    let onMarkNoClass: () -> Void
    let onEditClick: () -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Subject Name
            Text(subject.getDisplayName(allSubjects: allSubjects))
                .font(.headline)
            
            // Attendance Stats
            HStack {
                Text("Present: \(subject.presentLectures)")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                Text("|")
                    .foregroundColor(.secondary)
                Text("Absent: \(subject.absentLectures)")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
            
            // Attendance Percentage
            HStack {
                Text(String(format: "%.1f%%", subject.currentAttendancePercentage))
                    .font(.subheadline)
                    .foregroundColor(subject.isAboveRequired ? .presentGreen : .absentRed)
                Text("(Required: \(subject.requiredAttendance)%)")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
            
            // Attendance Pie Chart
            AttendanceChart(
                presentCount: subject.presentLectures,
                absentCount: subject.absentLectures,
                requiredPercentage: subject.requiredAttendance
            )
            .frame(height: 100)
            .padding(.vertical, 4)
            
            // Action Buttons
            HStack(spacing: 8) {
                AttendanceButton(
                    text: "Present",
                    isSelected: currentStatus == .present,
                    color: .presentGreen,
                    action: onMarkPresent
                )
                
                AttendanceButton(
                    text: "Absent",
                    isSelected: currentStatus == .absent,
                    color: .absentRed,
                    action: onMarkAbsent
                )
                
                AttendanceButton(
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
        .shadow(radius: 2)
    }
}

/// A button for marking attendance status
struct AttendanceButton: View {
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
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(isSelected ? color : color.opacity(0.2))
                .cornerRadius(8)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

#Preview {
    SubjectCard(
        subject: Subject(name: "Mathematics", requiredAttendance: 75, totalLectures: 20, presentLectures: 16, absentLectures: 4),
        allSubjects: [],
        currentStatus: .present,
        onMarkPresent: {},
        onMarkAbsent: {},
        onMarkNoClass: {},
        onEditClick: {}
    )
    .padding()
}
