import Foundation
import Combine
import UIKit
import SudokuShared
import PuzzlePersistence

extension Evaluation {
    static func run(givens: String, progress: String, hint: Bool, explain: Bool) -> Evaluation {
        let result = PuzzleEngine().evaluate(givens: givens, progress: progress, includeHint: hint, explainHints: explain)
        func indices(_ text: String) -> Set<Int> {
            Set(text.split(separator: ",").compactMap { Int($0) })
        }
        return Evaluation(
            status: result.status, message: result.message,
            incorrect: indices(result.incorrectCells), hintIndex: Int(result.hintIndex),
            hintDigit: Int(result.hintDigit), technique: result.hintTechnique,
            explanation: result.hintExplanation, supporting: indices(result.supportingCells)
        )
    }
}

@MainActor
final class PuzzleModel: ObservableObject {
    @Published private(set) var history: [SavedPuzzle] = []
    @Published private(set) var currentID: UUID?
    @Published var storageNotice: String?
    private let store: PuzzleStore
    private var currentTitle = "Manual puzzle"
    private var createdAt = Date()
    private var updatedAt = Date()
    private var hasPhoto = false
    private var pendingPhotoData: Data?
    private var saveFailed = false
    @Published private(set) var refusedScans: [RefusedScan] = []
    private lazy var refusedStore = RefusedScanStore(directory: Self.defaultStore().directory.appendingPathComponent("RefusedScans"))
    @Published private(set) var recognition: RecognitionEvidence?
    private var evidenceDirty = false
    @Published private(set) var study: StudyBookmark?
    @Published private(set) var solutionDigits: [Int]?
    var cluesText: String { Self.encode(givens) }
    var answersText: String { Self.encode(progress) }
    var enteredCells: String {
        let cells = scanPreview == nil ? Set((0..<81).filter { progress[$0] != 0 }) :
            Set(recognition?.corrections.filter { $0.newSource == "GUESS" && progress[$0.cell] != 0 }.map(\.cell) ?? [])
        return cells.sorted().map(String.init).joined(separator: ",")
    }

    init(store: PuzzleStore? = nil) {
        self.store = store ?? Self.defaultStore()
        let loaded = self.store.load()
        history = loaded.puzzles
        if let current = loaded.active { restore(current) }
        let notices = loaded.warnings + (storageNotice.map { [$0] } ?? [])
        storageNotice = notices.isEmpty ? nil : notices.joined(separator: " ")
    }

    @Published var scanPreview: UIImage?
    @Published var scanUncertain: Set<Int> = []
    @Published var reviewingScan = false
    @Published var editAsAnswer = false
    @Published var givens = Array(repeating: 0, count: 81)
    @Published var progress = Array(repeating: 0, count: 81)
    @Published var selected = 0
    @Published var editingClues = true
    @Published var busy = false
    @Published var message = "Enter the printed clues, then choose Start puzzle."
    @Published var framingNote: String?
    @Published var readerComplaint: String?
    @Published var result: Evaluation?
    @Published var explanationVisible = false
    private let hintWorker = HintPresentationWorker()
    @Published private(set) var hintBookmark: HintBookmark?
    @Published private(set) var hintPresentation: HintPresentation?
    @Published var overlayMode = "Read"
    @Published private(set) var overlayPresentation: PuzzlePresentation?
    private var answerShown = 0
    func refreshOverlay(pressed: String = "") async {
        guard !busy else { return }
        busy = true
        let clues = cluesText, answers = answersText, showing = overlayMode, answer = answerShown, entered = enteredCells, uncertain = !scanUncertain.isEmpty
        let text = await Task.detached(priority: .userInitiated) {
            PuzzlePresentationEngine().snapshot(givens: clues, progress: answers, showing: showing, pressed: pressed,
                answerShown: Int32(answer), uncertain: uncertain, enteredCells: entered)
        }.value
        if let presentation = try? JSONDecoder().decode(PuzzlePresentation.self, from: Data(text.utf8)) {
            overlayPresentation = presentation; overlayMode = presentation.mode; answerShown = presentation.answerShown
            solutionDigits = presentation.mode == "Solve" && presentation.roles.contains("SOLUTION") ?
                (0..<81).map { presentation.digits[$0] == 0 ? givens[$0] : presentation.digits[$0] } : nil
        }
        busy = false; persist(touch: false)
    }
    var hintDepth: Int { hintBookmark?.depth ?? 0 }

    func requestHint(showing: Bool) async {
        let depth = Int(HintPresentationEngine().nextDepth(showingHint: showing, depth: Int32(hintDepth), explain: AppSettings.shared.explainHints))
        if depth < 0 { closeHint(); return }
        if !showing || result == nil { await evaluate() }
        guard result?.status == "ready", incorrect.isEmpty else { closeHint(); return }
        hintBookmark = HintBookmark(depth: depth)
        await refreshHint()
    }
    func chooseHintDepth(_ depth: Int) async {
        hintBookmark = HintBookmark(depth: depth)
        await refreshHint()
    }
    func visitHint(_ step: Int) async {
        guard var bookmark = hintBookmark else { return }
        bookmark.parents.append(bookmark.frame); bookmark.frame = HintPosition(step: step)
        hintBookmark = bookmark; await refreshHint()
    }
    func backHint() async {
        guard var bookmark = hintBookmark, let parent = bookmark.parents.popLast() else { return }
        bookmark.frame = parent; hintBookmark = bookmark; await refreshHint()
    }
    func changeHintFrame(before: Bool? = nil, consequence: Int? = nil) async {
        guard var bookmark = hintBookmark else { return }
        if let before { bookmark.frame.before = before }
        if let consequence { bookmark.frame.consequence = consequence }
        hintBookmark = bookmark; await refreshHint()
    }
    func closeHint() { hintBookmark = nil; hintPresentation = nil; persist(touch: false) }
    func refreshHint() async {
        guard let bookmark = hintBookmark, !busy else { return }
        busy = true
        let clues = cluesText, answers = answersText, entered = enteredCells, explain = AppSettings.shared.explainHints
        let text = await hintWorker.snapshot(givens: clues, progress: answers, bookmark: bookmark, explain: explain, entered: entered)
        hintPresentation = try? JSONDecoder().decode(HintPresentation.self, from: Data(text.utf8))
        if hintPresentation?.available != true { hintBookmark = nil }
        busy = false; persist(touch: false)
    }

    #if DEBUG
    /// Isolated UI fixture for report/review workflows; ordinary scan tests use actual readings.
    func useUncertainPhotoFixture() {
        guard ProcessInfo.processInfo.arguments.contains("--history-test-session"),let recognition,
              let data=try? JSONEncoder().encode(recognition),
              var object=(try? JSONSerialization.jsonObject(with:data)) as? [String:Any],
              var readings=object["readings"] as? [[String:Any]],readings.count==81 else { return }
        readings[2]["roleUncertain"]=true;object["readings"]=readings;object["uncertainCells"]=[2]
        guard let changed=try? JSONSerialization.data(withJSONObject:object),let evidence=try? JSONDecoder().decode(RecognitionEvidence.self,from:changed) else { return }
        self.recognition=evidence;scanUncertain=[2];reviewingScan=true;editingClues=false;overlayMode="Read";evidenceDirty=true
        readerComplaint=nil;persist(touch:false)
    }
    #endif
    var readingReview: ReadingReviewPresentation { PuzzlePresentationEngine().readingReview(openQuestions:Int32(openQuestions.count),originalQuestions:Int32(recognition?.uncertainCells.count ?? 0),expanded:reviewingScan,showing:overlayMode) }
    var hasReadingReview: Bool { readingReview.available }
    var openQuestions: Set<Int> {
        guard overlayPresentation?.unique == true || result?.status == "ready" || result?.status == "complete" else { return scanUncertain }
        return scanUncertain.filter { index in
            guard let reading = recognition?.readings.first(where: { $0.cell == index }) else { return true }
            return reading.roleUncertain || (reading.confidence ?? 1) < 0.67
        }
    }
    var readingNote: String {
        let unique = overlayPresentation?.unique == true || result?.status == "ready" || result?.status == "complete"
        return [framingNote, unique ? nil : readerComplaint].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " ")
    }
    var readingHeadline: String { PuzzlePresentationEngine().readingHeadline(count: Int32(openQuestions.count)) }
    func expandReadingReview() {
        guard hasReadingReview,!busy else { return }
        reviewingScan=true;editingClues=false;solutionDigits=nil;overlayMode="Read";persist(touch:false)
    }
    func collapseReadingReview() {
        reviewingScan = false; editingClues = false; persist(touch: false)
    }
    var incorrect: Set<Int> { result?.incorrect ?? [] }

    func historyThumbnail(_ entry: SavedPuzzle) -> UIImage? {
        guard entry.hasPhoto, let data = try? store.photo(entry.id) else { return nil }
        return UIImage(data: data)
    }

    func digit(at index: Int) -> Int { givens[index] == 0 ? progress[index] : givens[index] }

    func enter(_ digit: Int) {
        guard (0...9).contains(digit), !busy else { return }
        let oldDigit = self.digit(at: selected)
        let oldSource = source(at: selected)
        if givens[selected] != 0 || (editingClues && scanPreview == nil) {
            givens[selected] = digit; progress[selected] = 0
        } else { progress[selected] = digit }
        study = nil; solutionDigits = nil; hintBookmark = nil; hintPresentation = nil
        recordCorrection(oldDigit: oldDigit, oldSource: oldSource)
        scanUncertain.remove(selected)
        result = nil
        overlayPresentation = nil
        explanationVisible = false
        message = editingClues ? "Enter the printed clues, then choose Start puzzle." : "Choose Check answers or ask for a hint."
        persist()
    }

    func evaluate(hint: Bool = false) async {
        guard !busy else { return }
        busy = true
        let clues = Self.encode(givens)
        let answers = Self.encode(progress)
        let explain = AppSettings.shared.explainHints
        // Only immutable Swift values cross the task boundary. Kotlin objects are
        // created, used and discarded on the worker so solving cannot block taps.
        let evaluation = await Task.detached(priority: .userInitiated) {
            Evaluation.run(givens: clues, progress: answers, hint: hint, explain: explain)
        }.value
        result = evaluation
        message = evaluation.message
        explanationVisible = false
        if evaluation.status == "ready" || evaluation.status == "complete" {
            editingClues = false
            if !hasPhoto { reviewingScan = false }
        }
        busy = false
        persist()
    }

    @discardableResult
    func reset() -> Bool {
        guard !busy, persist(touch: false) else { return false }
        do { try store.setActive(nil) }
        catch { storageNotice = "The new puzzle could not be saved. Your current puzzle is still open."; return false }
        clearCurrent()
        return true
    }

    private func clearCurrent() {
        overlayMode = "Read"; overlayPresentation = nil; answerShown = 0
        currentID = nil
        currentTitle = "Manual puzzle"
        createdAt = Date(); updatedAt = createdAt
        hasPhoto = false
        framingNote = nil; readerComplaint = nil
        recognition = nil; evidenceDirty = false; study = nil; solutionDigits = nil; hintBookmark = nil; hintPresentation = nil
        pendingPhotoData = nil
        scanPreview = nil
        scanUncertain = []
        reviewingScan = false
        editAsAnswer = false
        givens = Array(repeating: 0, count: 81)
        progress = givens
        selected = 0
        editingClues = true
        result = nil
        overlayPresentation = nil
        explanationVisible = false
        message = "Enter the printed clues, then choose Start puzzle."
    }

    #if DEBUG
    func loadFixture(_ clues: String) {
        guard clues.count == 81, clues.allSatisfy({ $0 == "." || $0.wholeNumberValue.map { (1...9).contains($0) } == true }), reset() else { return }
        currentTitle = "Example puzzle"; givens = clues.map { $0.wholeNumberValue ?? 0 }
        editingClues = false; overlayMode = "None"; persist()
    }
    #endif

    func loadExample() {
        guard reset() else { return }
        currentTitle = "Example puzzle"
        givens = Self.example.map { $0.wholeNumberValue ?? 0 }
        message = "Example loaded. Choose Start puzzle to check the clues."
        persist()
    }

    func select(_ index: Int) {
        guard !busy, (0..<81).contains(index) else { return }
        selected = index
        if hasPhoto { reviewingScan=false }
        editAsAnswer = givens[index] == 0
        persist(touch: false)
    }

    func setRole(asAnswer: Bool) {
        guard !busy else { return }
        let value = digit(at: selected)
        let oldSource = source(at: selected)
        editAsAnswer = asAnswer
        givens[selected] = asAnswer ? 0 : value
        progress[selected] = asAnswer ? value : 0
        study = nil; solutionDigits = nil; hintBookmark = nil; hintPresentation = nil
        recordCorrection(oldDigit: value, oldSource: oldSource)
        scanUncertain.remove(selected)
        result = nil
        overlayPresentation = nil
        explanationVisible = false
        persist()
    }

    func acceptReading() async {
        scanUncertain = []; reviewingScan = false; editingClues = false
        await evaluate()
        reviewingScan = false; editingClues = false
        await refreshOverlay()
        persist()
    }
    func acceptCellReading() {
        scanUncertain.remove(selected); persist()
    }
    func readPhoto(_ data: Data) async {
        guard !busy, persist(touch: false) else { return }
        busy = true
        message = "Reading the photo…"
        do {
            let scan = try await Task.detached(priority: .userInitiated) {
                try PhotoScanner.read(data)
            }.value
            let initialOverlay = await Task.detached {
                PuzzlePresentationEngine().snapshot(givens: scan.givens.map(String.init).joined(),
                    progress: scan.progress.map(String.init).joined(), showing: "None", pressed: "",
                    answerShown: 0, uncertain: !scan.uncertain.isEmpty, enteredCells: "")
            }.value
            currentID = nil
            currentTitle = "Scanned puzzle"
            createdAt = Date(); updatedAt = createdAt
            hasPhoto = true
            recognition = scan.evidence; evidenceDirty = true; study = nil; solutionDigits = nil; hintBookmark = nil; hintPresentation = nil
            pendingPhotoData = scan.photoData
            givens = scan.givens
            progress = scan.progress
            scanPreview = scan.image
            framingNote = scan.framingNote; readerComplaint = scan.readerComplaint
            scanUncertain = scan.uncertain
            overlayPresentation = try? JSONDecoder().decode(PuzzlePresentation.self, from: Data(initialOverlay.utf8))
            reviewingScan = hasReadingReview
            editingClues = false
            overlayMode = openQuestions.isEmpty ? "None" : "Read"
            selected = openQuestions.min() ?? 0
            editAsAnswer = givens[selected] == 0 && progress[selected] != 0
            result = nil
            explanationVisible = false
            message = scan.message + " Review the photo: bold digits are printed clues; purple digits are handwritten answers."
            persist()
        } catch {
            message = error.localizedDescription
            let failure = message
            let store = refusedStore
            _ = await Task.detached { try? store.keep(data, reason: failure) }.value
            reloadRefusedScans()
        }
        busy = false
        offerAutomaticIfNeeded()
    }

    func editClues() {
        guard !busy else { return }
        solutionDigits = nil
        reviewingScan = hasPhoto && hasReadingReview
        editingClues = !hasPhoto
        editAsAnswer = givens[selected] == 0 && progress[selected] != 0
        result = nil
        overlayPresentation = nil
        explanationVisible = false
        message = "Edit the printed clues, then choose Start puzzle."
        persist()
    }

    @discardableResult
    func persist(touch: Bool = true) -> Bool {
        // Busy/loading messages are transient; the previous stable state is already saved.
        if busy && pendingPhotoData == nil { return true }
        let hasContent = givens.contains { $0 != 0 } || progress.contains { $0 != 0 } || hasPhoto
        if currentID == nil && !hasContent { return true }
        let id = currentID ?? UUID()
        currentID = id
        if touch { updatedAt = Date() }
        let record = SavedPuzzle(id: id, title: currentTitle, createdAt: createdAt, updatedAt: updatedAt,
                                 givens: givens, progress: progress, selected: selected, editingClues: editingClues,
                                 reviewingScan: reviewingScan, editAsAnswer: editAsAnswer, uncertain: scanUncertain,
                                 evaluation: result, explanationVisible: explanationVisible, message: message, framingNote: framingNote, readerComplaint: readerComplaint, hasPhoto: hasPhoto, recognition: recognition, study: study, hint: hintBookmark, overlayMode: overlayMode, answerShown: answerShown)
        do {
            try store.save(record, photo: pendingPhotoData)
            pendingPhotoData = nil
            if saveFailed { storageNotice = nil; saveFailed = false }
            history.removeAll { $0.id == record.id }
            history.append(record)
            history.sort { $0.updatedAt > $1.updatedAt }
            offerAutomaticIfNeeded()
            return true
        } catch {
            saveFailed = true
            storageNotice = "Changes could not be saved on this device. Keep this puzzle open and try again."
            return false
        }
    }

    @discardableResult
    func open(_ record: SavedPuzzle) -> Bool {
        guard !busy, persist(touch: false) else { return false }
        do {
            let saved = try store.read(record.id)
            try store.setActive(saved.id)
            restore(saved)
            if let snapshot = reportSnapshot(automatic: true) { FeedbackModel.shared.offerAutomatic(snapshot) }
            return true
        } catch {
            storageNotice = "That saved puzzle could not be opened. Your current puzzle is still open."
            return false
        }
    }

    func delete(_ record: SavedPuzzle) {
        guard !busy else { return }
        do {
            try store.delete(record.id)
            FeedbackModel.shared.cancelQueued(record.id)
            history.removeAll { $0.id == record.id }
            if currentID == record.id { clearCurrent() }
        } catch {
            storageNotice = "That puzzle could not be fully deleted. Please try again."
            history = store.load().puzzles
        }
    }

    private func restore(_ record: SavedPuzzle) {
        currentID = record.id
        currentTitle = record.title
        createdAt = record.createdAt; updatedAt = record.updatedAt
        givens = record.givens; progress = record.progress
        selected = record.selected; editingClues = record.editingClues && !record.hasPhoto
        reviewingScan = record.reviewingScan; editAsAnswer = record.editAsAnswer
        scanUncertain = record.uncertain; result = record.evaluation
        framingNote = record.framingNote; readerComplaint = record.readerComplaint
        explanationVisible = record.explanationVisible; message = record.message
        hasPhoto = record.hasPhoto
        recognition = record.recognition; evidenceDirty = false
        study = record.study; solutionDigits = nil; hintBookmark = record.hint ?? ((record.overlayMode == nil && (record.evaluation?.hintIndex ?? -1) >= 0) ? HintBookmark(depth: record.explanationVisible ? 3 : 0) : nil); hintPresentation = nil
        overlayMode = record.overlayMode ?? (hintBookmark != nil ? "Hint" : record.editingClues ? "Read" : "Check")
        answerShown = record.answerShown ?? 0; overlayPresentation = nil
        pendingPhotoData = nil
        scanPreview = nil
        if record.hasPhoto {
            if let data = try? store.photo(record.id), let image = UIImage(data: data) { scanPreview = image }
            else { storageNotice = "The puzzle was restored, but its saved photo could not be opened." }
        }
    }

    func saveStudy(_ bookmark: StudyBookmark) {
        guard !busy, bookmark.isValid, bookmark.givens == cluesText, bookmark.progress == answersText else { return }
        study = bookmark
        persist(touch: false)
    }

    func applyStudyAnswer(index: Int, digit: Int, clues: String, answers: String) {
        guard !editingClues, !busy, clues == cluesText, answers == answersText,
              (0..<81).contains(index), givens[index] == 0, (1...9).contains(digit) else { return }
        selected = index; enter(digit)
    }

    func displayedDigit(at index: Int) -> Int { solutionDigits?[index] ?? digit(at: index) }

    private func source(at index: Int) -> String {
        givens[index] != 0 ? "GIVEN" : progress[index] != 0 ? "GUESS" : "EMPTY"
    }

    private func recordCorrection(oldDigit: Int, oldSource: String) {
        guard hasPhoto, recognition != nil else { return }
        let newDigit = digit(at: selected), newSource = source(at: selected)
        guard oldDigit != newDigit || oldSource != newSource else { return }
        recognition?.corrections.append(ReadingCorrection(cell: selected, oldDigit: oldDigit == 0 ? nil : oldDigit,
                                                        newDigit: newDigit == 0 ? nil : newDigit,
                                                        oldSource: oldSource, newSource: newSource))
        evidenceDirty = true
    }

    private func offerAutomaticIfNeeded() {
        guard evidenceDirty, let snapshot = reportSnapshot(automatic: true) else { return }
        evidenceDirty = false
        FeedbackModel.shared.offerAutomatic(snapshot)
    }

    func reportSnapshot(automatic: Bool = false) -> ReportSnapshot? {
        guard !busy, let id = currentID, let evidence = recognition, evidence.isValid,
              let photo = pendingPhotoData ?? (try? store.photo(id)) else { return nil }
        let cells = (0..<81).map { digit(at: $0) == 0 ? "." : String(digit(at: $0)) }
        let grid = stride(from: 0, to: 81, by: 9).map { cells[$0..<$0 + 9].joined() }.joined(separator: "\n")
        return ReportSnapshot(puzzleID: id, evidence: evidence, currentGrid: automatic ? evidence.correctedGrid : grid, jpeg: photo,
                              appVersion: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown")
    }

    var readingDescription: String? {
        let digit = self.digit(at: selected)
        let corrected = recognition?.corrections.contains { $0.cell == selected } == true
        if corrected {
            if digit == 0 { return "You cleared this square." }
            return givens[selected] != 0 ? "You set this to a printed \(digit)." : "You set this to a handwritten \(digit)."
        }
        guard let reading = recognition?.readings.first(where: { $0.cell == selected }) else {
            return digit == 0 ? "Empty." : givens[selected] != 0 ? "A printed \(digit)." : "A handwritten \(digit)."
        }
        return PuzzlePresentationEngine().describeReading(ink: reading.ink ?? (givens[selected] != 0 ? "PRINTED" : progress[selected] != 0 ? "ANSWER" : "NONE"), digit: Int32(reading.digit ?? 0), confidence: reading.confidence ?? 1,
            runnerUp: Int32(reading.runnerUp ?? 0), runnerUpConfidence: reading.runnerUpConfidence ?? 0, roleUncertain: reading.roleUncertain)
    }
    var secondReading: String? {
        guard recognition?.corrections.contains(where: { $0.cell == selected }) != true,
              let reading = recognition?.readings.first(where: { $0.cell == selected }) else { return nil }
        let text = PuzzlePresentationEngine().secondGuess(digit: Int32(reading.digit ?? 0), runnerUp: Int32(reading.runnerUp ?? 0), confidence: reading.runnerUpConfidence ?? 0)
        return text.isEmpty ? nil : text
    }

    func reloadRefusedScans() { refusedScans = refusedStore.list() }
    func refusedPhoto(_ scan: RefusedScan) -> Data? { try? refusedStore.photo(scan) }
    func deleteRefusedScan(_ scan: RefusedScan) {
        do { try refusedStore.discard(scan); reloadRefusedScans() }
        catch { storageNotice = "The photo could not be deleted. Please try again." }
    }

    var diagnostics: String {
        guard let recognition else { return "Refused scans retained locally: \(refusedStore.list().count). No photos included." }
        return "Recognition summary (no photo or digit grids):\n" +
            "Cells: \(recognition.readings.count); uncertain: \(recognition.uncertainCells.count); corrections: \(recognition.corrections.count)."
    }

    private static func defaultStore() -> PuzzleStore {
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        var root = support.appendingPathComponent("SudokuBuddyHistory", isDirectory: true)
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if let flag = args.firstIndex(of: "--history-test-session"), args.indices.contains(flag + 1),
           let id = UUID(uuidString: args[flag + 1]) {
            root = support.appendingPathComponent("UITestHistory", isDirectory: true).appendingPathComponent(id.uuidString, isDirectory: true)
        }
        #endif
        return PuzzleStore(directory: root)
    }

    private static func encode(_ cells: [Int]) -> String {
        cells.map { $0 == 0 ? "." : String($0) }.joined()
    }

    private static let example = "53..7....6..195....98....6.8...6...34..8.3..17...2...6.6....28....419..5....8..79"
}

struct HintPresentation: Decodable {
    struct Question: Decodable, Identifiable { let step: Int; let label: String; var id: Int { step } }
    let available: Bool
    let stageNames: [String]?
    let stageCaption: String?
    let keyCaption: String?
    let answerGuidance: String?
    let visual: Bool?
    let target: Int?
    let digit: Int?
    let technique: String?
    let caption: String?
    let causalArrows: [CausalArrow]?
    let eliminationTargets: [Int]?
    let targets: [Int]?
    let elimination: Bool?
    let lessonTechnique: String?
    let finalStep: Int?
    let chainLength: Int?
    let branches: [Question]?
    let dependencies: [Question]?
    let digits: [Int]?
    let roles: [String]?
    let evidence: [Int]?
    let focus: Int?
    let candidates: [[Int]]?
    let removed: [[Int]]?
    let chain: ChainEvidence?
}

struct PuzzlePresentation: Decodable {
    let mode: String
    let answerShown: Int
    let answerCount: Int
    let status: String
    let canHint: Bool
    let unique: Bool
    let guidance: String
    let digits: [Int]
    let roles: [String]
    let evidence: [Int]
    let focus: Int?
    let legend: [String]
}

private actor HintPresentationWorker {
    private let engine = HintPresentationEngine()
    func snapshot(givens: String, progress: String, bookmark: HintBookmark, explain: Bool, entered: String) -> String {
        engine.snapshot(givens: givens, progress: progress, depth: Int32(bookmark.depth), step: Int32(bookmark.frame.step),
            consequence: Int32(bookmark.frame.consequence), before: bookmark.frame.before, explain: explain, enteredCells: entered)
    }
}
