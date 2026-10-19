import SwiftUI
import PuzzlePersistence

struct StudyView: View {
    @ObservedObject var puzzle: PuzzleModel
    @State private var showHowTo = false
    @ObservedObject private var language = AppLanguage.shared
    @Environment(\.dismiss) private var dismiss
    @StateObject private var study: StudyModel
    init(puzzle: PuzzleModel, mode: String, technique: String = "", onExplore: ((String) -> Void)? = nil) {
        self.onExplore = onExplore
        self.puzzle = puzzle
        _study = StateObject(wrappedValue: StudyModel(givens: puzzle.cluesText, progress: puzzle.answersText, mode: mode,
                                                     technique: technique, bookmark: puzzle.study))
    }
    var embedded = false
    var onClose: (() -> Void)?
    var onExplore: ((String) -> Void)?
    init(puzzle: PuzzleModel, study: StudyModel, onClose: @escaping () -> Void, onExplore: @escaping (String) -> Void) {
        self.puzzle = puzzle; _study = StateObject(wrappedValue: study)
        embedded = true; self.onClose = onClose; self.onExplore = onExplore
    }
    var body: some View {
        Group {
            if embedded { content }
            else {
                NavigationStack {
                    content.navigationBarTitleDisplayMode(.inline)
                        .navigationTitle(tr(study.mode == "catalog" ? "Strategies" : study.mode == "proof" ? "Hint proof" : "Tutor"))
                        .toolbar { ToolbarItem(placement: .confirmationAction) { Button(tr("Done")) { dismiss() }.accessibilityIdentifier("studyDone") } }
                }
            }
        }
        .task { if study.data == nil { await study.load() }; puzzle.saveStudy(study.bookmark) }
        .onChange(of: study.stepIndex) { _ in showHowTo = false }
        .onChange(of: study.after) { _ in showHowTo = false }
        .onChange(of: study.technique) { _ in showHowTo = false }
        .onChange(of: study.bookmark) { puzzle.saveStudy($0) }
        .onDisappear { var bookmark = study.bookmark; bookmark.open = false; puzzle.saveStudy(bookmark) }
    }
    private var content: some View {
        VStack(spacing: 4) {
            if embedded {
                Button { onClose?() } label: { Capsule().fill(AppStyle.secondary).frame(width: 32, height: 4).frame(maxWidth: .infinity, minHeight: 16) }.accessibilityLabel(tr("Close"))
                ZStack {
                    HStack {
                        Button(tr(study.mode == "proof" ? "Hint proof" : "Tutor")) { onClose?() }
                            .foregroundStyle(AppStyle.onSurface).accessibilityIdentifier("studyDone")
                        Spacer()
                    }
                    stepping
                }.padding(.horizontal, 16).padding(.top, 8)
            }
            if study.mode != "catalog", study.data != nil {
                HStack(spacing: 10) {
                    Menu {
                        Button(tr("Full route")) { Task { await study.load(mode: "route") } }
                        ForEach(study.data?.available ?? []) { item in
                            Button(tr(item.name) + " · \(item.count)") { Task { await study.load(mode: "strategy", technique: item.name) } }
                        }
                        if study.data?.available?.isEmpty == true { Text(tr("No technique applies here")) }
                    } label: { Label(tr(study.mode == "proof" ? "Hint reasoning" : study.mode == "strategy" ? study.technique : "Full route"), systemImage: "chevron.down") }
                        .accessibilityIdentifier("tutorPicker")
                    if let step = study.current {
                        let overlay = study.practice && !study.showAnswer ? step.overlayPractice : study.after ? step.overlayAfter : step.overlayBefore
                        ForEach(overlay.legend, id: \.self) { entry in
                            HStack(spacing: 4) {
                                RoundedRectangle(cornerRadius: 3).fill(entry == "EVIDENCE" ? AppStyle.written : entry == "SOLUTION" ? AppStyle.solution : AppStyle.incorrect).frame(width: 10, height: 10)
                                Text(tr(entry == "EVIDENCE" ? step.technique : entry == "SOLUTION" ? "Answer" : entry == "DEAD_END" ? "Dead end" : "Removed")).font(AndroidText.bodySmall)
                            }
                        }
                    }
                    Spacer(minLength: 0)
                    if !study.introduction, let chapter = study.data?.chapters?.first(where: { ($0.from..<($0.from + $0.count)).contains(study.stepIndex) }), chapter.count > 1 {
                        Text(tr("\(study.stepIndex - chapter.from + 1) of \(chapter.count)")).font(AndroidText.bodySmall)
                    }
                }.padding(.horizontal, 16).font(AndroidText.bodySmall)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 2) {
                        ForEach(study.data?.chapters ?? []) { chapter in
                            Button(tr("\(chapter.technique) (\(chapter.from + 1)–\(chapter.from + chapter.count))")) { study.move(to: chapter.from) }
                                .font(AndroidText.bodySmall).padding(8).foregroundStyle(!study.introduction && (chapter.from..<(chapter.from + chapter.count)).contains(study.stepIndex) ? AppStyle.primary : AppStyle.secondary)
                        }
                    }.padding(.horizontal, 8)
                }
            }
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if study.busy { ProgressView(tr("Preparing puzzle…")) }
                    if !study.message.isEmpty { Text(tr(study.message)).accessibilityIdentifier("studyStatus") }
                    if study.mode == "catalog" { catalog }
                    else {
                        if !embedded { stepping }
                        if let note = study.data?.reasoningNote, !note.isEmpty { Text(tr(note)).font(AndroidText.bodySmall) }
                        if study.mode == "proof" { Button(tr("Back to visual hint")) { onClose?(); if !embedded { dismiss() } } }
                        if study.introduction {
                            Text(tr(study.mode == "strategy" ? "Examples available now: each starts from your current board." : "Follow a route where each move builds on the previous one."))
                            Text(tr(study.data?.introduction ?? "")).accessibilityIdentifier("studyIntroduction")
                        } else if let step = study.current {
                            lesson(step)
                        } else if !study.busy { Text(tr("Nothing left to work out.")) }
                    }
                }.padding(16).frame(maxWidth: .infinity, alignment: .leading).disabled(study.busy)
            }.accessibilityIdentifier("studyMessages")
                .simultaneousGesture(DragGesture(minimumDistance: 48).onEnded { value in
                    guard abs(value.translation.width) > abs(value.translation.height), study.mode != "catalog" else { return }
                    study.move(to: value.translation.width < 0 ? (study.introduction ? 0 : study.stepIndex + 1) : study.stepIndex - 1)
                })
        }.background(embedded ? AppStyle.surface : AppStyle.background)
    }
    private var stepping: some View {
        HStack {
            Button { study.move(to: study.stepIndex - 1) } label: { Image(systemName: "chevron.left").frame(width: 40, height: 44) }
                .disabled(study.introduction).accessibilityLabel(tr("Previous step")).accessibilityIdentifier("previousStudyStep")
            Text(tr("\(study.introduction ? 0 : study.stepIndex + 1) / \(study.steps.count)")).font(AndroidText.bodyMedium).accessibilityIdentifier("studyStepCount")
            Button { study.move(to: study.introduction ? 0 : study.stepIndex + 1) } label: { Image(systemName: "chevron.right").frame(width: 40, height: 44) }
                .disabled(!study.introduction && study.stepIndex + 1 == study.steps.count).accessibilityLabel(tr("Next step")).accessibilityIdentifier("nextStudyStep")
        }
    }
    private var proofQuestions: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(tr("Work backwards from your square. Choose a question below."))
            if let final = study.steps.indices.last(where: { study.steps[$0].index >= 0 }) {
                let target = study.steps[final].index
                ForEach(study.steps.indices.filter { study.steps[$0].removed.contains(target) }, id: \.self) { index in
                    let reason = study.steps[index]
                    Button(tr("Why not \(reason.digit)? · \(reason.technique)")) { study.visitReason(index) }
                }
                Button(tr("Why this square? · \(study.steps[final].technique)")) { study.visitReason(final) }
            }
            if !study.parents.isEmpty {
                Button(tr("Back to the previous question")) { study.backToQuestion() }.accessibilityIdentifier("previousProofQuestion")
            }
        }
    }
    private var catalog: some View {
        LazyVStack(alignment: .leading, spacing: 28) {
            Text(tr("Every sudoku with one answer can be solved by reasoning; guessing is never required and rarely helps. These are the ways of reasoning this app can recognise and explain, easiest first. Where one of them applies to the puzzle you have open, it will show you every place it does."))
            ForEach(study.data?.techniques ?? []) { technique in
                VStack(alignment: .leading, spacing: 10) {
                    Text(tr(technique.name)).font(AndroidText.titleMedium)
                    Text(tr(difficulty(technique.difficulty))).font(AndroidText.bodySmall).foregroundStyle(AppStyle.accent)
                    Text(tr(technique.rule))
                    Text(tr(technique.howTo)).font(AndroidText.bodyMedium).foregroundStyle(.secondary)
                    if let count = technique.available {
                        if count == 0 { Text(tr("None on your puzzle at the moment.")).font(AndroidText.bodySmall) }
                        else {
                            Button(tr(count == 1 ? "Show me the one on your puzzle" : "Show me all \(count) on your puzzle")) {
                                if let onExplore { onExplore(technique.name) } else { Task { await study.load(mode: "strategy", technique: technique.name) } }
                            }.accessibilityIdentifier("explore-\(technique.name)")
                        }
                    }
                }
            }
            Text(tr("A technique list is never complete, and the hardest puzzles are built to defeat whatever is on it. So when every one of these comes up empty, the tutor settles a single square by trying its candidates out, says plainly that is what it has done, and carries on reasoning from there. It always reaches the end of a puzzle, and it never pretends a lookup was a deduction.")).font(AndroidText.bodySmall)
        }
    }
    private func lesson(_ step: LessonStep) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            if !embedded {
                LessonBoard(step: step, after: study.after, selected: Binding(get: { study.selected }, set: { study.selectCell($0) }), chainDepth: study.chainDepth, hideAnswer: study.practice && !study.showAnswer)
            }
            if step.hasProof {
                Toggle(tr("Try it yourself"), isOn: $study.practice)
                    .onChange(of: study.practice) { enabled in if enabled { study.after = false; study.showAnswer = false; study.practiceDigit = 0; study.practiceCell = nil } }.accessibilityIdentifier("studyPractice")
                if study.practice && !study.showAnswer {
                    Text(tr(step.index >= 0 ? "Using \(step.technique), tap the square you can fill, then choose its digit." : "Using \(step.technique), tap one square where a candidate can be removed, then choose that digit."))
                    HStack {
                        ForEach(1...9, id: \.self) { digit in Button(String(digit)) { Task { await study.attempt(digit) } }.frame(maxWidth: .infinity) }
                    }.environment(\.layoutDirection, .leftToRight)
                    Button(tr("Reveal explanation")) { study.showAnswer = true; study.after = true }
                }
            }
            if !study.practice || study.showAnswer {
                HStack(spacing: 12) {
                    Button(tr("Before")) { study.after = false }.disabled(!study.after).accessibilityIdentifier("studyBefore")
                    Button(tr("After")) { study.after = true }.disabled(study.after).accessibilityIdentifier("studyAfter")
                    Text(tr(study.after ? "After this move" : "Before this move")).font(AndroidText.bodySmall)
                }.frame(minHeight: 44)
                if !study.after { Text(tr("Switch to After to see the move and its consequences on the grid.")).font(AndroidText.bodySmall) }
                Text(tr(step.explanation)).accessibilityIdentifier("studyExplanation")
                if let effect = step.effect, !effect.isEmpty { Text(tr(effect)).font(AndroidText.bodySmall).foregroundStyle(AppStyle.secondary) }
                if !step.howTo.isEmpty {
                    Button { showHowTo.toggle() } label: { Label(tr("How to spot one"), systemImage: "chevron.right") }.accessibilityLabel(tr(showHowTo ? "Hide how to spot one" : "How to spot one"))
                }
                if !step.hasProof { Text(tr("\n\nSolver-assisted answer: the app's supported techniques did not find a move. A detailed proof is unavailable.")) }
                if !(step.dependencyLinks?.isEmpty ?? true) { Text(tr("Earlier steps used in this proof")).font(.caption.weight(.medium)) }
                ForEach(step.dependencyLinks ?? []) { dependency in
                    Button(tr(dependency.label)) { study.visitReason(dependency.step) }
                }
                if let frame = study.parents.last {
                    Button(tr("Return to step \(frame.step + 1)")) { study.backToQuestion() }.accessibilityIdentifier("previousProofQuestion")
                }


            }
            if study.stepIndex + 1 == study.steps.count, study.mode == "proof" {
                Button(tr("Continue with the full route")) { Task { await study.load(mode: "route") } }
            }
            if study.mode != "proof", study.stepIndex + 1 == study.steps.count, study.data?.finishes == true { Text(tr("Route complete. Go back to review any move, or try the steps yourself.")) }

            if showHowTo { Text(tr(step.howTo)).font(AndroidText.bodyMedium).foregroundStyle(AppStyle.secondary) }
        }
    }
    private func difficulty(_ value: String) -> String {
        ["EASY": "Gentle", "MEDIUM": "Moderate", "HARD": "Hard", "VERY_HARD": "Very hard"][value] ?? value
    }
}

struct LessonBoard: View {
    let step: LessonStep
    let after: Bool
    @Binding var selected: Int
    let chainDepth: Int
    let hideAnswer: Bool
    var photo: UIImage? = nil
    var lines: RecognitionEvidence.Lines? = nil
    var cellPrefix = "study-cell-"
    private var overlay: LessonOverlay { hideAnswer ? step.overlayPractice : after ? step.overlayAfter : step.overlayBefore }
    private var digits: [Int] { (after && !hideAnswer ? step.after : step.before).map { $0.wholeNumberValue ?? 0 } }
    var body: some View {
        GeometryReader { geometry in
            ZStack(alignment: .topLeading) {
                if let photo { Image(uiImage: photo).resizable().frame(width: geometry.size.width, height: geometry.size.height) }
                else { Color.white }
                ForEach(0..<81, id: \.self) { index in
                    let rect = cellRect(index, size: geometry.size)
                    Button { selected = index } label: {
                        ZStack {
                            background(index)
                            if let hypothetical = overlay.chain?.links.last(where: { $0.index == index }) {
                                Text(String(hypothetical.digit)).font(AppStyle.puzzleFont(size: rect.height * 0.62, weight: .bold)).foregroundStyle(.black).blendMode(.destinationOut)
                            } else if overlay.digits[index] != 0 {
                                Text(String(overlay.digits[index])).font(AppStyle.puzzleFont(size: rect.height * 0.62, weight: .bold)).foregroundStyle(AppStyle.solution)
                            } else if digits[index] != 0 && photo == nil {
                                Text(String(digits[index])).font(AppStyle.puzzleFont(size: rect.height * 0.62)).foregroundStyle(Color.black)
                            } else if !overlay.candidates[index].isEmpty || !overlay.removed[index].isEmpty {
                                Color.white.opacity(0.92)
                                ForEach(Array(Set(overlay.candidates[index] + overlay.removed[index])).sorted(), id: \.self) { digit in
                                    Text(String(digit)).font(AppStyle.puzzleFont(size: rect.height * 0.24, weight: .medium))
                                        .foregroundStyle(overlay.removed[index].contains(digit) ? AppStyle.incorrect : .black)
                                        .strikethrough(overlay.removed[index].contains(digit))
                                        .position(x: (CGFloat((digit - 1) % 3) + 0.5) * rect.width / 3,
                                                  y: (CGFloat((digit - 1) / 3) + 0.5) * rect.height / 3)
                                }
                            }
                            if let chain = overlay.chain { ChainContradiction(chain: chain, index: index, side: rect.height) }
                            if overlay.focus == index { Rectangle().stroke(AppStyle.solution, lineWidth: rect.height * 0.06).padding(rect.height * 0.04) }
                            if let at = overlay.chain?.links.firstIndex(where: { $0.index == index }) {
                                Text(String(at + 1)).font(AppStyle.puzzleFont(size: rect.height * 0.26, weight: .bold)).foregroundStyle(.white)
                                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading).padding(rect.height*0.04)
                            }
                        }.compositingGroup().frame(width: rect.width, height: rect.height).contentShape(Rectangle())
                    }.buttonStyle(.plain).position(x: rect.midX, y: rect.midY)
                        .accessibilityIdentifier("\(cellPrefix)\(index)").accessibilityLabel(label(index))
                        .accessibilityAddTraits(selected == index ? .isSelected : [])
                }
                if photo == nil {
                    Path { path in
                        for n in 0...9 {
                            let x = CGFloat(n) * geometry.size.width / 9, y = CGFloat(n) * geometry.size.height / 9
                            path.move(to: CGPoint(x: x, y: 0)); path.addLine(to: CGPoint(x: x, y: geometry.size.height))
                            path.move(to: CGPoint(x: 0, y: y)); path.addLine(to: CGPoint(x: geometry.size.width, y: y))
                        }
                    }.stroke(Color.black.opacity(0.5), lineWidth: 0.5).allowsHitTesting(false)
                    Path { path in
                        for n in stride(from: 0, through: 9, by: 3) {
                            let x = CGFloat(n) * geometry.size.width / 9, y = CGFloat(n) * geometry.size.height / 9
                            path.move(to: CGPoint(x: x, y: 0)); path.addLine(to: CGPoint(x: x, y: geometry.size.height))
                            path.move(to: CGPoint(x: 0, y: y)); path.addLine(to: CGPoint(x: geometry.size.width, y: y))
                        }
                    }.stroke(Color.black.opacity(0.9), lineWidth: 1.5).allowsHitTesting(false)
                }
                if let chain = overlay.chain { ChainArrows(chain: chain, hypothetical: false, rect: cellRect) }

            }.clipped()
        }.aspectRatio(1, contentMode: .fit).environment(\.layoutDirection, .leftToRight)
    }
    private func cellRect(_ index: Int, size: CGSize) -> CGRect {
        let row = index / 9, column = index % 9
        if let lines, let photo {
            let normalized = (lines.vertical.last ?? 1) <= 1.01
            let sx = normalized ? size.width : size.width / photo.size.width
            let sy = normalized ? size.height : size.height / photo.size.height
            return CGRect(x: lines.vertical[column] * sx, y: lines.horizontal[row] * sy,
                width: (lines.vertical[column + 1] - lines.vertical[column]) * sx,
                height: (lines.horizontal[row + 1] - lines.horizontal[row]) * sy)
        }
        return CGRect(x: CGFloat(column) * size.width / 9, y: CGFloat(row) * size.height / 9, width: size.width / 9, height: size.height / 9)
    }
    private func background(_ index: Int) -> Color {
        if selected == index && hideAnswer { return AppStyle.accent.opacity(0.3) }
        if overlay.chain?.deadEnd.contains(index) == true { return AppStyle.incorrect.opacity(0.34) }
        if overlay.chain?.links.contains(where: { $0.index == index }) == true { return AppStyle.written.opacity(0.45) }
        if overlay.evidence.contains(index) { return AppStyle.written.opacity(0.28) }
        return .clear
    }
    private func label(_ index: Int) -> String {
        let digit = overlay.digits[index] == 0 ? digits[index] : overlay.digits[index]
        var label = tr("Row \(index / 9 + 1), column \(index % 9 + 1)") + (digit == 0 ? ", " + tr("empty") : ", \(digit)")
        if !overlay.candidates[index].isEmpty { label += tr(", candidates \(overlay.candidates[index].map(String.init).joined(separator: ", "))") }
        if let link = overlay.chain?.links.first(where: { $0.index == index }) { label += tr(", hypothetical \(link.digit), not an entered answer") }
        if overlay.chain?.deadEnd.contains(index) == true { label += tr(", contradiction") }
        return label
    }
}
