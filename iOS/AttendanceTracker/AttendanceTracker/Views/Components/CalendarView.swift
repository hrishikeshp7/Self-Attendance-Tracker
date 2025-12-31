import SwiftUI

/// A custom calendar view for displaying attendance history
struct CalendarView: View {
    let selectedMonth: Date
    let selectedDate: Date
    let attendanceRecords: [AttendanceRecord]
    let onDateSelected: (Date) -> Void
    let onMonthChanged: (Date) -> Void
    
    private let calendar = Calendar.current
    private let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMMM yyyy"
        return formatter
    }()
    
    private let weekdaySymbols = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]
    
    private var daysInMonth: [Date?] {
        let components = calendar.dateComponents([.year, .month], from: selectedMonth)
        guard let startOfMonth = calendar.date(from: components),
              let range = calendar.range(of: .day, in: .month, for: startOfMonth) else {
            return []
        }
        
        let firstWeekday = calendar.component(.weekday, from: startOfMonth)
        var days: [Date?] = Array(repeating: nil, count: firstWeekday - 1)
        
        for day in range {
            if let date = calendar.date(byAdding: .day, value: day - 1, to: startOfMonth) {
                days.append(date)
            }
        }
        
        return days
    }
    
    var body: some View {
        VStack(spacing: 16) {
            // Month Navigation
            HStack {
                Button(action: { changeMonth(-1) }) {
                    Image(systemName: "chevron.left")
                        .font(.title2)
                        .foregroundColor(.primary)
                }
                
                Spacer()
                
                Text(dateFormatter.string(from: selectedMonth))
                    .font(.title2)
                    .fontWeight(.bold)
                
                Spacer()
                
                Button(action: { changeMonth(1) }) {
                    Image(systemName: "chevron.right")
                        .font(.title2)
                        .foregroundColor(.primary)
                }
            }
            .padding(.horizontal)
            
            // Weekday Headers
            HStack {
                ForEach(weekdaySymbols, id: \.self) { symbol in
                    Text(symbol)
                        .font(.caption)
                        .fontWeight(.medium)
                        .foregroundColor(.secondary)
                        .frame(maxWidth: .infinity)
                }
            }
            
            // Calendar Grid
            LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 7), spacing: 8) {
                ForEach(Array(daysInMonth.enumerated()), id: \.offset) { _, date in
                    if let date = date {
                        DayCell(
                            date: date,
                            isSelected: calendar.isDate(date, inSameDayAs: selectedDate),
                            attendanceRecords: attendanceRecords.filter { calendar.isDate($0.date, inSameDayAs: date) },
                            onTap: { onDateSelected(date) }
                        )
                    } else {
                        Color.clear
                            .frame(height: 40)
                    }
                }
            }
            .padding(.horizontal)
        }
    }
    
    private func changeMonth(_ delta: Int) {
        if let newMonth = calendar.date(byAdding: .month, value: delta, to: selectedMonth) {
            onMonthChanged(newMonth)
        }
    }
}

struct DayCell: View {
    let date: Date
    let isSelected: Bool
    let attendanceRecords: [AttendanceRecord]
    let onTap: () -> Void
    
    private let calendar = Calendar.current
    
    private var dayNumber: Int {
        calendar.component(.day, from: date)
    }
    
    private var attendanceColor: Color? {
        if attendanceRecords.contains(where: { $0.status == .present }) {
            return .presentGreen
        } else if attendanceRecords.contains(where: { $0.status == .absent }) {
            return .absentRed
        } else if attendanceRecords.contains(where: { $0.status == .noClass }) {
            return .noClassGray
        }
        return nil
    }
    
    var body: some View {
        Button(action: onTap) {
            ZStack {
                if isSelected {
                    Circle()
                        .fill(Color.accentColor)
                        .frame(width: 36, height: 36)
                } else if let color = attendanceColor {
                    Circle()
                        .fill(color.opacity(0.3))
                        .frame(width: 36, height: 36)
                }
                
                Text("\(dayNumber)")
                    .font(.subheadline)
                    .foregroundColor(isSelected ? .white : (attendanceColor ?? .primary))
            }
        }
        .buttonStyle(PlainButtonStyle())
        .frame(height: 40)
    }
}

#Preview {
    CalendarView(
        selectedMonth: Date(),
        selectedDate: Date(),
        attendanceRecords: [],
        onDateSelected: { _ in },
        onMonthChanged: { _ in }
    )
}
