import Foundation
import Combine
import SudokuShared
import PuzzlePersistence

struct AppLocale: Codable, Identifiable {
    let id: String
    let name: String
}

/// Translates at presentation time; saved puzzles and solver identifiers stay language-neutral.
@MainActor
final class AppLanguage: ObservableObject {
    static let shared = AppLanguage()
    let languages: [AppLocale]
    let preferences: UserDefaults
    private var translator: MessageTranslator?
    @Published private(set) var resolved = "en"
    @Published var selection: String {
        didSet {
            preferences.set(selection, forKey: "appLanguage")
            reload()
        }
    }
    var locale: Locale { Locale(identifier: resolved) }
    var isRTL: Bool { ["ar", "he", "fa"].contains(resolved) }

    private init() {
        let args = ProcessInfo.processInfo.arguments
        var defaults = UserDefaults.standard
        #if DEBUG
        if let flag = args.firstIndex(of: "--history-test-session"), args.indices.contains(flag + 1),
           let id = UUID(uuidString: args[flag + 1]), let isolated = UserDefaults(suiteName: "UITest-\(id.uuidString)") {
            defaults = isolated
        }
        #endif
        preferences = defaults
        let url = Bundle.main.url(forResource: "languages", withExtension: "json")
        languages = url.flatMap { try? Data(contentsOf: $0) }.flatMap { try? JSONDecoder().decode([AppLocale].self, from: $0) } ?? [AppLocale(id: "en", name: "English")]
        var chosen = defaults.string(forKey: "appLanguage") ?? ""
        #if DEBUG
        if let flag = args.firstIndex(of: "--app-language"), args.indices.contains(flag + 1) { chosen = args[flag + 1] }
        #endif
        selection = languages.contains { $0.id == chosen } ? chosen : ""
        reload()
        NotificationCenter.default.addObserver(forName: NSLocale.currentLocaleDidChangeNotification, object: nil, queue: .main) { [weak self] _ in
            Task { @MainActor in if self?.selection == "" { self?.reload() } }
        }
    }

    private func reload() {
        if let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: SharedImageInbox.group) {
            try? SharedImageLanguage.save(selection: selection, container: container)
        }
        resolved = selection.isEmpty
            ? Bundle.preferredLocalizations(from: languages.map(\.id), forPreferences: Locale.preferredLanguages).first ?? "en"
            : selection
        func read(_ code: String) -> [String: String]? {
            guard let path = Bundle.main.path(forResource: code, ofType: "lproj"),
                  let data = try? Data(contentsOf: URL(fileURLWithPath: path).appendingPathComponent("Messages.json")) else { return nil }
            return try? JSONDecoder().decode([String: String].self, from: data)
        }
        let messages = read(resolved) ?? read("en") ?? [:]
        let sources = messages.keys.sorted()
        translator = MessageTranslator(sources: sources, translations: sources.map { messages[$0]! })
    }

    func text(_ source: String) -> String { translator?.translate(text: source) ?? source }
}

@MainActor
func tr(_ source: String) -> String { AppLanguage.shared.text(source) }
