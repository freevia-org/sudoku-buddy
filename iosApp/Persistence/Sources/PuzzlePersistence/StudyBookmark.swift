import Foundation

public struct StudyPosition: Codable, Equatable, Sendable {
    public var step: Int
    public var after: Bool
    public var chainDepth: Int
    public var selected: Int
    public init(step: Int, after: Bool, chainDepth: Int, selected: Int) {
        self.step = step; self.after = after; self.chainDepth = chainDepth; self.selected = selected
    }
    public var isValid: Bool { (0..<729).contains(step) && (0...81).contains(chainDepth) && (0..<81).contains(selected) }
}

public struct StudyBookmark: Codable, Equatable, Sendable {
    public var mode: String
    public var technique: String
    public var givens: String
    public var progress: String
    public var step: Int
    public var after: Bool
    public var chainDepth: Int
    public var practice: Bool
    public var selected: Int
    public var practiceCell: Int?
    public var practiceDigit: Int
    public var revealed: Bool
    public var parents: [StudyPosition]?
    public var introduction: Bool?
    public var open: Bool
    public init(mode: String, technique: String = "", givens: String, progress: String, step: Int = 0,
                after: Bool = false, chainDepth: Int = 0, practice: Bool = false, selected: Int = 0, practiceDigit: Int = 0, revealed: Bool = false, open: Bool = true, parents: [StudyPosition]? = nil, introduction: Bool? = nil, practiceCell: Int? = nil) {
        self.mode = mode; self.technique = technique; self.givens = givens; self.progress = progress; self.step = step
        self.after = after; self.chainDepth = chainDepth; self.practice = practice; self.selected = selected
        self.practiceDigit = practiceDigit; self.revealed = revealed; self.open = open; self.parents = parents; self.introduction = introduction; self.practiceCell = practiceCell
    }
    public var isValid: Bool {
        (practiceCell.map { (0..<81).contains($0) } ?? true) &&
        (parents.map { $0.count <= 81 && $0.allSatisfy(\.isValid) } ?? true) &&
        ["catalog", "route", "proof", "strategy"].contains(mode) && (0..<729).contains(step) && (0...81).contains(chainDepth) &&
        (0..<81).contains(selected) && (0...9).contains(practiceDigit) &&
        [givens, progress].allSatisfy { $0.count == 81 && $0.allSatisfy { $0 == "." || ("0"..."9").contains(String($0)) } }
    }
}
