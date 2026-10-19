import Foundation
import CryptoKit

public enum FeedbackError: LocalizedError {
    case invalidReport, unavailable, invalidResponse, tooLarge, http(Int), missingConsent, deleted
    public var errorDescription: String? {
        switch self {
        case .invalidReport: return "This puzzle could not be prepared."
        case .unavailable: return "Submissions are not available in this build."
        case .invalidResponse: return "Submission response status is invalid."
        case .tooLarge: return "The report is too large to submit."
        case .http(let code): return "Submission service returned HTTP \(code)."
        case .missingConsent: return "Submit uncertain reading for analysis"
        case .deleted: return "Deletion response did not confirm this receipt."
        }
    }
}

public struct ReportSnapshot: Sendable {
    public let puzzleID: UUID
    public let evidence: RecognitionEvidence
    public let currentGrid: String
    public let jpeg: Data
    public let appVersion: String
    public init(puzzleID: UUID, evidence: RecognitionEvidence, currentGrid: String, jpeg: Data, appVersion: String) {
        self.puzzleID = puzzleID; self.evidence = evidence; self.currentGrid = currentGrid
        self.jpeg = jpeg; self.appVersion = appVersion
    }
}

/// Deterministic, uncompressed ZIP: retries retain the same server receipt.
public enum ReportArchive {
    public static let maximumBytes = 20 * 1024 * 1024
    public static func package(_ report: ReportSnapshot) throws -> Data {
        guard report.evidence.isValid, !report.jpeg.isEmpty,
              report.currentGrid.filter({ !$0.isWhitespace }).count == 81,
              report.currentGrid.allSatisfy({ $0.isWhitespace || $0 == "." || ("1"..."9").contains(String($0)) }) else { throw FeedbackError.invalidReport }
        guard report.jpeg.count < maximumBytes else { throw FeedbackError.tooLarge }
        struct Manifest: Encodable {
            let schemaVersion = 1
            let appVersion: String
            let originalGrid: String
            let currentGrid: String
            let uncertainCells: [Int]
            let readings: [CellRecognition]
            let corrections: [ReadingCorrection]
            let gridLines: RecognitionEvidence.Lines
        }
        // Early iOS builds kept 1152px grid coordinates; the shared report contract
        // uses fractions of the rectified photograph, as Android does.
        let storedLines=report.evidence.gridLines
        let legacyPixels=(storedLines.vertical.max() ?? 0)>1.01 || (storedLines.horizontal.max() ?? 0)>1.01
        let reportLines=legacyPixels ? RecognitionEvidence.Lines(vertical:storedLines.vertical.map { $0/1152 },horizontal:storedLines.horizontal.map { $0/1152 }) : storedLines
        let manifest = Manifest(appVersion: report.appVersion, originalGrid: report.evidence.originalGrid,
                                currentGrid: report.currentGrid, uncertainCells: report.evidence.uncertainCells.sorted(),
                                readings: report.evidence.readings.sorted { $0.cell < $1.cell },
                                corrections: report.evidence.corrections, gridLines: reportLines)
        let encoder = JSONEncoder(); encoder.outputFormatting = [.sortedKeys, .withoutEscapingSlashes]
        let body = zip([("manifest.json", try encoder.encode(manifest)), ("puzzle.jpg", report.jpeg)])
        guard body.count <= maximumBytes else { throw FeedbackError.tooLarge }
        return body
    }
    public static func digest(_ data: Data) -> String { SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined() }
    public static func validDigest(_ value: String) -> Bool { value.count == 64 && value.utf8.allSatisfy { (48...57).contains($0) || (97...102).contains($0) } }

    private static func zip(_ entries: [(String, Data)]) -> Data {
        var body = Data(); var directory = Data()
        for (name, data) in entries {
            let filename = Data(name.utf8), checksum = crc32(data), offset = UInt32(body.count)
            body.le32(0x04034b50); body.le16(20); body.le16(0); body.le16(0)
            body.le16(0); body.le16(33) // 1980-01-01, invariant across retries/timezones.
            body.le32(checksum); body.le32(UInt32(data.count)); body.le32(UInt32(data.count))
            body.le16(UInt16(filename.count)); body.le16(0); body.append(filename); body.append(data)
            directory.le32(0x02014b50); directory.le16(20); directory.le16(20); directory.le16(0); directory.le16(0)
            directory.le16(0); directory.le16(33); directory.le32(checksum)
            directory.le32(UInt32(data.count)); directory.le32(UInt32(data.count)); directory.le16(UInt16(filename.count))
            directory.le16(0); directory.le16(0); directory.le16(0); directory.le16(0); directory.le32(0); directory.le32(offset); directory.append(filename)
        }
        let start = UInt32(body.count); body.append(directory)
        body.le32(0x06054b50); body.le16(0); body.le16(0); body.le16(UInt16(entries.count)); body.le16(UInt16(entries.count))
        body.le32(UInt32(directory.count)); body.le32(start); body.le16(0)
        return body
    }
    private static func crc32(_ data: Data) -> UInt32 {
        var crc: UInt32 = 0xffffffff
        for byte in data {
            crc ^= UInt32(byte)
            for _ in 0..<8 { crc = (crc >> 1) ^ (crc & 1 == 1 ? 0xedb88320 : 0) }
        }
        return ~crc
    }
}
private extension Data {
    mutating func le16(_ n: UInt16) { var v = n.littleEndian; Swift.withUnsafeBytes(of: &v) { append(contentsOf: $0) } }
    mutating func le32(_ n: UInt32) { var v = n.littleEndian; Swift.withUnsafeBytes(of: &v) { append(contentsOf: $0) } }
}
