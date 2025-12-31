import SwiftUI

/// App theme colors matching the Android version
extension Color {
    static let presentGreen = Color(red: 0.3, green: 0.69, blue: 0.31) // #4CAF50
    static let absentRed = Color(red: 0.96, green: 0.26, blue: 0.21) // #F44336
    static let noClassGray = Color(red: 0.62, green: 0.62, blue: 0.62) // #9E9E9E
    
    // Primary colors
    static let primaryLight = Color(red: 0.4, green: 0.28, blue: 0.75) // Purple
    static let primaryDark = Color(red: 0.82, green: 0.68, blue: 1.0)
    
    // Surface colors
    static let surfaceLight = Color(red: 0.99, green: 0.96, blue: 1.0)
    static let surfaceDark = Color(red: 0.14, green: 0.11, blue: 0.18)
}

/// Theme configuration
struct AppTheme {
    static func primaryColor(for colorScheme: ColorScheme) -> Color {
        colorScheme == .dark ? .primaryDark : .primaryLight
    }
    
    static func backgroundColor(for colorScheme: ColorScheme) -> Color {
        colorScheme == .dark ? .surfaceDark : .surfaceLight
    }
}
