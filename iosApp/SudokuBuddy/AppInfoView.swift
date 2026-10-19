import SwiftUI
import PuzzlePersistence

struct AppInfoView: View {
    @ObservedObject var puzzle: PuzzleModel
    @ObservedObject private var language = AppLanguage.shared
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        NavigationStack {
            List {
                Section { Text("Sudoku Buddy " + (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "")).foregroundStyle(AppStyle.secondary) }
                Section(tr("What it does")) {
                    Text(tr("Point the camera at a sudoku puzzle on paper. The app reads the printed digits and anything you have written in, solves it, and can give you a hint, check your answers, or show the whole solution."))
                    Text(tr("Recognition can make mistakes, especially with handwriting. Use Read to compare the recognised digits with the photo, and tap any square to correct it. The app marks uncertain readings for review; a confident reading can still be wrong."))
                }
                Section(tr("Your photos and your privacy")) {
                    Text(tr("Puzzles are read and solved on this phone. If you submit an uncertain reading, or enable automatic sharing, the puzzle photo, original readings, uncertainty flags and your corrections are sent securely to Freevia for recognition analysis and private training. Reports do not include your device model or diagnostics. Automatic sharing is optional; there are no accounts, routine usage analytics or ad tracking. Training copies can be deleted by receipt, though a model already trained may retain learned changes. Use Submission receipts in Settings to delete a report."))
                    Text(tr("Photographs you keep are stored in this app's private storage and are removed when you delete them or uninstall the app."))
                    Text(tr("Sudoku Buddy is published by Freevia. Contact: info@freevia.org"))
                    Link(tr("Privacy policy"), destination: URL(string: "https://freevia.org/sudoku-buddy/privacy")!)
                }
                Section(tr("Open source notices")) {
                    Text(tr("Sudoku Buddy source code is licensed under Apache License 2.0. Freevia branding is subject to separate trademark rights."))
                    NavigationLink(tr("Licenses")) { LicensesView() }.accessibilityIdentifier("licenses")
                }
                Section(tr("Puzzles")) {
                    Text(tr("Sudoku is a public-domain puzzle form. This app solves puzzles you already have; it does not reproduce or distribute anyone's puzzles."))
                    Text(tr("Puzzles photographed for testing came from sudoku.cba.si. This app is not affiliated with them."))
                }
            }
            .listStyle(.plain).scrollContentBackground(.hidden).background(AppStyle.background)
            .navigationBarTitleDisplayMode(.inline).navigationTitle(tr("About"))
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(tr("Done")) { dismiss() }.accessibilityIdentifier("appInfoDone") } }
        }
    }
}

struct LanguageView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject private var language = AppLanguage.shared
    var body: some View {
        List {
            choice("", title: tr("Use device language"))
            ForEach(language.languages) { locale in choice(locale.id, title: locale.name) }
        }
        .navigationBarTitleDisplayMode(.inline).navigationTitle(tr("Choose language"))
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button(tr("Done")) { dismiss() }.accessibilityIdentifier("languageDone")
            }
        }
    }
    private func choice(_ id: String, title: String) -> some View {
        Button { language.selection = id } label: {
            HStack {
                Text(title).foregroundStyle(.primary)
                Spacer()
                if language.selection == id { Image(systemName: "checkmark").foregroundStyle(AppStyle.accent) }
            }
        }
        .accessibilityIdentifier("language-\(id.isEmpty ? "system" : id)")
        .accessibilityAddTraits(language.selection == id ? .isSelected : [])
    }
}

private struct LicensesView: View {
    private var files: [URL] {
        guard let root = Bundle.main.resourceURL?.appendingPathComponent("Licenses") else { return [] }
        return ((try? FileManager.default.contentsOfDirectory(at: root, includingPropertiesForKeys: nil)) ?? [])
            .sorted { $0.lastPathComponent < $1.lastPathComponent }
    }
    var body: some View {
        List(files, id: \.self) { file in
            let name = file.deletingPathExtension().lastPathComponent
            NavigationLink(name) {
                ScrollView {
                    Text(verbatim: (try? String(contentsOf: file, encoding: .utf8)) ?? tr("Open source notices")).font(AndroidText.bodySmall).textSelection(.enabled)
                        .frame(maxWidth: .infinity, alignment: .leading).padding()
                }.navigationBarTitleDisplayMode(.inline).navigationTitle(name)
            }.accessibilityIdentifier("notice-\(name)")
        }.navigationBarTitleDisplayMode(.inline).navigationTitle(tr("Licenses"))
    }
    private func notice(_ name: String) -> String {
        guard let url = Bundle.main.url(forResource: name, withExtension: "txt", subdirectory: "Licenses"),
              let text = try? String(contentsOf: url, encoding: .utf8) else { return tr("Open source notices") }
        return text
    }
}

private struct RefusedScansView: View {
    @ObservedObject var puzzle: PuzzleModel
    @State private var photo: OriginalPhotoShare?
    var body: some View {
        List {
            Text(tr("Photos the app could not make a puzzle out of. Send one and it can be looked at."))
            Text(tr("Photographs you keep are stored in this app's private storage and are removed when you delete them or uninstall the app."))
            ForEach(puzzle.refusedScans) { scan in
                VStack(alignment: .leading, spacing: 10) {
                    Text(scan.at, format: .dateTime)
                    Text(tr(scan.reason)).font(AndroidText.bodyMedium)
                    HStack {
                        Button(tr("Send this photo")) {
                            photo = puzzle.refusedPhoto(scan).flatMap { try? OriginalPhotoShare(data: $0) }
                        }.buttonStyle(.bordered)
                        Button(tr("Delete this photo"), role: .destructive) { puzzle.deleteRefusedScan(scan) }.buttonStyle(.bordered)
                    }
                }
            }
        }
        .navigationBarTitleDisplayMode(.inline).navigationTitle(tr("Refused photographs"))
        .onAppear { puzzle.reloadRefusedScans() }
        .sheet(item: $photo, onDismiss: { photo = nil }) { ShareSheet(items: [$0, puzzle.diagnostics]) }
    }
}
