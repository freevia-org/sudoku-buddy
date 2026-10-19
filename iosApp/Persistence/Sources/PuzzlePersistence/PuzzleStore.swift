import Foundation

public struct Evaluation: Codable, Sendable, Equatable {
    public let status: String
    public let message: String
    public let incorrect: Set<Int>
    public let hintIndex: Int
    public let hintDigit: Int
    public let technique: String
    public let explanation: String
    public let supporting: Set<Int>

    public init(status: String, message: String, incorrect: Set<Int>, hintIndex: Int,
                hintDigit: Int, technique: String, explanation: String, supporting: Set<Int>) {
        self.status = status; self.message = message; self.incorrect = incorrect
        self.hintIndex = hintIndex; self.hintDigit = hintDigit; self.technique = technique
        self.explanation = explanation; self.supporting = supporting
    }

    var isValid: Bool {
        ["invalid", "impossible", "ambiguous", "complete", "ready"].contains(status) &&
        incorrect.allSatisfy { (0..<81).contains($0) } && supporting.allSatisfy { (0..<81).contains($0) } &&
        ((hintIndex == -1 && hintDigit == 0) || ((0..<81).contains(hintIndex) && (1...9).contains(hintDigit)))
    }
}

public struct HintPosition: Codable, Sendable, Equatable {
    public var step: Int
    public var consequence: Int
    public var before: Bool
    public init(step: Int = -1, consequence: Int = 0, before: Bool = true) {
        self.step = step; self.consequence = consequence; self.before = before
    }
}
public struct HintBookmark: Codable, Sendable, Equatable {
    public var depth: Int
    public var frame: HintPosition
    public var parents: [HintPosition]
    public init(depth: Int = 0, frame: HintPosition = HintPosition(), parents: [HintPosition] = []) {
        self.depth = depth; self.frame = frame; self.parents = parents
    }
    public var isValid: Bool { (0...3).contains(depth) && parents.count < 100 &&
        ([frame] + parents).allSatisfy { $0.step >= -1 && $0.step < 10000 && (0...1000).contains($0.consequence) } }
}

public struct SavedPuzzle: Codable, Identifiable, Sendable, Equatable {
    public var version = 1
    public var id: UUID
    public var title: String
    public var createdAt: Date
    public var updatedAt: Date
    public var givens: [Int]
    public var progress: [Int]
    public var selected: Int
    public var editingClues: Bool
    public var reviewingScan: Bool
    public var editAsAnswer: Bool
    public var uncertain: Set<Int>
    public var evaluation: Evaluation?
    public var explanationVisible: Bool
    public var message: String
    public var framingNote: String?
    public var readerComplaint: String?
    public var hasPhoto: Bool
    public var recognition: RecognitionEvidence?
    public var study: StudyBookmark?
    public var hint: HintBookmark?
    public var overlayMode: String?
    public var answerShown: Int?

    public init(id: UUID = UUID(), title: String, createdAt: Date = Date(), updatedAt: Date = Date(),
                givens: [Int], progress: [Int], selected: Int = 0, editingClues: Bool = true,
                reviewingScan: Bool = false, editAsAnswer: Bool = false, uncertain: Set<Int> = [],
                evaluation: Evaluation? = nil, explanationVisible: Bool = false,
                message: String = "", framingNote: String? = nil, readerComplaint: String? = nil, hasPhoto: Bool = false, recognition: RecognitionEvidence? = nil, study: StudyBookmark? = nil, hint: HintBookmark? = nil, overlayMode: String? = nil, answerShown: Int? = nil) {
        self.id = id; self.title = title; self.createdAt = createdAt; self.updatedAt = updatedAt
        self.givens = givens; self.progress = progress; self.selected = selected
        self.editingClues = editingClues; self.reviewingScan = reviewingScan; self.editAsAnswer = editAsAnswer
        self.uncertain = uncertain; self.evaluation = evaluation; self.explanationVisible = explanationVisible
        self.framingNote = framingNote; self.readerComplaint = readerComplaint
        self.message = message; self.hasPhoto = hasPhoto; self.recognition = recognition; self.study = study; self.hint = hint; self.overlayMode = overlayMode; self.answerShown = answerShown
    }

    enum CodingKeys: String, CodingKey {
        case version, id, title, createdAt, updatedAt, givens, progress, selected, editingClues, reviewingScan,
             editAsAnswer, uncertain, evaluation, explanationVisible, message, framingNote, readerComplaint, hasPhoto, recognition, study, hint, overlayMode, answerShown
    }
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        version = try c.decode(Int.self, forKey: .version)
        id = try c.decode(UUID.self, forKey: .id); title = try c.decode(String.self, forKey: .title)
        createdAt = try c.decode(Date.self, forKey: .createdAt); updatedAt = try c.decode(Date.self, forKey: .updatedAt)
        givens = try c.decode([Int].self, forKey: .givens); progress = try c.decode([Int].self, forKey: .progress)
        selected = try c.decode(Int.self, forKey: .selected); editingClues = try c.decode(Bool.self, forKey: .editingClues)
        reviewingScan = try c.decode(Bool.self, forKey: .reviewingScan); editAsAnswer = try c.decode(Bool.self, forKey: .editAsAnswer)
        uncertain = try c.decode(Set<Int>.self, forKey: .uncertain); evaluation = try c.decodeIfPresent(Evaluation.self, forKey: .evaluation)
        explanationVisible = try c.decode(Bool.self, forKey: .explanationVisible)
        message = try c.decode(String.self, forKey: .message); hasPhoto = try c.decode(Bool.self, forKey: .hasPhoto)
        framingNote = try? c.decode(String.self, forKey: .framingNote)
        readerComplaint = try? c.decode(String.self, forKey: .readerComplaint)
        // Missing/invalid optional recognition data must never hide a recoverable puzzle.
        recognition = try? c.decode(RecognitionEvidence.self, forKey: .recognition)
        if recognition?.isValid == false { recognition = nil }
        study = try? c.decode(StudyBookmark.self, forKey: .study)
        if study?.isValid == false { study = nil }
        hint = try? c.decode(HintBookmark.self, forKey: .hint)
        if hint?.isValid == false { hint = nil }
        overlayMode = try? c.decode(String.self, forKey: .overlayMode)
        if let mode = overlayMode, !["Read", "Check", "Hint", "Solve", "None"].contains(mode) { overlayMode = nil }
        answerShown = try? c.decode(Int.self, forKey: .answerShown)
        if let answer = answerShown, !(0...100000).contains(answer) { answerShown = nil }
    }

    public var clueCount: Int { givens.filter { $0 != 0 }.count }
    public var answerCount: Int { progress.filter { $0 != 0 }.count }
    public var stateLabel: String {
        if reviewingScan { return "Reviewing scan" }
        if editingClues { return "Entering clues" }
        return evaluation?.status == "complete" ? "Complete" : "In progress"
    }

    public var isValid: Bool {
        version == 1 && !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        givens.count == 81 && progress.count == 81 && givens.allSatisfy { (0...9).contains($0) } &&
        progress.allSatisfy { (0...9).contains($0) } && zip(givens, progress).allSatisfy { $0.0 == 0 || $0.1 == 0 } &&
        (0..<81).contains(selected) && uncertain.allSatisfy { (0..<81).contains($0) } &&
        (!reviewingScan || hasPhoto) && (evaluation?.isValid ?? true) && (recognition?.isValid ?? true) && (study?.isValid ?? true) && (hint?.isValid ?? true)
    }
}

public struct LoadedHistory {
    public let puzzles: [SavedPuzzle]
    public let active: SavedPuzzle?
    public let warnings: [String]
}

public enum PuzzleStoreError: LocalizedError {
    case invalidRecord
    public var errorDescription: String? { "The saved puzzle could not be read safely." }
}

/// Accessed serially by the app's main actor. Records are independent and replaced atomically.
/// The active pointer is written last, so a partial save never points at an incomplete record.
public final class PuzzleStore {
    public let directory: URL
    private let files: FileManager
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()
    private struct Current: Codable { var version = 1; let id: UUID? }

    public init(directory: URL, files: FileManager = .default) {
        self.directory = directory
        self.files = files
    }

    public func load() -> LoadedHistory {
        guard files.fileExists(atPath: directory.path) else {
            return LoadedHistory(puzzles: [], active: nil, warnings: [])
        }
        var warnings: [String] = []
        let urls: [URL]
        do { urls = try files.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil) }
        catch { return LoadedHistory(puzzles: [], active: nil, warnings: ["Saved puzzles could not be opened. Your files have been kept."]) }
        var puzzles: [SavedPuzzle] = []
        for url in urls where url.pathExtension == "json" && UUID(uuidString: url.deletingPathExtension().lastPathComponent) != nil {
            do {
                let record = try decoder.decode(SavedPuzzle.self, from: Data(contentsOf: url))
                guard record.isValid, record.id.uuidString == url.deletingPathExtension().lastPathComponent else {
                    throw PuzzleStoreError.invalidRecord
                }
                puzzles.append(record)
            } catch {
                if warnings.isEmpty { warnings.append("Some saved puzzles could not be opened. Their files have been kept; other puzzles are available in History.") }
            }
        }
        puzzles.sort { $0.updatedAt == $1.updatedAt ? $0.id.uuidString < $1.id.uuidString : $0.updatedAt > $1.updatedAt }
        var active: SavedPuzzle?
        let pointer = directory.appendingPathComponent("current.json")
        if files.fileExists(atPath: pointer.path) {
            do {
                let current = try decoder.decode(Current.self, from: Data(contentsOf: pointer))
                guard current.version == 1 else { throw PuzzleStoreError.invalidRecord }
                if let id = current.id {
                    guard let record = puzzles.first(where: { $0.id == id }) else { throw PuzzleStoreError.invalidRecord }
                    active = record
                }
                // An explicit nil means the user started a new, empty puzzle.
            } catch {
                active = puzzles.first
                warnings.append("The last session could not be restored. The most recent readable puzzle was opened instead.")
            }
        } else {
            // Recover a record committed just before an interrupted first pointer write.
            active = puzzles.first
        }
        return LoadedHistory(puzzles: puzzles, active: active, warnings: warnings)
    }

    public func read(_ id: UUID) throws -> SavedPuzzle {
        let record = try decoder.decode(SavedPuzzle.self, from: Data(contentsOf: recordURL(id)))
        guard record.id == id, record.isValid else { throw PuzzleStoreError.invalidRecord }
        return record
    }

    public func save(_ record: SavedPuzzle, photo: Data? = nil) throws {
        guard record.isValid else { throw PuzzleStoreError.invalidRecord }
        try prepare()
        if let photo { try photo.write(to: photoURL(record.id), options: .atomic) }
        try encoder.encode(record).write(to: recordURL(record.id), options: .atomic)
        try setActive(record.id)
    }

    public func setActive(_ id: UUID?) throws {
        try prepare()
        try encoder.encode(Current(id: id)).write(to: directory.appendingPathComponent("current.json"), options: .atomic)
    }

    public func photo(_ id: UUID) throws -> Data { try Data(contentsOf: photoURL(id)) }

    public func delete(_ id: UUID) throws {
        let wasActive = load().active?.id == id
        // Commit the blank active session before removing its record. A restart during
        // deletion can leave a recoverable history entry, but cannot reopen a deleted one.
        if wasActive { try setActive(nil) }
        do {
            if files.fileExists(atPath: photoURL(id).path) { try files.removeItem(at: photoURL(id)) }
            try files.removeItem(at: recordURL(id))
        } catch {
            if wasActive && files.fileExists(atPath: recordURL(id).path) { try? setActive(id) }
            throw error
        }
    }

    private func prepare() throws { try files.createDirectory(at: directory, withIntermediateDirectories: true) }
    private func recordURL(_ id: UUID) -> URL { directory.appendingPathComponent(id.uuidString).appendingPathExtension("json") }
    private func photoURL(_ id: UUID) -> URL { directory.appendingPathComponent(id.uuidString).appendingPathExtension("jpg") }
}
