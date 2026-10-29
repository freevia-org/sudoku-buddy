import XCTest
@testable import PuzzlePersistence

final class SharedImageInboxTests: XCTestCase {
    private var root: URL!
    private var inbox: SharedImageInbox!
    override func setUpWithError() throws {
        root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        inbox = SharedImageInbox(container: root)
    }
    override func tearDownWithError() throws { if FileManager.default.fileExists(atPath: root.path) { try FileManager.default.removeItem(at: root) } }
    func testHandoffSurvivesReopeningAndAcknowledgementOnlyRemovesSelectedImage() throws {
        try inbox.enqueue(Data([1, 2])); try inbox.enqueue(Data([3, 4]))
        let reopened = SharedImageInbox(container: root)
        let files = try reopened.pending(); XCTAssertEqual(files.count, 2)
        let data = try reopened.read(files[1]); XCTAssertFalse(data.isEmpty)
        try reopened.remove(files[0])
        XCTAssertEqual(try reopened.pending(), [files[1]])
        XCTAssertEqual(try reopened.read(files[1]), data)
    }
    func testEmptyOversizeAndFullQueueAreRejectedWithoutReplacingImages() throws {
        XCTAssertThrowsError(try inbox.enqueue(Data()))
        XCTAssertThrowsError(try inbox.enqueue(Data(count: SharedImageInbox.maximumBytes + 1)))
        for i in 0..<5 { try inbox.enqueue(Data([UInt8(i)])) }
        XCTAssertThrowsError(try inbox.enqueue(Data([9])))
        XCTAssertEqual(try inbox.pending().count, 5)
    }
    func testPendingDataExpiresAndDoesNotReappear() throws {
        try inbox.enqueue(Data([1]))
        XCTAssertTrue(try inbox.pending(now: Date().addingTimeInterval(8 * 86400)).isEmpty)
        XCTAssertTrue(try inbox.pending().isEmpty)
    }
    func testOutsideFilesAndSymlinksCannotBeReadOrDeleted() throws {
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        let outside = root.appendingPathComponent(UUID().uuidString + ".image")
        try Data([7]).write(to: outside)
        XCTAssertThrowsError(try inbox.read(outside)); XCTAssertThrowsError(try inbox.remove(outside))
        try FileManager.default.createDirectory(at: inbox.directory, withIntermediateDirectories: true)
        let link = inbox.directory.appendingPathComponent(UUID().uuidString + ".image")
        try FileManager.default.createSymbolicLink(at: link, withDestinationURL: outside)
        XCTAssertTrue(try inbox.pending().isEmpty)
        XCTAssertThrowsError(try inbox.read(link)); XCTAssertThrowsError(try inbox.remove(link))
        XCTAssertEqual(try Data(contentsOf: outside), Data([7]))
    }
}
