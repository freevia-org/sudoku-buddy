import Foundation

/// An explicitly opened, editable browser draft. This function performs no networking.
public enum BugDraft {
    public static func url(version: String, build: String, device: String, system: String, diagnostics: String) -> URL? {
        func field(_ text: String) -> String { String(text.replacingOccurrences(of: "\n", with: " ").replacingOccurrences(of: "\r", with: " ").prefix(60)) }
        let diagnostic = diagnostics.replacingOccurrences(of: "```", with: "'''")
        var limit = min(diagnostic.count, 1200)
        while true {
            let body = """
            ## Issue or idea
            Please describe the problem or idea and give this report a descriptive title.

            ## Expected behavior or proposed improvement
            What did you expect?

            ## What happened and steps to reproduce (for bugs)
            Describe the steps, expected and actual results, and whether you corrected the recognized digits first.

            ## Screenshots or puzzle examples
            Attach relevant examples yourself using GitHub's attachment control.
            Reports and attachments are public. No photos are attached automatically.
            For private examples, contact info@freevia.org.

            ## App and device (prefilled)
            - App: Sudoku Buddy \(field(version)) (\(field(build)))
            - Device: \(field(device))
            - iOS: \(field(system))

            ## Recent scan diagnostics (prefilled)
            ```text
            \(diagnostic.isEmpty ? "No scan diagnostics available." : String(diagnostic.prefix(limit)))
            ```
            """
            var parts = URLComponents(string: "https://github.com/freevia-org/sudoku-buddy/issues/new")!
            parts.queryItems = [URLQueryItem(name: "title", value: "Sudoku Buddy feedback - \(field(version))"), URLQueryItem(name: "body", value: body)]
            if let url = parts.url, url.absoluteString.count <= 7500 { return url }
            if limit == 0 { return nil }
            limit = max(0, limit - 100)
        }
    }
}
