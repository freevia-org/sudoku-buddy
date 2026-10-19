import SwiftUI
import PuzzlePersistence
import SudokuShared

/// The puzzle drawer answers “which puzzle?”, as on Android. It stays over the
/// current board rather than replacing it with another navigation screen.
struct HistoryView: View {
    @ObservedObject private var language = AppLanguage.shared
    @ObservedObject var model: PuzzleModel
    var onScan: () -> Void = {}
    var onClose: () -> Void = {}
    var onOpen: (() -> Void)? = nil
    @State private var deleting: SavedPuzzle?
    @State private var sharing: UIImage?
    @State private var summaries: [UUID: String] = [:]
    @State private var refusedSharing: OriginalPhotoShare?
    @State private var deletingRefused: RefusedScan?

    private var days: [Date] {
        Array(Set(model.history.map { Calendar.current.startOfDay(for: $0.createdAt) })).sorted(by: >)
    }
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(tr("Your puzzles")).font(AndroidText.titleMedium)
                Spacer()
                Button(action: onClose) { Image(systemName: "xmark").frame(width: 48, height: 48) }
                    .accessibilityLabel(tr("Close")).accessibilityIdentifier("historyDone")
            }.padding(.horizontal, 16).padding(.vertical, 8)
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 20) {
                    Button(action: onScan) {
                        HStack(spacing: 16) {
                            Image(systemName: "camera.fill").font(AndroidText.titleLarge)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(tr("New sudoku")).font(AndroidText.labelLarge)
                                Text(tr("Photograph another puzzle.")).font(AndroidText.bodySmall)
                            }
                        }.frame(maxWidth: .infinity, minHeight: 56, alignment: .leading)
                    }.accessibilityIdentifier("historyScan")
                    Divider()
                    if let notice = model.storageNotice {
                        Text(tr(notice)).foregroundStyle(AppStyle.uncertain)
                        Button(tr("Retry saving current puzzle")) { model.persist(touch: false) }
                    }
                    if model.history.isEmpty {
                        Text(tr("Puzzles you photograph are kept here, so you can pick one up again later."))
                    }
                    ForEach(days, id: \.self) { day in
                        Text(day, format: .dateTime.weekday(.wide).day().month(.wide).year())
                            .font(AndroidText.labelLarge).foregroundStyle(AppStyle.secondary)
                        ForEach(model.history.filter { Calendar.current.isDate($0.createdAt, inSameDayAs: day) }.sorted { $0.createdAt > $1.createdAt }) { entry in
                            HStack(spacing: 10) {
                                Button {
                                    if model.open(entry) { if let onOpen { onOpen() } else { onClose() } }
                                } label: {
                                    HStack(spacing: 12) {
                                        thumbnail(entry)
                                        VStack(alignment: .leading, spacing: 4) {
                                            Text(entry.createdAt, format: .dateTime.hour().minute())
                                                .foregroundStyle(entry.id == model.currentID ? AppStyle.primary : AppStyle.onSurface)
                                                .font(AndroidText.labelLarge)
                                            Text(tr(summaries[entry.id] ?? "\(entry.clueCount) printed"))
                                                .font(AndroidText.bodySmall).foregroundStyle(AppStyle.onSurface)
                                                .task(id: entry.updatedAt) {
                                                    let clues = entry.givens.map { $0 == 0 ? "." : String($0) }.joined()
                                                    let answers = entry.progress.map { $0 == 0 ? "." : String($0) }.joined()
                                                    summaries[entry.id] = await Task.detached { PuzzlePresentationEngine().historySummary(givens: clues, progress: answers) }.value
                                                }
                                        }.frame(maxWidth: .infinity, alignment: .leading)
                                    }
                                }.buttonStyle(.plain).accessibilityIdentifier("history-open-\(entry.id.uuidString)")
                                if entry.hasPhoto {
                                    Button { sharing = model.historyThumbnail(entry) } label: { Image(systemName: "square.and.arrow.up").frame(width: 40, height: 48) }
                                        .accessibilityLabel(tr("Send this photo"))
                                }
                                Button { deleting = entry } label: { Image(systemName: "trash.fill").frame(width: 40, height: 48) }
                                    .accessibilityLabel(tr("Delete puzzle")).accessibilityIdentifier("history-delete-\(entry.id.uuidString)")
                            }
                        }
                    }
                    if !model.refusedScans.isEmpty {
                        Divider(); Text(tr("Would not read")).font(AndroidText.titleMedium)
                        Text(tr("Photos the app could not make a puzzle out of. Send one and it can be looked at.")).font(AndroidText.bodySmall)
                        ForEach(model.refusedScans) { scan in
                            VStack(alignment: .leading, spacing: 8) {
                                Text(scan.at, format: .dateTime).font(AndroidText.bodyMedium)
                                Text(tr(scan.reason)).font(AndroidText.bodySmall)
                                HStack {
                                    Button(tr("Send this photo")) { refusedSharing = model.refusedPhoto(scan).flatMap { try? OriginalPhotoShare(data: $0) } }
                                    Spacer()
                                    Button(tr("Delete this photo"), role: .destructive) { deletingRefused = scan }.accessibilityIdentifier("refused-delete-\(scan.id.uuidString)")
                                }
                            }
                        }
                    }
                }.padding(16)
            }
        }
        .foregroundStyle(AppStyle.onSurface).background(AppStyle.drawer)
        .confirmationDialog(tr("Delete this saved puzzle?"), isPresented: Binding(
            get: { deleting != nil }, set: { if !$0 { deleting = nil } }
        ), titleVisibility: .visible, presenting: deleting) { entry in
            Button(tr("Delete saved puzzle"), role: .destructive) { model.delete(entry); deleting = nil }
        } message: { _ in Text(tr("This removes the puzzle and its photo from this device.")) }
        .sheet(isPresented: Binding(get: { sharing != nil }, set: { if !$0 { sharing = nil } })) {
            if let sharing { ShareSheet(items: [sharing]) }
        }
        .onAppear { model.reloadRefusedScans() }
        .sheet(item: $refusedSharing) { ShareSheet(items: [$0, model.diagnostics]) }
        .alert(tr("Delete this photo"), isPresented: Binding(get: { deletingRefused != nil }, set: { if !$0 { deletingRefused = nil } }), presenting: deletingRefused) { scan in
            Button(tr("Delete this photo"), role: .destructive) { model.deleteRefusedScan(scan); deletingRefused = nil }.accessibilityIdentifier("confirmRefusedDeletion")
            Button(tr("Cancel"), role: .cancel) { deletingRefused = nil }
        }
        .tint(AppStyle.primary)
    }
    @ViewBuilder private func thumbnail(_ entry: SavedPuzzle) -> some View {
        if let photo = model.historyThumbnail(entry) {
            Image(uiImage: photo).resizable().scaledToFill().frame(width: 52, height: 52).clipped()
        } else {
            Image(systemName: "square.grid.3x3").frame(width: 52, height: 52).background(AppStyle.surface)
        }
    }
}
