import Foundation

public struct CellRecognition: Codable, Sendable, Equatable {
    public let cell: Int
    public let ink: String?
    public let digit: Int?
    public let confidence: Double?
    public let runnerUp: Int?
    public let runnerUpConfidence: Double?
    public let roleUncertain: Bool
    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(cell, forKey: .cell); try c.encode(ink, forKey: .ink)
        try c.encode(digit, forKey: .digit); try c.encode(confidence, forKey: .confidence)
        try c.encode(runnerUp, forKey: .runnerUp); try c.encode(runnerUpConfidence, forKey: .runnerUpConfidence)
        try c.encode(roleUncertain, forKey: .roleUncertain)
    }
}

public struct ReadingCorrection: Codable, Sendable, Equatable {
    public let cell: Int
    public let oldDigit: Int?
    public let newDigit: Int?
    public let oldSource: String
    public let newSource: String
    public let atMillis: Int64
    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(cell, forKey: .cell); try c.encode(oldDigit, forKey: .oldDigit)
        try c.encode(newDigit, forKey: .newDigit); try c.encode(oldSource, forKey: .oldSource)
        try c.encode(newSource, forKey: .newSource); try c.encode(atMillis, forKey: .atMillis)
    }
    public init(cell: Int, oldDigit: Int?, newDigit: Int?, oldSource: String, newSource: String, at: Date = Date()) {
        self.cell = cell; self.oldDigit = oldDigit; self.newDigit = newDigit
        self.oldSource = oldSource; self.newSource = newSource
        atMillis = Int64(at.timeIntervalSince1970 * 1000)
    }
}

public struct RecognitionEvidence: Codable, Sendable, Equatable {
    public struct Lines: Codable, Sendable, Equatable {
        public let vertical: [Double]
        public let horizontal: [Double]
    }
    public let originalGrid: String
    public let uncertainCells: [Int]
    public let readings: [CellRecognition]
    public let gridLines: Lines
    public var corrections: [ReadingCorrection] = []

    public var correctedGrid: String {
        var cells = Array(originalGrid.filter { !$0.isWhitespace })
        guard cells.count == 81 else { return originalGrid }
        for change in corrections where (0..<81).contains(change.cell) { cells[change.cell] = change.newDigit.map { Character(String($0)) } ?? "." }
        return stride(from: 0, to: 81, by: 9).map { String(cells[$0..<$0 + 9]) }.joined(separator: "\n")
    }

    public var isValid: Bool {
        originalGrid.filter { !$0.isWhitespace }.count == 81 &&
        originalGrid.allSatisfy { $0.isWhitespace || $0 == "." || ("1"..."9").contains(String($0)) } &&
        uncertainCells.allSatisfy { (0..<81).contains($0) } &&
        readings.count == 81 && Set(readings.map(\.cell)) == Set(0..<81) &&
        readings.allSatisfy { r in
            ["PRINTED", "ANSWER", "MARK", "NONE"].contains(r.ink ?? "NONE") &&
            (r.digit.map { (1...9).contains($0) } ?? true) &&
            (r.runnerUp.map { (1...9).contains($0) } ?? true) &&
            [r.confidence, r.runnerUpConfidence].allSatisfy { $0.map { $0.isFinite && (0...1).contains($0) } ?? true }
        } && gridLines.vertical.count == 10 && gridLines.horizontal.count == 10 &&
        [gridLines.vertical, gridLines.horizontal].allSatisfy { lines in
            lines.allSatisfy { $0.isFinite && (0...1600).contains($0) } && zip(lines, lines.dropFirst()).allSatisfy { $0 < $1 }
        } && corrections.allSatisfy { (0..<81).contains($0.cell) &&
            [$0.oldDigit, $0.newDigit].allSatisfy { $0.map { (1...9).contains($0) } ?? true } &&
            ["GIVEN", "GUESS", "EMPTY"].contains($0.oldSource) && ["GIVEN", "GUESS", "EMPTY"].contains($0.newSource) }
    }
}
