import SwiftUI
import UIKit
import ImageIO
import UniformTypeIdentifiers
import PuzzlePersistence

struct ReportConsentView: View {
    let snapshot: ReportSnapshot
    @ObservedObject private var language = AppLanguage.shared
    @ObservedObject private var feedback = FeedbackModel.shared
    @Environment(\.dismiss) private var dismiss
    @State private var consent = false
    @State private var automatic = false
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    if let image = UIImage(data: snapshot.jpeg) {
                        Image(uiImage: image).resizable().scaledToFit().frame(maxHeight: 320)
                            .frame(maxWidth: .infinity).accessibilityLabel(tr("Review reading"))
                    }
                    DisclosureGroup(tr("Review reading")) {
                        Text(verbatim: snapshot.currentGrid).font(.system(.body, design: .monospaced))
                            .environment(\.layoutDirection, .leftToRight)
                    }
                    Text(tr("Thanks for helping improve recognition. Your puzzle photo, readings and corrections will be used for analysis and training."))
                    Text(tr("Puzzles are read and solved on this phone. If you submit an uncertain reading, or enable automatic sharing, the puzzle photo, original readings, uncertainty flags and your corrections are sent securely to Freevia for recognition analysis and private training. Reports do not include your device model or diagnostics. Automatic sharing is optional; there are no accounts, routine usage analytics or ad tracking. Training copies can be deleted by receipt, though a model already trained may retain learned changes. Use Submission receipts in Settings to delete a report."))
                    Text(tr("Each receipt identifies one submitted puzzle revision. Delete it here to remove the privately stored report from Freevia."))
                    Button { consent.toggle() } label: {
                        HStack(spacing: 12) {
                            Image(systemName: consent ? "checkmark.square.fill" : "square").foregroundStyle(AppStyle.accent).accessibilityHidden(true)
                            Text(tr("Submit reading")).foregroundStyle(.primary)
                        }.frame(maxWidth: .infinity, minHeight: 44, alignment: .leading).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(tr("Submit reading"))
                    .accessibilityAddTraits(consent ? .isSelected : [])
                    .accessibilityIdentifier("reportConsent")
                    Toggle(tr("Submit future uncertain readings automatically"), isOn: $automatic)
                        .toggleStyle(.switch).accessibilityIdentifier("futureReportConsent")
                    Text(tr("Includes their photos, readings and corrections. Turn this off in Settings.")).font(AndroidText.bodySmall)
                    if feedback.endpoint == nil { Text(tr("Submissions are not available in this build.")) }
                    Button(tr("Submit")) {
                        AppSettings.shared.setAutomaticSharing(automatic)
                        Task { await feedback.submit(snapshot, consent: consent, automatic: automatic) }
                    }
                    .buttonStyle(PuzzleActionStyle(active: true))
                    .disabled(!consent || feedback.busy || feedback.endpoint == nil)
                    .accessibilityIdentifier("submitReport")
                    if feedback.busy { ProgressView() }
                    if let notice = feedback.notice { Text(tr(notice)).accessibilityIdentifier("reportStatus") }
                    NavigationLink(tr("Submission receipts")) { ReceiptsView() }.accessibilityIdentifier("reportReceipts")
                }.padding().frame(maxWidth: 620).frame(maxWidth: .infinity)
            }
            .accessibilityIdentifier("reportForm")
            .onAppear { automatic = AppSettings.shared.automaticSharing; feedback.notice = nil }
            .navigationBarTitleDisplayMode(.inline).navigationTitle(tr("Submit reading"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(tr("Close")) { dismiss() } } }
        }
    }
}

struct ReceiptsView: View {
    @ObservedObject private var language = AppLanguage.shared
    @ObservedObject private var feedback = FeedbackModel.shared
    @State private var deleting: SubmissionReceipt?
    var body: some View {
        List {
            Section {
                Text(tr("Each receipt identifies one submitted puzzle revision. Delete it here to remove the privately stored report from Freevia."))
                ForEach(feedback.receipts) { receipt in
                    VStack(alignment: .leading, spacing: 8) {
                        Text(verbatim: receipt.id).font(.caption.monospaced()).textSelection(.enabled)
                        Text(tr(receipt.state == .accepted ? "Submitted" : receipt.state == .deleted ? "Removed" : "Needs confirmation"))
                            .accessibilityIdentifier("receipt-state-\(receipt.state.rawValue)")
                        Text(receipt.consentedAt, format: .dateTime.day().month().year().hour().minute()).font(AndroidText.bodySmall)
                        if receipt.state != .deleted {
                            HStack {
                                if receipt.state == .pending && !receipt.deletionRequested {
                                    Button(tr("Submit")) { Task { await feedback.retry(receipt) } }.accessibilityIdentifier("retryReport")
                                }
                                Button(tr("Delete report"), role: .destructive) { deleting = receipt }.accessibilityIdentifier("deleteReport")
                            }.buttonStyle(.borderless).disabled(feedback.busy)
                        }
                    }
                    .padding(.vertical, 8)
                }
            }
            if let notice = feedback.notice { Text(tr(notice)).accessibilityIdentifier("receiptStatus") }
            if feedback.busy { ProgressView() }
            Button(tr("Copy deletion request")) {
                UIPasteboard.general.string = "Please delete my Sudoku Buddy recognition submissions. Receipt IDs: " +
                    feedback.receipts.filter { $0.state != .deleted }.map(\.id).joined(separator: ", ")
            }.disabled(feedback.receipts.isEmpty)
        }
        .navigationBarTitleDisplayMode(.inline).navigationTitle(tr("Submission receipts"))
        .onAppear { feedback.reload() }
        .confirmationDialog(tr("Delete this submission?"), isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }), titleVisibility: .visible, presenting: deleting) { receipt in
            Button(tr("Delete report"), role: .destructive) { Task { await feedback.delete(receipt) }; deleting = nil }.accessibilityIdentifier("confirmReportDeletion")
        } message: { _ in
            Text(tr("Freevia will delete this report revision and its private training copy. Future corrections may create a new report under your sharing settings. A model already trained on it may retain learned changes."))
        }
    }
}

struct FeedbackSettingsView: View {
    @ObservedObject var puzzle: PuzzleModel
    @ObservedObject private var language = AppLanguage.shared
    @ObservedObject private var settings = AppSettings.shared
    @ObservedObject private var feedback = FeedbackModel.shared
    @State private var confirmAutomatic = false
    var body: some View {
        List {
            Section {
                NavigationLink { LanguageView() } label: {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(tr("Language"))
                        Text(language.languages.first(where: { $0.id == language.selection })?.name ?? tr("Use device language"))
                            .font(AndroidText.bodySmall).foregroundStyle(AppStyle.secondary)
                    }
                }.accessibilityIdentifier("languageSettings")
            }.listRowBackground(AppStyle.background).listRowSeparator(.hidden)
            Section {
                Toggle(isOn: $settings.explainHints) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(tr("Explain hints"))
                        Text(tr("Name the technique and show the squares that prove it, instead of just giving the digit.")).font(AndroidText.bodySmall).foregroundStyle(AppStyle.secondary)
                    }.frame(maxWidth: .infinity, alignment: .leading).contentShape(Rectangle()).onTapGesture { settings.explainHints.toggle() }
                }
                .accessibilityIdentifier("explainHints")
                Toggle(isOn: $settings.shortChains) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(tr("Keep forcing chains short"))
                        Text(tr("Compare forcing chains and prefer ones with fewer squares to follow. Shorter chains can mean more steps in the full solution.")).font(AndroidText.bodySmall).foregroundStyle(AppStyle.secondary)
                    }.frame(maxWidth: .infinity, alignment: .leading).contentShape(Rectangle()).onTapGesture { settings.shortChains.toggle() }
                }
                .accessibilityIdentifier("shortChains")
                Toggle(isOn: $settings.autoCapture) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(tr("Take the photo automatically"))
                        Text(tr("Fire the shutter once the grid is square in frame and steady. The button always works either way.")).font(AndroidText.bodySmall).foregroundStyle(AppStyle.secondary)
                    }.frame(maxWidth: .infinity, alignment: .leading).contentShape(Rectangle()).onTapGesture { settings.autoCapture.toggle() }
                }
                .accessibilityIdentifier("autoCapture")
            }.listRowBackground(AppStyle.background).listRowSeparator(.hidden)
            Section {
                Toggle(isOn: Binding(get: { settings.automaticSharing }, set: {
                    if $0 { confirmAutomatic = true } else { settings.setAutomaticSharing(false) }
                })) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(tr("Share automatically when uncertain"))
                        Text(tr("Automatically share uncertain puzzle photos, reading results and later cell corrections with Freevia for recognition analysis and training. Off by default."))
                            .font(AndroidText.bodySmall).foregroundStyle(AppStyle.secondary)
                    }.frame(maxWidth: .infinity, alignment: .leading).contentShape(Rectangle()).onTapGesture {
                        if settings.automaticSharing { settings.setAutomaticSharing(false) } else { confirmAutomatic = true }
                    }
                }.disabled(feedback.endpoint == nil).accessibilityIdentifier("automaticSharing")
                if feedback.endpoint == nil { Text(tr("Submissions are not available in this build.")) }
                NavigationLink(tr("Submission receipts")) { ReceiptsView() }.accessibilityIdentifier("settingsReceipts")
            }.listRowBackground(AppStyle.background).listRowSeparator(.hidden)
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(AppStyle.background)
        .navigationBarTitleDisplayMode(.inline).navigationTitle(tr("Settings"))
        .alert(tr("Share uncertain readings automatically?"), isPresented: $confirmAutomatic) {
            Button(tr("Turn on")) {
                settings.setAutomaticSharing(true)
                if let report = puzzle.reportSnapshot(automatic: true) { feedback.offerAutomatic(report) }
            }.accessibilityIdentifier("confirmAutomaticSharing")
            Button(tr("Cancel"), role: .cancel) { confirmAutomatic = false }.accessibilityIdentifier("cancelAutomaticSharing")
        } message: {
            Text(tr("Future uncertain readings will send the straightened puzzle photo, recognition results and corrections to Freevia for analysis and training. Training copies remain until deleted from Submission receipts; a trained model may retain learned changes. If an uncertain puzzle is already open, it will be sent now. You can turn this off in Settings."))
        }
    }
}

struct BugReportView: View {
    @ObservedObject var puzzle: PuzzleModel
    @ObservedObject private var language = AppLanguage.shared
    @State private var includeDiagnostics = false
    @State private var error: String?
    var body: some View {
        Form {
            Section {
                Text(tr("Open a draft in your browser with the app version, phone model, Android version and recent scan diagnostics filled in. Add a full description and relevant screenshots yourself.\n\nGitHub sign-in is required. You can review and edit the draft before submitting it. Submitted reports and attachments are public. No photographs are attached automatically."))
                Toggle(isOn: $includeDiagnostics) {
                    Text(tr("Send diagnostics")).frame(maxWidth: .infinity, alignment: .leading).contentShape(Rectangle()).onTapGesture { includeDiagnostics.toggle() }
                }.accessibilityIdentifier("includeBugDiagnostics")
                DisclosureGroup(tr("Review reading")) { Text(verbatim: draftText).font(AndroidText.bodySmall).textSelection(.enabled).accessibilityIdentifier("bugDraftPreview") }
                Button(tr("Open GitHub")) {
                    guard let url = draftURL else { return }
                    UIApplication.shared.open(url) { opened in
                        if !opened { error = "Could not open GitHub. Contact support at info@freevia.org." }
                    }
                }.accessibilityIdentifier("openBugDraft")
                if let error { Text(tr(error)) }
            }
        }.navigationBarTitleDisplayMode(.inline).navigationTitle(tr("Report a bug or idea on GitHub"))
    }
    private var draftURL: URL? {
        BugDraft.url(version: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown",
                     build: Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "unknown",
                     device: UIDevice.current.model, system: UIDevice.current.systemVersion,
                     diagnostics: includeDiagnostics ? puzzle.diagnostics : "")
    }
    private var draftText: String { draftURL.flatMap { URLComponents(url: $0, resolvingAgainstBaseURL: false) }?.queryItems?.first { $0.name == "body" }?.value ?? "" }
}

struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController { UIActivityViewController(activityItems: items, applicationActivities: nil) }
    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

/// Share the exact refused capture, including orientation metadata, as a private temporary file.
final class OriginalPhotoShare: NSObject, UIActivityItemSource, Identifiable {
    var id: ObjectIdentifier { ObjectIdentifier(self) }
    private let directory: URL
    private let url: URL
    init(data: Data) throws {
        directory = FileManager.default.temporaryDirectory.appendingPathComponent("ScanShare-" + UUID().uuidString)
        let type = CGImageSourceCreateWithData(data as CFData, nil).flatMap { CGImageSourceGetType($0) }.flatMap { UTType($0 as String) }
        url = directory.appendingPathComponent("refused-scan." + (type?.preferredFilenameExtension ?? "bin"))
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try data.write(to: url, options: .atomic)
        super.init()
    }
    deinit { try? FileManager.default.removeItem(at: directory) }
    func activityViewControllerPlaceholderItem(_ activityViewController: UIActivityViewController) -> Any { url }
    func activityViewController(_ activityViewController: UIActivityViewController, itemForActivityType activityType: UIActivity.ActivityType?) -> Any? { url }
}
