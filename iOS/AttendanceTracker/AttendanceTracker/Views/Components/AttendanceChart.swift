import SwiftUI

/// A pie chart showing attendance distribution
struct AttendanceChart: View {
    let presentCount: Int
    let absentCount: Int
    let requiredPercentage: Int
    
    private var totalCount: Int {
        presentCount + absentCount
    }
    
    private var presentPercentage: Double {
        guard totalCount > 0 else { return 0 }
        return Double(presentCount) / Double(totalCount)
    }
    
    private var absentPercentage: Double {
        guard totalCount > 0 else { return 0 }
        return Double(absentCount) / Double(totalCount)
    }
    
    var body: some View {
        HStack(spacing: 16) {
            // Pie Chart
            ZStack {
                if totalCount > 0 {
                    Circle()
                        .stroke(Color.gray.opacity(0.2), lineWidth: 20)
                    
                    // Present arc
                    Circle()
                        .trim(from: 0, to: presentPercentage)
                        .stroke(Color.presentGreen, style: StrokeStyle(lineWidth: 20, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                    
                    // Absent arc
                    Circle()
                        .trim(from: presentPercentage, to: 1)
                        .stroke(Color.absentRed, style: StrokeStyle(lineWidth: 20, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                    
                    // Center text
                    VStack(spacing: 2) {
                        Text(String(format: "%.0f%%", presentPercentage * 100))
                            .font(.headline)
                            .fontWeight(.bold)
                        Text("Present")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }
                } else {
                    Circle()
                        .stroke(Color.gray.opacity(0.2), lineWidth: 20)
                    
                    Text("No Data")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            .frame(width: 100, height: 100)
            
            // Legend
            VStack(alignment: .leading, spacing: 8) {
                LegendItem(color: .presentGreen, label: "Present", count: presentCount)
                LegendItem(color: .absentRed, label: "Absent", count: absentCount)
                
                Divider()
                
                HStack {
                    Text("Required:")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Text("\(requiredPercentage)%")
                        .font(.caption)
                        .fontWeight(.medium)
                }
            }
        }
    }
}

struct LegendItem: View {
    let color: Color
    let label: String
    let count: Int
    
    var body: some View {
        HStack(spacing: 8) {
            Circle()
                .fill(color)
                .frame(width: 12, height: 12)
            Text(label)
                .font(.caption)
                .foregroundColor(.secondary)
            Spacer()
            Text("\(count)")
                .font(.caption)
                .fontWeight(.medium)
        }
    }
}

#Preview {
    AttendanceChart(presentCount: 16, absentCount: 4, requiredPercentage: 75)
        .frame(height: 100)
        .padding()
}
