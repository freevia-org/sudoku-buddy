import SwiftUI
import PuzzlePersistence
import SudokuShared

struct PuzzleView: View {
    @ObservedObject private var language = AppLanguage.shared
    @StateObject private var model = PuzzleModel()
    @ObservedObject private var feedback = FeedbackModel.shared
    @State private var reportRevision: String?
    @State private var showReadingReceipts = false
    @Environment(\.scenePhase) private var scenePhase
    private struct StudyPresentation: Identifiable { let id = UUID(); let mode: String }
    private struct ReportPresentation: Identifiable { let id = UUID(); let snapshot: ReportSnapshot }
    @State private var inlineStudy: StudyModel?
    @State private var studyPresentation: StudyPresentation?
    @State private var reportToSubmit: ReportPresentation?
    @State private var shareDiagnostics = false
    @State private var showInfo = false
    @State private var showHistory = false
    @State private var confirmReset = false
    @State private var showScan = false
    @State private var cameraDestination: String?
    @State private var returnToCamera = false
    @State private var confirmScan = false
    @State private var showEditor = false
    @State private var showSettings = false
    @State private var showBugReport = false
    private var layer: String { get { model.overlayMode } nonmutating set { model.overlayMode = newValue } }
    @State private var showEarlierHint = false
    @ScaledMetric private var textScale: CGFloat = 1

    var body: some View {
        NavigationStack {
            GeometryReader { geometry in
                let layout=PuzzlePresentationEngine().layout(width:Double(geometry.size.width),height:Double(geometry.size.height),fontScale:Double(textScale),chrome:72)
                let photoSide=CGFloat(layout.photoSide)
                VStack(spacing: 0) {
                    appBar
                    if layout.sideBySide {
                        HStack(spacing: 0) {
                            activeBoard.frame(width: photoSide, height: photoSide).padding(12)
                            activeControls
                        }
                    } else {
                        activeBoard.frame(width: photoSide, height: photoSide)
                            .padding(.horizontal, 12).padding(.vertical, 8)
                        activeControls
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                .background(AppStyle.background)
                .disabled(model.busy)
            }
            .task {
                #if DEBUG
                let args = ProcessInfo.processInfo.arguments
                if model.currentID == nil, let flag = args.firstIndex(of: "--puzzle-fixture"), args.indices.contains(flag + 1) { model.loadFixture(args[flag + 1]) }
                let rotated = args.contains("--scan-fixture-rotated")
                if args.contains("--scan-fixture") || args.contains("--scan-review-fixture") || rotated {
                    let name = rotated ? "scan-fixture-rotated" : "scan-fixture"
                    let ext = rotated ? "jpg" : "png"
                    if let url = Bundle.main.url(forResource: name, withExtension: ext), let data = try? Data(contentsOf: url) {
                        await model.readPhoto(data)
                        if args.contains("--scan-review-fixture") { model.useUncertainPhotoFixture() }
                    }
                }
                if args.contains("--refused-fixture") {
                    let data = UIGraphicsImageRenderer(size: CGSize(width: 512, height: 512)).jpegData(withCompressionQuality: 0.9) { context in
                        context.cgContext.setFillColor(UIColor.white.cgColor); context.cgContext.fill(CGRect(x: 0, y: 0, width: 512, height: 512))
                    }
                    await model.readPhoto(data)
                }
                #endif
                if let bookmark = model.study, bookmark.open { openStudy(bookmark.mode, technique: bookmark.technique) }
                restoreLayer()
                if layer == "Hint" { await model.refreshHint() } else { await model.refreshOverlay() }
                await FeedbackModel.shared.resumeAutomatic()
                if let snapshot = model.reportSnapshot(automatic: true) { FeedbackModel.shared.offerAutomatic(snapshot) }
                var startCamera = model.currentID == nil
                #if DEBUG
                startCamera = startCamera && (!ProcessInfo.processInfo.arguments.contains("--history-test-session") || ProcessInfo.processInfo.arguments.contains("--camera-test-start"))
                #endif
                if startCamera { showScan = true }
            }
            .task(id: (model.currentID?.uuidString ?? "") + model.cluesText + model.answersText + String(model.recognition?.corrections.count ?? 0)) {
                guard let snapshot = model.reportSnapshot() else { reportRevision = nil; return }
                let digest = await Task.detached { (try? ReportArchive.package(snapshot)).map(ReportArchive.digest) }.value
                guard !Task.isCancelled else { return }; reportRevision = digest
            }
            .onChange(of: model.currentID) { _ in
                inlineStudy = nil
                if let bookmark = model.study, bookmark.open { openStudy(bookmark.mode, technique: bookmark.technique) }
                restoreLayer()
                Task { if layer == "Hint" { await model.refreshHint() } else { await model.refreshOverlay() } }
            }
            .onChange(of: model.cluesText + model.answersText) { _ in
                if let study = inlineStudy, study.givens != model.cluesText || study.progress != model.answersText { closeStudy() }
            }
            .toolbar(.hidden, for: .navigationBar)
            .onChange(of: scenePhase) { phase in if phase != .active { model.persist(touch: false) } }
            .sheet(item: $studyPresentation, onDismiss: { resumeCamera() }) {
                StudyView(puzzle: model, mode: $0.mode, onExplore: { technique in returnToCamera = false; studyPresentation = nil; openStudy("strategy", technique: technique) })
                    .presentationDetents([.large]).presentationDragIndicator(.visible)
            }
            .sheet(item: $reportToSubmit) { ReportConsentView(snapshot: $0.snapshot) }
            .sheet(isPresented: $shareDiagnostics, onDismiss: { resumeCamera() }) { ShareSheet(items: diagnosticShareItems) }
            .sheet(isPresented: $showReadingReceipts) { NavigationStack { ReceiptsView().toolbar { ToolbarItem(placement: .confirmationAction) { Button(tr("Done")) { showReadingReceipts = false } } } } }
            .sheet(isPresented: $showInfo, onDismiss: { resumeCamera() }) { AppInfoView(puzzle: model) }
            .sheet(isPresented: $showSettings, onDismiss: { resumeCamera() }) {
                NavigationStack {
                    FeedbackSettingsView(puzzle: model)
                        .toolbar { ToolbarItem(placement: .confirmationAction) { Button(tr("Done")) { showSettings = false }.accessibilityIdentifier("settingsDone") } }
                }
            }
            .sheet(isPresented: $showBugReport, onDismiss: { resumeCamera() }) { NavigationStack { BugReportView(puzzle: model)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button(tr("Done")) { showBugReport = false }.accessibilityIdentifier("bugReportDone") } }
            } }
            .overlay {
                if showHistory {
                    GeometryReader { geometry in
                        ZStack(alignment: .leading) {
                            Color.black.opacity(0.5).ignoresSafeArea().onTapGesture { showHistory = false; resumeCamera() }.accessibilityLabel(tr("Close"))
                            HistoryView(model: model, onScan: { returnToCamera = false; showHistory = false; requestScan() }, onClose: { showHistory = false; resumeCamera() }, onOpen: { returnToCamera = false; showHistory = false })
                                .frame(width: min(380, geometry.size.width * 0.88))
                        }
                    }.transition(.move(edge: .leading))
                }
            }
            .sheet(isPresented: $showScan, onDismiss: {
                switch cameraDestination {
                case "history": showHistory = true
                case "strategies": openStudy("catalog")
                case "settings": showSettings = true
                case "about": showInfo = true
                case "bug": showBugReport = true
                case "diagnostics": model.reloadRefusedScans(); shareDiagnostics = true
                default: break
                }
                cameraDestination = nil
            }) { ScanSheet(canCancel: cameraCanCancel, onNavigate: { returnToCamera = true; cameraDestination = $0 }) { data in Task { await model.readPhoto(data) } } }
            .sheet(isPresented: $showEditor, onDismiss: { Task { if layer == "Hint", model.hintBookmark != nil { await model.refreshHint() } else { await model.refreshOverlay() } } }) { cellEditor.presentationDetents([.height(460), .large]).presentationDragIndicator(.visible) }
            .confirmationDialog(tr("Scan another puzzle? Your current puzzle will stay in History."), isPresented: $confirmScan, titleVisibility: .visible) {
                Button(tr("Scan another puzzle")) { showScan = true }
            }
            .confirmationDialog(tr("Start a new puzzle? Your current puzzle will stay in History."), isPresented: $confirmReset, titleVisibility: .visible) {
                Button(tr("Start new puzzle")) { model.reset(); layer = "Read" }
            }
        }
        .tint(AppStyle.primary)
    }

    private var cameraCanCancel: Bool {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("--history-test-session") { return true }
        #endif
        return model.currentID != nil
    }
    private func resumeCamera() {
        guard returnToCamera else { return }
        returnToCamera = false
        showScan = true
    }
    private func openStudy(_ mode: String, technique: String = "") {
        if mode == "catalog" { inlineStudy = nil; studyPresentation = StudyPresentation(mode: mode); return }
        inlineStudy = StudyModel(givens: model.cluesText, progress: model.answersText, mode: mode, technique: technique, bookmark: model.study)
    }
    private func closeStudy() {
        if let study = inlineStudy { var bookmark = study.bookmark; bookmark.open = false; model.saveStudy(bookmark) }
        inlineStudy = nil
    }
    @ViewBuilder private var activeBoard: some View {
        if let study = inlineStudy { InlineStudyBoard(study: study, puzzle: model, normal: AnyView(board)) }
        else { board }
    }
    @ViewBuilder private var activeControls: some View {
        if let study = inlineStudy {
            StudyView(puzzle: model, study: study, onClose: { closeStudy() }, onExplore: { technique in
                if technique.isEmpty { openStudy("catalog") } else { openStudy("strategy", technique: technique) }
            }).clipShape(UnevenTutorShape())
        } else { controls }
    }

    private func restoreLayer() {
        if model.hintBookmark != nil { layer = "Hint" }
    }

    @ViewBuilder private func hintPanel(_ hint: HintPresentation) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            if hint.visual == true {
                ScrollView(.horizontal, showsIndicators: false) { HStack(spacing: 0) {
                    ForEach(Array((hint.stageNames ?? []).enumerated()), id: \.offset) { depth, title in
                        Button("\(depth + 1). \(tr(title))") { Task { showEarlierHint = false; await model.chooseHintDepth(depth) } }
                            .font(AndroidText.labelLarge).frame(minHeight:48)
                            .disabled(model.hintDepth == depth).accessibilityIdentifier("hint-stage-\(depth)")
                    }
                } }
                Text(tr(hint.keyCaption ?? "")).font(AndroidText.bodySmall)
                if model.hintDepth == 0 {
                    Text(tr(hint.stageCaption ?? ""))
                } else if model.hintDepth == 1 {
                    Text(tr(hint.stageCaption ?? ""))
                } else if let bookmark = model.hintBookmark, bookmark.frame.step >= 0 {
                    Text(tr(hint.lessonTechnique ?? "")).font(AndroidText.titleMedium).accessibilityIdentifier("hintTechnique")
                    HStack {
                        Button(tr("Before")) { Task { await model.changeHintFrame(before: true) } }.disabled(bookmark.frame.before)
                        Spacer()
                        Button(tr((hint.chainLength ?? -1) >= 0 ? "Test assumption" : "After")) { Task { await model.changeHintFrame(before: false) } }.disabled(!bookmark.frame.before)
                    }
                    Text(tr(hint.caption ?? "")).accessibilityIdentifier("hintExplanation")
                    if let count = hint.chainLength, count >= 0, !bookmark.frame.before {
                        Text(tr(bookmark.frame.consequence > count ? "Assumption rejected · real candidates restored" : bookmark.frame.consequence == count ? "Contradiction · assumption fails" : bookmark.frame.consequence == 0 ? "Assumption · hypothetical digit" : "Consequence \(bookmark.frame.consequence) of \(count - 1) · hypothetical digits")).font(AndroidText.bodySmall)
                        HStack {
                            Button(tr("Previous")) { Task { await model.changeHintFrame(consequence: bookmark.frame.consequence - 1) } }.disabled(bookmark.frame.consequence == 0)
                            Spacer()
                            Button(tr(bookmark.frame.consequence == count ? "Reject assumption" : bookmark.frame.consequence == count - 1 ? "Show contradiction" : "Next consequence")) {
                                Task { await model.changeHintFrame(consequence: bookmark.frame.consequence + 1) }
                            }.disabled(bookmark.frame.consequence > count)
                        }
                    }
                    if let dependencies = hint.dependencies, !dependencies.isEmpty {
                        Button(tr(showEarlierHint ? "Hide earlier evidence" : "Explain earlier candidate removals (\(dependencies.count))")) { showEarlierHint.toggle() }
                        if showEarlierHint {
                            ForEach(dependencies) { question in
                                Button(tr(question.label)) { Task { showEarlierHint = false; await model.visitHint(question.step) } }
                            }
                        }
                    }
                    Button(tr(bookmark.parents.count <= 1 ? "Back to your square" : "Back to the previous question")) { Task { showEarlierHint = false; await model.backHint() } }.accessibilityIdentifier("hint-back")
                } else {
                    if model.hintDepth == 3 {
                        Text(tr(hint.stageCaption ?? "")).font(AndroidText.titleMedium).accessibilityIdentifier("hintAnswer")
                        Text(tr(hint.answerGuidance ?? ""))
                    } else { Text(tr(hint.stageCaption ?? "")) }
                    ForEach(hint.branches ?? []) { question in
                        Button(tr(question.label)) { Task { await model.visitHint(question.step) } }
                    }
                    Button(tr("Why this square? · \(hint.technique ?? "")")) { Task { await model.visitHint(hint.finalStep ?? 0) } }.accessibilityIdentifier("hint-final-proof")
                }
                if model.hintDepth >= 2 {
                    Button(tr("Follow the full proof in order")) { openStudy("proof") }
                }
            } else {
                Text(tr(hint.caption ?? "")).accessibilityIdentifier("hintExplanation")
            }
            Button(tr("Close hint")) { model.closeHint(); layer = "None" }.accessibilityIdentifier("closeHint")
        }.buttonStyle(AndroidTextButtonStyle())
    }

    @ViewBuilder private func hintMarks(_ hint: HintPresentation, index: Int, side: CGFloat) -> some View {
        let green = Color(red: 23/255, green: 107/255, blue: 80/255)
        let link = hint.chain?.links.first(where: { $0.index == index })
        let chainCell = link != nil || hint.chain?.deadEnd.contains(index) == true
        if let link, let at = hint.chain?.links.firstIndex(where: { $0.index == index }) {
            Color.white.opacity(0.94)
            green.opacity(0.18)
            Text(String(link.digit)).font(AppStyle.puzzleFont(size: side * 0.62, weight: .bold)).italic().foregroundStyle(green)
            Text(String(at + 1)).font(AppStyle.puzzleFont(size: side * 0.26, weight: .bold)).foregroundStyle(green)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading).padding(side * 0.04)
        } else if !chainCell, let candidates = hint.candidates, !candidates[index].isEmpty || !(hint.removed?[index].isEmpty ?? true) {
            ZStack {
                Color.white.opacity(0.88).padding(side * 0.07)
                ForEach(Array(Set(candidates[index] + (hint.removed?[index] ?? []))).sorted(), id: \.self) { digit in
                    Text(String(digit)).font(AppStyle.puzzleFont(size: side * 0.27, weight: .bold))
                        .foregroundStyle((hint.removed?[index].contains(digit) ?? false) ? AppStyle.incorrect : Color(red: 23/255, green: 76/255, blue: 55/255))
                        .position(x: (CGFloat((digit - 1) % 3) + 0.5) * side / 3, y: (CGFloat((digit - 1) / 3) + 0.5) * side / 3)
                }
                Path { path in
                    for digit in hint.removed?[index] ?? [] {
                        let x = (CGFloat((digit - 1) % 3) + 0.5) * side / 3, y = (CGFloat((digit - 1) / 3) + 0.5) * side / 3
                        path.move(to: CGPoint(x: x - side * 0.1, y: y + side * 0.1)); path.addLine(to: CGPoint(x: x + side * 0.1, y: y - side * 0.1))
                    }
                }.stroke(AppStyle.incorrect, lineWidth: side * 0.035)
            }
        }
        if let chain = hint.chain { ChainContradiction(chain: chain, index: index, side: side) }
        if hint.focus == index {
            Rectangle().stroke(hint.target == index ? AppStyle.solution : green, lineWidth: side * 0.06).padding(side * 0.04)
        }
        if hint.target == index { Rectangle().stroke(AppStyle.solution, lineWidth: side * 0.08).padding(side * 0.01) }
        if hint.elimination == true, hint.chain == nil, hint.targets?.contains(index) == true {
            Rectangle().stroke(green, lineWidth: side * 0.04).padding(side * 0.1)
        }
    }

    private var diagnosticShareItems: [Any] {
        [model.diagnostics] + model.refusedScans.compactMap { scan -> Any? in
            guard let data = model.refusedPhoto(scan) else { return nil }
            return try? OriginalPhotoShare(data: data)
        }
    }
    private func requestScan() {
        if model.givens.contains(where: { $0 != 0 }) || model.progress.contains(where: { $0 != 0 }) { confirmScan = true }
        else { showScan = true }
    }

    private var appBar: some View {
        HStack(spacing: 4) {
            Button { showHistory = true } label: { Image(systemName: "line.3.horizontal").frame(width: 48, height: 48) }
                .accessibilityLabel(tr("Your puzzles")).accessibilityIdentifier("puzzleHistory")
            VStack(alignment: .leading, spacing: 2) {
                Text("Sudoku Buddy").font(AndroidText.labelLarge).lineLimit(1)
                Text(tr("\((0..<81).filter { model.digit(at: $0) == 0 }.count)/81 empty"))
                    .font(AndroidText.labelSmall).foregroundStyle(AppStyle.secondary)
            }.frame(maxWidth: .infinity, alignment: .leading)
            if model.hasReadingReview && !showingReadingReview {
                Button { Task { model.expandReadingReview();await model.refreshOverlay() } } label: { Image(systemName: "checkmark.circle").frame(width: 48, height: 48) }
                    .accessibilityLabel(tr("Review or submit reading")).accessibilityIdentifier("reviewReading")
            }
            Button { requestScan() } label: { Image(systemName: "camera.fill").frame(width: 48, height: 48) }
                .accessibilityLabel(tr("Take a new photo")).accessibilityIdentifier("scanPuzzle")
            Menu {
                Button(tr("Strategies")) { openStudy("catalog") }
                Button(tr("Settings")) { showSettings = true }.accessibilityIdentifier("appSettings")
                Button(tr("About")) { showInfo = true }.accessibilityIdentifier("appInfo")
                Divider()
                Button(tr("Report a bug or idea on GitHub")) { showBugReport = true }.accessibilityIdentifier("reportBug")
                Button(tr("Send diagnostics")) { model.reloadRefusedScans(); shareDiagnostics = true }
                #if DEBUG
                if ProcessInfo.processInfo.arguments.contains("--history-test-session") {
                    Button(tr("New puzzle")) { confirmReset = true }
                }
                #endif
            } label: { Image(systemName: "ellipsis").rotationEffect(.degrees(90)).frame(width: 48, height: 48) }
                .accessibilityLabel(tr("More")).accessibilityIdentifier("moreMenu")
        }
        .foregroundStyle(AppStyle.onSurface).padding(.horizontal, 4).frame(minHeight: 48)
    }

    private var controls: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing:10) {
                    if model.busy { ProgressView(tr(model.message)) }
                    if showingReadingReview { readingBanner }
                    if let status = model.overlayPresentation?.status, !status.isEmpty, !model.editingClues {
                        Text(tr(status)).accessibilityIdentifier("puzzleStatus")
                    }
                    if !showingReadingReview, layer != "Hint", let overlay = model.overlayPresentation, !overlay.guidance.isEmpty {
                        Text(tr(overlay.guidance)).accessibilityIdentifier(layer == "Solve" ? "solutionLegend" : "puzzleGuidance")
                    }
                    if model.editingClues && !model.reviewingScan {
                        Text(tr(model.message)).accessibilityIdentifier("puzzleStatus")
                    }
                    if layer == "Hint", let hint = model.hintPresentation, hint.available {
                        hintPanel(hint)
                    }
                    if model.editingClues && !model.reviewingScan {
                        HStack {
                            Button(tr(model.scanPreview == nil ? "Start puzzle" : "All correct")) { Task { await model.evaluate(); layer = "None"; await model.refreshOverlay() } }
                                .buttonStyle(PuzzleActionStyle(active: true)).accessibilityIdentifier("startPuzzle")
                            if model.scanPreview == nil {
                                Button(tr("Try an example")) { model.loadExample(); layer = "Read" }.accessibilityIdentifier("loadExample")
                            }
                        }
                    }
                    if let notice = model.storageNotice {
                        Text(tr(notice)).foregroundStyle(AppStyle.uncertain)
                        HStack {
                            Button(tr("Retry saving")) { model.persist(touch: false) }
                            Button(tr("Dismiss notice")) { model.storageNotice = nil }
                        }.accessibilityIdentifier("storageNotice")
                    }
                }.font(AndroidText.body).frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal,16).padding(.vertical,4)
            }.accessibilityIdentifier("puzzleMessages")
            if hasLegend { legend.padding(.horizontal,16).padding(.vertical,4).accessibilityIdentifier("puzzleLegend") }
            ViewThatFits(in: .horizontal) {
                actionRow
                VStack(spacing: 6) {
                    HStack(spacing: 6) { readAction; checkAction }
                    HStack(spacing: 6) { hintAction; solveAction }
                }
            }.padding(.horizontal,16).padding(.vertical,6)
            if model.overlayPresentation?.unique != false || model.editingClues {
            Button {
                openStudy(model.editingClues ? "catalog" : "route")
            } label: {
                VStack(alignment: .leading, spacing: 8) {
                    Capsule().fill(AppStyle.secondary.opacity(0.6)).frame(width: 32, height: 4).frame(maxWidth: .infinity)
                    Text(tr("Tutor")).font(AndroidText.titleMedium).frame(maxWidth: .infinity, alignment: .leading)
                }.padding(.horizontal, 20).padding(.top, 8).padding(.bottom, 16)
                    .background(AppStyle.surface, in: UnevenTutorShape())
            }.foregroundStyle(AppStyle.onSurface).accessibilityIdentifier("tutorMenu")
            }
        }.frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var currentReportReceipt: SubmissionReceipt? { feedback.receipts.first { $0.id == reportRevision } }
    private var readingSubmitLabel: String {
        if feedback.busy { return "Submitting…" }
        if currentReportReceipt?.state == .accepted { return "Submitted" }
        if currentReportReceipt?.state == .deleted { return "Removed" }
        if feedback.receipts.contains(where: { $0.puzzleID == model.currentID && $0.state == .accepted }) { return "Submit update" }
        return "Submit reading"
    }
    private var showingReadingReview: Bool { model.readingReview.visible }
    private var readingBanner: some View {
        VStack(alignment: .leading, spacing:6) {
            HStack {
                Text(tr(model.readingHeadline)).font(AndroidText.labelLarge).foregroundStyle(AppStyle.uncertain)
                Spacer()
                Button { model.collapseReadingReview() } label: { Image(systemName: "chevron.up").frame(width:48,height:48) }.accessibilityLabel(tr("Collapse reading review"))
            }
            if !model.readingNote.isEmpty { Text(tr(model.readingNote)).font(AndroidText.bodySmall).accessibilityIdentifier("readingNote") }
            if !model.openQuestions.isEmpty { Text(tr("Tap a marked square to review it, or confirm all readings are correct.")).font(AndroidText.bodySmall) }
            HStack(spacing: 8) {
                if !model.openQuestions.isEmpty {
                    Button(tr(layer == "Read" ? "All correct" : "Review reading")) { Task {
                        if layer == "Read" { await model.acceptReading() }
                        else { layer="Read";await model.refreshOverlay() }
                    } }.buttonStyle(AndroidTonalButtonStyle()).accessibilityIdentifier("startPuzzle")
                }
                if model.recognition != nil {
                    Button(tr(readingSubmitLabel)) {
                        if let snapshot = model.reportSnapshot() { reportToSubmit = ReportPresentation(snapshot: snapshot) }
                        else { model.storageNotice = "This puzzle could not be prepared." }
                    }.buttonStyle(AndroidTonalButtonStyle()).accessibilityIdentifier("reportReading")
                        .disabled(feedback.busy || currentReportReceipt?.state == .accepted || currentReportReceipt?.state == .deleted)
                }
            }
            if feedback.receipts.contains(where: { $0.puzzleID == model.currentID }) {
                Button(tr("Submission receipt")) { showReadingReceipts = true }
            }
        }.padding(12).background(AppStyle.uncertain.opacity(34.0/255), in: RoundedRectangle(cornerRadius: 10))
    }

    private var actionRow: some View { HStack(spacing: 6) { readAction; checkAction; hintAction; solveAction } }
    private var readAction: some View {
        Button(tr("Read")) { Task { model.closeHint(); await model.refreshOverlay(pressed: "Read") } }
            .buttonStyle(PuzzleActionStyle(active: layer == "Read")).accessibilityIdentifier("readPuzzle")
    }
    private var checkAction: some View {
        Button(tr("Check")) { Task {
            let wasCheck = layer == "Check"
            if model.solutionDigits != nil { model.editClues() }
            await model.evaluate(); model.closeHint()
            if wasCheck { layer = "Check" }
            await model.refreshOverlay(pressed: "Check")
        } }
            .buttonStyle(PuzzleActionStyle(active: layer == "Check")).accessibilityIdentifier("checkAnswers")
    }
    private var hintAction: some View {
        Button(tr("Hint")) { Task { if model.solutionDigits != nil { model.editClues() }; await model.requestHint(showing: layer == "Hint" && model.hintBookmark != nil); layer = model.hintBookmark == nil ? "None" : "Hint" } }
            .buttonStyle(HintActionStyle(progress: layer == "Hint" ? (AppSettings.shared.explainHints ? Double(model.hintDepth + 1) / 4 : 1) : 0)).accessibilityIdentifier("requestHint").disabled(model.overlayPresentation?.canHint == false && model.result == nil)
    }
    private var solveAction: some View {
        Button(tr("Solve")) { Task { model.closeHint(); await model.refreshOverlay(pressed: "Solve") } }
            .buttonStyle(PuzzleActionStyle(active: layer == "Solve")).accessibilityIdentifier("showSolution")
    }

    private var hasLegend: Bool {
        if layer == "Hint",model.hintPresentation?.visual == true { return false }
        if !model.openQuestions.isEmpty || layer == "Read" { return true }
        if layer == "Hint" { return model.hintPresentation?.digits?.contains(where:{$0 != 0}) == true || !(model.hintPresentation?.evidence?.isEmpty ?? true) }
        return !(model.overlayPresentation?.legend.isEmpty ?? true)
    }
    private var legend: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 16) {
                if let overlay = model.overlayPresentation, layer != "Hint" {
                    ForEach(overlay.legend, id: \.self) { entry in
                        switch entry {
                        case "CORRECT": key("Right", AppStyle.correct)
                        case "INCORRECT": key("Wrong", AppStyle.incorrect)
                        case "SOLUTION", "HINT": key("Answer", AppStyle.solution)
                        case "WRITTEN": key("Written", AppStyle.written)
                        case "EVIDENCE": key("Why", Color(red: 23/255, green: 107/255, blue: 80/255))
                        case "PRINTED": key("Printed", AppStyle.accent)
                        case "MARKS": key("Marks", .gray)
                        default: EmptyView()
                        }
                    }
                }
                else if layer == "Hint", let hint = model.hintPresentation {
                    if hint.digits?.contains(where: { $0 != 0 }) == true { key("Answer", AppStyle.solution) }
                    if !(hint.evidence?.isEmpty ?? true) || hint.chain != nil { key("Why", Color(red: 23/255, green: 107/255, blue: 80/255)) }
                }
                else if layer == "Read" { key("Printed", AppStyle.accent); key("Written", AppStyle.written); key("Marks", .gray) }
                if !model.openQuestions.isEmpty { key("Unsure", AppStyle.uncertain) }
            }
        }.fixedSize(horizontal: false, vertical: true)
    }
    private func key(_ title: String, _ color: Color) -> some View {
        HStack(spacing: 5) { RoundedRectangle(cornerRadius: 3).fill(color).frame(width: 11, height: 11); Text(tr(title)).font(AndroidText.labelSmall) }
    }

    private var cellEditor: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    if let description = model.readingDescription { Text(tr(description)).accessibilityIdentifier("cellReading") }
                    if let second = model.secondReading { Text(tr(second)).font(AndroidText.bodyMedium).foregroundStyle(AppStyle.secondary) }
                    LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 3), spacing: 6) {
                        ForEach(1...9, id: \.self) { digit in
                            Button(String(digit)) { model.enter(digit); showEditor = false }.frame(maxWidth: .infinity, minHeight: 48)
                                .background(AppStyle.surface, in: RoundedRectangle(cornerRadius: 8))
                                .accessibilityLabel(tr("Enter \(digit)"))
                        }
                    }.environment(\.layoutDirection, .leftToRight)
                    HStack {
                        Button { model.enter(0); showEditor = false } label: { Label(tr("Empty"), systemImage: "delete.left") }.accessibilityIdentifier("Erase")
                        if model.digit(at: model.selected) != 0 {
                            Button(tr(model.givens[model.selected] == 0 ? "Printed" : "Handwritten")) { model.setRole(asAnswer: model.givens[model.selected] != 0); showEditor = false }
                        }
                    }
                    if model.openQuestions.contains(model.selected) {
                        Button(tr("That is right")) { model.acceptCellReading(); showEditor = false }.buttonStyle(PuzzleActionStyle(active: true))
                    }
                }.padding(20)
            }.background(AppStyle.background)
                .navigationTitle(tr("Row \(model.selected / 9 + 1), column \(model.selected % 9 + 1)")).navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button(tr("Done")) { showEditor = false }.accessibilityIdentifier("cellEditorDone") } }
        }
    }

    /// Explicit cell rectangles share the photographed grid's coordinates. No intrinsic
    /// text/button size participates in the 9×9 layout (including empty cells).
    private var board: some View {
        GeometryReader { geometry in
            ZStack(alignment: .topLeading) {
                if let photo = model.scanPreview {
                    Image(uiImage: photo).resizable().frame(width: geometry.size.width, height: geometry.size.height)
                } else { Color.white }
                if layer == "Solve" { Color.black.opacity(0.35) }
                ForEach(0..<81, id: \.self) { index in
                    let rect = cellRect(index, size: geometry.size)
                    Button {
                        model.select(index)
                        if layer == "Read", !model.editingClues,model.scanPreview == nil { model.editClues() }
                        showEditor = true
                    } label: {
                        ZStack {
                            cellColor(index)
                            if drawsDigit(index) {
                                Text(String(visualDigit(index)))
                                    .font(AppStyle.puzzleFont(size: rect.height * 0.62, weight: .bold))
                                    .minimumScaleFactor(0.5)
                                    .foregroundStyle(cutsOutDigit(index) ? Color.black : digitColor(index))
                                    .blendMode(cutsOutDigit(index) ? .destinationOut : .normal)
                            }
                            if layer == "Hint", let hint = model.hintPresentation {
                                hintMarks(hint, index: index, side: rect.height)
                            } else if model.overlayPresentation?.focus == index {
                                Rectangle().stroke(AppStyle.primary, lineWidth: 2).padding(2)
                            }
                        }.compositingGroup().frame(width: rect.width, height: rect.height)
                            .overlay(alignment: .bottomLeading) { confidenceBar(index, width: rect.width) }
                            .contentShape(Rectangle())
                    }.buttonStyle(.plain).position(x: rect.midX, y: rect.midY)
                        .accessibilityLabel(cellLabel(index))
                        .accessibilityAddTraits(model.selected == index && showEditor ? .isSelected : [])
                        .accessibilityIdentifier("cell-\(index)")
                }
                if model.scanPreview == nil {
                    Path { path in
                        for n in 0...9 {
                            let x = CGFloat(n) * geometry.size.width / 9
                            let y = CGFloat(n) * geometry.size.height / 9
                            path.move(to: CGPoint(x: x, y: 0)); path.addLine(to: CGPoint(x: x, y: geometry.size.height))
                            path.move(to: CGPoint(x: 0, y: y)); path.addLine(to: CGPoint(x: geometry.size.width, y: y))
                        }
                    }.stroke(Color.black.opacity(0.5), lineWidth: 0.5).allowsHitTesting(false)
                    Path { path in
                        for n in stride(from: 0, through: 9, by: 3) {
                            let x = CGFloat(n) * geometry.size.width / 9
                            let y = CGFloat(n) * geometry.size.height / 9
                            path.move(to: CGPoint(x: x, y: 0)); path.addLine(to: CGPoint(x: x, y: geometry.size.height))
                            path.move(to: CGPoint(x: 0, y: y)); path.addLine(to: CGPoint(x: geometry.size.width, y: y))
                        }
                    }.stroke(Color.black.opacity(0.9), lineWidth: 1.5).allowsHitTesting(false)
                }
                if layer == "Hint", let hint = model.hintPresentation {
                    if let chain = hint.chain { ChainArrows(chain: chain, rect: cellRect) }
                    CausalArrows(arrows: hint.causalArrows ?? [], targets: hint.eliminationTargets ?? [], rect: cellRect)
                }

            }.clipped()
        }.aspectRatio(1, contentMode: .fit).environment(\.layoutDirection, .leftToRight)
    }

    private func cellRect(_ index: Int, size: CGSize) -> CGRect {
        let row = index / 9, column = index % 9
        if let lines = model.recognition?.gridLines, let photo = model.scanPreview {
            // Scanner evidence uses normalized line coordinates (0…1).
            let normalized = (lines.vertical.last ?? 1) <= 1.01
            let sx = normalized ? size.width : size.width / photo.size.width
            let sy = normalized ? size.height : size.height / photo.size.height
            return CGRect(x: lines.vertical[column] * sx, y: lines.horizontal[row] * sy,
                          width: (lines.vertical[column + 1] - lines.vertical[column]) * sx,
                          height: (lines.horizontal[row + 1] - lines.horizontal[row]) * sy)
        }
        return CGRect(x: CGFloat(column) * size.width / 9, y: CGFloat(row) * size.height / 9, width: size.width / 9, height: size.height / 9)
    }
    private func visualDigit(_ index: Int) -> Int {
        if layer == "Hint", let hint = model.hintPresentation {
            if let link = hint.chain?.links.last(where: { $0.index == index }) { return link.digit }
            if let digits = hint.digits, digits[index] != 0 { return digits[index] }
            if !(hint.candidates?[index].isEmpty ?? true) { return 0 }
        }
        if layer != "Hint", layer != "Read", let overlay = model.overlayPresentation, overlay.digits[index] != 0 { return overlay.digits[index] }
        return model.displayedDigit(at: index)
    }
    private func drawsDigit(_ index: Int) -> Bool {
        guard visualDigit(index) != 0 else { return false }
        if model.scanPreview == nil || layer == "Read" { return true }
        if layer == "Solve" { return model.overlayPresentation?.roles[index] == "SOLUTION" || model.overlayPresentation?.roles[index] == "INCORRECT" }
        if layer == "Check", let role = model.overlayPresentation?.roles[index], !role.isEmpty { return role == "INCORRECT" || role == "WRITTEN" }
        if layer == "Hint", (model.hintPresentation?.digits?[index] ?? 0) != 0 || model.hintPresentation?.chain?.links.contains(where: { $0.index == index }) == true { return true }
        return model.recognition?.corrections.contains(where: { $0.cell == index }) == true
    }
    @ViewBuilder private func confidenceBar(_ index: Int, width: CGFloat) -> some View {
        if let reading = model.recognition?.readings.first(where: { $0.cell == index }),
           let confidence = reading.confidence,
           (layer == "Read" && confidence < 0.9) || model.openQuestions.contains(index) {
            let color = confidence < 0.67 ? AppStyle.incorrect : confidence < 0.9 ? AppStyle.uncertain : AppStyle.correct
            ZStack(alignment: .leading) {
                Color.black.opacity(0.4)
                color.frame(width: max(0, width - 4) * confidence)
            }.frame(width: max(0, width - 4), height: model.openQuestions.contains(index) ? 4 : 1.5).padding(2)
        }
    }
    private func cutsOutDigit(_ index: Int) -> Bool {
        guard model.scanPreview != nil else { return false }
        let role = layer == "Hint" ? model.hintPresentation?.roles?[index] : model.overlayPresentation?.roles[index]
        return layer == "Read" || role == "WRITTEN" || role == "INCORRECT"
    }
    private func overlayColor(_ role: String) -> Color {
        switch role {
        case "CORRECT": return AppStyle.correct
        case "INCORRECT": return AppStyle.incorrect
        case "SOLUTION", "HINT": return AppStyle.solution
        default: return AppStyle.written
        }
    }
    private func digitColor(_ index: Int) -> Color {
        if layer != "Hint", let role = model.overlayPresentation?.roles[index], !role.isEmpty { return overlayColor(role) }
        if layer == "Hint", let role = model.hintPresentation?.roles?[index], !role.isEmpty { return overlayColor(role) }
        return .black
    }
    private func cellColor(_ index: Int) -> Color {
        let photo = model.scanPreview != nil
        if model.selected == index && showEditor { return AppStyle.primary.opacity(0.4) }
        if layer != "Hint", layer != "Read", let overlay = model.overlayPresentation {
            switch overlay.roles[index] {
            case "SOLUTION", "HINT": return .clear
            case "WRITTEN": return AppStyle.written.opacity(0.52)
            case "INCORRECT": return AppStyle.incorrect.opacity(0.42)
            case "CORRECT": return AppStyle.correct.opacity(0.32)
            default: break
            }
            if overlay.evidence.contains(index) { return AppStyle.written.opacity(0.28) }
        }
        if layer == "Hint", let hint = model.hintPresentation {
            if hint.chain?.deadEnd.contains(index) == true { return AppStyle.incorrect.opacity(0.34) }
            if hint.chain?.links.contains(where: { $0.index == index }) == true { return .clear }
            if hint.roles?[index] == "WRITTEN" { return AppStyle.written.opacity(0.52) }
            if hint.evidence?.contains(index) == true { return Color(red: 23/255, green: 107/255, blue: 80/255).opacity(0.28) }
        }
        if layer == "Read" {
            if model.givens[index] != 0 { return AppStyle.accent.opacity(0.52) }
            if model.progress[index] != 0 { return AppStyle.written.opacity(0.52) }
            if model.recognition?.readings.first(where: { $0.cell == index })?.ink == "MARK" { return .gray.opacity(0.3) }
        }
        return .clear
    }
    private func cellLabel(_ index: Int) -> String {
        let digit = model.digit(at: index)
        let cell = digit == 0 ? tr("empty") : tr(model.givens[index] == 0 ? "\(digit), entry" : "\(digit), printed clue")
        var label = tr("Row \(index / 9 + 1), column \(index % 9 + 1)") + ", " + cell
        if layer == "Hint", let hint = model.hintPresentation {
            if hint.target == index { label += tr(", destination of this hint") }
            if hint.evidence?.contains(index) == true { label += tr(", supporting evidence") }
            if let candidates = hint.candidates?[index], !candidates.isEmpty { label += tr(", candidates \(candidates.map(String.init).joined(separator: ", "))") }
            if let removed = hint.removed?[index], !removed.isEmpty { label += tr(", ruled out \(removed.map(String.init).joined(separator: ", "))") }
            if let link = hint.chain?.links.first(where: { $0.index == index }) { label += tr(", hypothetical \(link.digit), not an entered answer") }
            if hint.chain?.deadEnd.contains(index) == true { label += tr(", contradiction") }
            if hint.roles?[index] == "HINT" { label += tr(", hint \(hint.digits?[index] ?? 0)") }
        } else if let overlay = model.overlayPresentation {
            if overlay.evidence.contains(index) { label += tr(", supporting evidence") }
            switch overlay.roles[index] {
            case "SOLUTION": label += tr(", solution \(overlay.digits[index])")
            case "CORRECT": label += tr(", checked correct")
            case "INCORRECT": label += tr(", does not match the solution")
            default: break
            }
        }
        return label
    }
}

private struct InlineStudyBoard: View {
    @ObservedObject var study: StudyModel
    @ObservedObject var puzzle: PuzzleModel
    let normal: AnyView
    var body: some View {
        if let step = study.current {
            LessonBoard(step: step, after: study.after, selected: Binding(get: { study.selected }, set: { study.selectCell($0) }), chainDepth: study.chainDepth,
                hideAnswer: study.practice && !study.showAnswer, photo: puzzle.scanPreview, lines: puzzle.recognition?.gridLines)
        } else { normal }
    }
}
