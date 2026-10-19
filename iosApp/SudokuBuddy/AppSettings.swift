import Foundation
import Combine

@MainActor
final class AppSettings: ObservableObject {
    static let shared = AppSettings()
    private let defaults: UserDefaults
    private static let consentVersion = "2026-10-10-analysis-training"
    @Published var explainHints: Bool { didSet { defaults.set(explainHints, forKey: "explainHints") } }
    @Published var shortChains: Bool { didSet { defaults.set(shortChains, forKey: "shortChains") } }
    @Published var autoCapture: Bool { didSet { defaults.set(autoCapture, forKey: "autoCapture") } }
    @Published private(set) var automaticSharing: Bool
    private init() {
        defaults = AppLanguage.shared.preferences
        explainHints = defaults.object(forKey: "explainHints") as? Bool ?? true
        shortChains = defaults.object(forKey: "shortChains") as? Bool ?? true
        autoCapture = defaults.object(forKey: "autoCapture") as? Bool ?? true
        automaticSharing = defaults.bool(forKey: "automaticSharing") && defaults.string(forKey: "sharingConsentVersion") == Self.consentVersion
    }
    func setAutomaticSharing(_ enabled: Bool) {
        automaticSharing = enabled
        defaults.set(enabled, forKey: "automaticSharing")
        defaults.set(enabled ? Self.consentVersion : "", forKey: "sharingConsentVersion")
        if !enabled { FeedbackModel.shared.stopAutomatic() }
    }
}
