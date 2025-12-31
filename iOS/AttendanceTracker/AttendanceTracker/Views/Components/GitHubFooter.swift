import SwiftUI

/// Footer component with GitHub link
struct GitHubFooter: View {
    var body: some View {
        HStack {
            Spacer()
            Link(destination: URL(string: "https://github.com/hrishikeshp7/web-sf-hf")!) {
                HStack(spacing: 8) {
                    Image(systemName: "star.fill")
                        .font(.caption)
                    Text("Star on GitHub")
                        .font(.caption)
                        .fontWeight(.medium)
                }
                .foregroundColor(.secondary)
            }
            Spacer()
        }
        .padding(.vertical, 12)
        .background(Color(.systemBackground))
    }
}

#Preview {
    GitHubFooter()
}
