import Foundation
import Combine
import SudokuShared
import PuzzlePersistence

struct TechniqueInfo: Decodable, Identifiable {
    let name: String
    let difficulty: String
    let rule: String
    let howTo: String
    let available: Int?
    var id: String { name }
}
struct ChainEvidence: Decodable {
    struct Link: Decodable { let index: Int; let digit: Int; let from: Int? }
    let links: [Link]
    let deadEndFrom: Int?
    let deadEnd: [Int]
    let blocked: [Int]
    let missing: Int?
}
struct LessonOverlay: Decodable {
    let legend: [String]
    let digits: [Int]
    let roles: [String]
    let evidence: [Int]
    let focus: Int?
    let candidates: [[Int]]
    let removed: [[Int]]
    let chain: ChainEvidence?
}
struct LessonStep: Decodable {
    let overlayBefore: LessonOverlay
    let overlayAfter: LessonOverlay
    let overlayPractice: LessonOverlay
    let howTo: String
    let technique: String
    let effect: String?
    let explanation: String
    let difficulty: String
    let hasProof: Bool
    let index: Int
    let digit: Int
    let supporting: [Int]
    let removed: [Int]
    let before: String
    let after: String
    let beforeCandidates: [[Int]]
    let afterCandidates: [[Int]]
    let dependencyLinks: [HintPresentation.Question]?
    let dependencies: [Int]
    let chain: ChainEvidence?
}
struct StudyChapter: Decodable, Identifiable {
    let technique: String
    let from: Int
    let count: Int
    var id: Int { from }
}
struct TechniqueChoice: Decodable, Identifiable { let name: String; let count: Int; var id: String { name } }
struct StudyData: Decodable {
    let available: [TechniqueChoice]?
    let chapters: [StudyChapter]?
    let introduction: String?
    let reasoningNote: String?
    let techniques: [TechniqueInfo]?
    let steps: [LessonStep]?
    let solution: String?
    let finishes: Bool?
    let cumulative: Bool?
    let hardest: String?
    let triedOut: Int?
    let error: String?
}

@MainActor
final class StudyModel: ObservableObject {
    let givens: String
    let progress: String
    @Published private(set) var data: StudyData?
    @Published private(set) var busy = false
    @Published var message = ""
    @Published var mode: String
    @Published var technique: String
    @Published var introduction: Bool
    @Published var stepIndex: Int
    @Published var after: Bool
    @Published var chainDepth: Int
    @Published var practice: Bool
    @Published var selected: Int
    @Published var practiceCell: Int?
    @Published var practiceDigit: Int
    @Published var showAnswer = false
    @Published private(set) var parents: [StudyPosition] = []
    private var generation = 0
    init(givens: String, progress: String, mode: String, technique: String = "", bookmark: StudyBookmark?) {
        self.givens = givens; self.progress = progress; self.mode = mode; self.technique = technique
        let saved = bookmark.flatMap { $0.mode == mode && $0.givens == givens && $0.progress == progress && (technique.isEmpty || technique == $0.technique) ? $0 : nil }
        self.technique = saved?.technique ?? technique
        introduction = saved?.introduction ?? (saved == nil && mode != "proof")
        stepIndex = saved?.step ?? 0; after = saved?.after ?? true; chainDepth = saved?.chainDepth ?? 0
        practice = saved?.practice ?? false; selected = saved?.selected ?? 0; practiceDigit = saved?.practiceDigit ?? 0; showAnswer = saved?.revealed ?? false
        practiceCell = saved?.practiceCell
        parents = saved?.parents ?? []
    }
    var bookmark: StudyBookmark {
        StudyBookmark(mode: mode, technique: technique, givens: givens, progress: progress, step: stepIndex, after: after,
                      chainDepth: chainDepth, practice: practice, selected: selected, practiceDigit: practiceDigit, revealed: showAnswer, parents: parents.isEmpty ? nil : parents, introduction: introduction, practiceCell: practiceCell)
    }
    var steps: [LessonStep] { data?.steps ?? [] }
    var current: LessonStep? { !introduction && steps.indices.contains(stepIndex) ? steps[stepIndex] : nil }
    func load(mode: String? = nil, technique: String? = nil) async {
        if let mode { practiceCell = nil; introduction = mode != "proof"; parents = []; self.mode = mode; stepIndex = 0; chainDepth = 0; after = false; practice = false; practiceDigit = 0 }
        if let technique { self.technique = technique }
        generation += 1; let expected = generation
        busy = true; message = ""
        let clues = givens, answers = progress, kind = self.mode, name = self.technique, short = AppSettings.shared.shortChains
        let text = await Task.detached(priority: .userInitiated) {
            let engine = TeachingEngine()
            return kind == "catalog" ? engine.catalogJson(givens: clues, progress: answers) :
                engine.lessonJson(givens: clues, progress: answers, mode: kind, technique: name, shortChains: short)
        }.value
        guard !Task.isCancelled, expected == generation else { return }
        do {
            data = try JSONDecoder().decode(StudyData.self, from: Data(text.utf8))
            stepIndex = min(stepIndex, max(0, steps.count - 1))
            chainDepth = min(chainDepth, current?.chain?.links.count ?? 0)
            message = data?.error ?? ""
        } catch { message = "This puzzle could not be prepared." }
        busy = false
    }
    func move(to index: Int) {
        guard index == -1 || steps.indices.contains(index) else { return }
        introduction = index == -1
        practiceCell = nil; parents = []
        stepIndex = max(0, index); after = true; chainDepth = 0; practiceDigit = 0; showAnswer = false; message = ""
    }
    func visitReason(_ index: Int) {
        guard steps.indices.contains(index), introduction || index != stepIndex else { return }
        introduction = false
        let stack = parents + [StudyPosition(step: stepIndex, after: after, chainDepth: chainDepth, selected: selected)]
        move(to: index); parents = Array(stack.suffix(81))
    }
    func backToQuestion() {
        guard let frame = parents.last, steps.indices.contains(frame.step) else { return }
        let stack = Array(parents.dropLast())
        move(to: frame.step); after = frame.after; selected = frame.selected
        chainDepth = min(frame.chainDepth, current?.chain?.links.count ?? 0); parents = stack
    }
    func selectCell(_ index: Int) { selected = index; if practice { practiceCell = index } }
    func attempt(_ digit: Int) async {
        guard let current, current.hasProof, !busy else { return }
        guard let cell = practiceCell else { message = "Tap a square on the grid first."; return }
        practiceDigit = digit; busy = true
        let clues = givens, answers = progress, kind = mode, name = technique, short = AppSettings.shared.shortChains, at = stepIndex
        let text = await Task.detached(priority: .userInitiated) {
            TeachingEngine().practiceJson(givens: clues, progress: answers, mode: kind, technique: name, shortChains: short,
                step: Int32(at), cell: Int32(cell), digit: Int32(digit))
        }.value
        guard at == stepIndex, kind == mode, name == technique, cell == practiceCell else { busy = false; return }
        struct Attempt: Decodable { let correct: Bool; let message: String }
        if let result = try? JSONDecoder().decode(Attempt.self, from: Data(text.utf8)) {
            message = result.message
            if result.correct { showAnswer = true; after = true }
        }
        busy = false
    }
}
