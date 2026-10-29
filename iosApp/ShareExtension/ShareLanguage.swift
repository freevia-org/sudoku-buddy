import Foundation
import PuzzlePersistence

/// Uses a generated subset of the containing app's 71 catalogs, without linking its UI/solver.
struct ShareLanguage {
    let code: String
    private let messages: [String: String]
    var isRTL: Bool { ["ar", "he", "fa"].contains(code) }
    init(bundle: Bundle = .main) {
        let supported = bundle.localizations.filter { $0 != "Base" }
        code = SharedImageLanguage.resolve(
            container: FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: SharedImageInbox.group),
            supported: supported, preferred: Locale.preferredLanguages)
        func read(_ code: String) -> [String: String]? {
            guard let path = bundle.path(forResource: code, ofType: "lproj"),
                  let data = try? Data(contentsOf: URL(fileURLWithPath: path).appendingPathComponent("Messages.json")) else { return nil }
            return try? JSONDecoder().decode([String: String].self, from: data)
        }
        messages = read(code) ?? read("en") ?? [:]
    }
    func text(_ source: String) -> String { messages[source] ?? source }
}
