import Foundation

/// Shares only the language choice, never puzzle, photo or feedback preferences.
public enum SharedImageLanguage {
    public static func save(selection: String, container: URL) throws {
        let url = container.appendingPathComponent("ShareLanguage.json")
        try JSONEncoder().encode(selection).write(to: url, options: [.atomic, .completeFileProtection])
        var values = URLResourceValues(); values.isExcludedFromBackup = true
        var excluded = url; try excluded.setResourceValues(values)
    }
    public static func resolve(container: URL?, supported: [String], preferred: [String]) -> String {
        if let container, let data = try? Data(contentsOf: container.appendingPathComponent("ShareLanguage.json")),
           let selected = try? JSONDecoder().decode(String.self, from: data), supported.contains(selected) { return selected }
        return Bundle.preferredLocalizations(from: supported, forPreferences: preferred).first ?? "en"
    }
}
