import XCTest
@testable import PuzzlePersistence

final class RefusedScanTests: XCTestCase {
    func testPrunesOriginalsToSixAndRestoresAcrossRestart() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        let store = RefusedScanStore(directory: root)
        for i in 0..<9 { try store.keep(Data([UInt8(i)]), reason: "No grid", at: Date(timeIntervalSince1970: Double(i))) }
        let restarted = RefusedScanStore(directory: root)
        XCTAssertEqual(restarted.list().count, 6)
        XCTAssertEqual(try restarted.photo(XCTUnwrap(restarted.list().first)), Data([8]))
        XCTAssertEqual(try FileManager.default.contentsOfDirectory(atPath: root.path).count, 12)
        for scan in restarted.list() { try restarted.discard(scan) }
        XCTAssertTrue(restarted.list().isEmpty)
        XCTAssertTrue(try FileManager.default.contentsOfDirectory(atPath: root.path).isEmpty)
    }
    func testInvalidCaptureDoesNotReplaceExistingEvidence() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        let store = RefusedScanStore(directory: root)
        let original = try store.keep(Data([1, 2, 3]), reason: "No grid")
        XCTAssertThrowsError(try store.keep(Data(), reason: "broken"))
        XCTAssertEqual(store.list(), [original]); XCTAssertEqual(try store.photo(original), Data([1, 2, 3]))
    }
}
