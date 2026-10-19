import XCTest
@testable import PuzzlePersistence

final class FeedbackTests: XCTestCase {
    private var root: URL!
    private var store: ReceiptStore!
    private let endpoint = URL(string: "https://feedback.example.test/v1/reports")!
    override func setUpWithError() throws {
        root = FileManager.default.temporaryDirectory.appendingPathComponent("SudokuFeedbackTests-\(UUID())")
        store = ReceiptStore(directory: root)
    }
    override func tearDownWithError() throws {
        if FileManager.default.fileExists(atPath: root.path) { try FileManager.default.removeItem(at: root) }
    }
    private func snapshot() throws -> ReportSnapshot {
        let readings = (0..<81).map { ["cell": $0, "ink": "NONE", "roleUncertain": false] as [String: Any] }
        let evidence: [String: Any] = ["originalGrid": String(repeating: ".", count: 81), "uncertainCells": [2],
                                       "readings": readings, "gridLines": ["vertical": (0...9).map { $0 * 100 }, "horizontal": (0...9).map { $0 * 100 }], "corrections": []]
        let decoded = try JSONDecoder().decode(RecognitionEvidence.self, from: JSONSerialization.data(withJSONObject: evidence))
        return ReportSnapshot(puzzleID: UUID(), evidence: decoded, currentGrid: decoded.originalGrid, jpeg: Data([0xff, 0xd8, 0xff, 0xd9]), appVersion: "test")
    }
    private func staged() throws -> (SubmissionReceipt, Data) {
        let snapshot = try snapshot(), body = try ReportArchive.package(snapshot)
        return (try store.stage(body: body, endpoint: endpoint, puzzleID: snapshot.puzzleID, automatic: false, consent: true), body)
    }
    func testConsentIsRequiredBeforeCreatingAnyUploadFiles() throws {
        let report = try snapshot(), data = try ReportArchive.package(report)
        XCTAssertThrowsError(try store.stage(body: data, endpoint: endpoint, puzzleID: report.puzzleID, automatic: false, consent: false))
        XCTAssertFalse(FileManager.default.fileExists(atPath: root.path))
    }
    func testDeterministicArchiveAndStandardSHA256() throws {
        let report = try snapshot()
        XCTAssertEqual(try ReportArchive.package(report), try ReportArchive.package(report))
        XCTAssertEqual(ReportArchive.digest(Data("abc".utf8)), "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
    }
    #if os(macOS)
    func testArchiveCanBeReadBySystemUnzipAndContainsOnlyDisclosedFiles() throws {
        let (receipt, _) = try staged()
        let archive = root.appendingPathComponent(receipt.id + ".zip")
        func unzip(_ args: [String]) throws -> Data {
            let process = Process(), output = Pipe()
            process.executableURL = URL(fileURLWithPath: "/usr/bin/unzip"); process.arguments = args
            process.standardOutput = output; process.standardError = Pipe()
            try process.run()
            let data = output.fileHandleForReading.readDataToEndOfFile(); process.waitUntilExit()
            XCTAssertEqual(process.terminationStatus, 0)
            return data
        }
        let names = String(decoding: try unzip(["-Z1", archive.path]), as: UTF8.self)
        XCTAssertEqual(names.split(separator: "\n"), ["manifest.json", "puzzle.jpg"])
        let json = try JSONSerialization.jsonObject(with: unzip(["-p", archive.path, "manifest.json"])) as! [String: Any]
        XCTAssertEqual(json["schemaVersion"] as? Int, 1)
        XCTAssertEqual((json["readings"] as? [Any])?.count, 81)
        let lines=try XCTUnwrap(json["gridLines"] as? [String:[Double]])
        XCTAssertEqual(try XCTUnwrap(lines["vertical"]?.last),900.0/1152.0,accuracy:1e-12)
        XCTAssertTrue(lines.values.flatMap { $0 }.allSatisfy { (0...1).contains($0) })
        XCTAssertEqual(try unzip(["-p", archive.path, "puzzle.jpg"]), Data([0xff, 0xd8, 0xff, 0xd9]))
        _ = try unzip(["-t", archive.path]) // Independently validates CRCs and central directory.
    }
    #endif
    func testPendingReceiptAndExactArchiveSurviveRestart() throws {
        let (receipt, body) = try staged()
        let restarted = ReceiptStore(directory: root)
        XCTAssertEqual(try restarted.list(), [receipt])
        XCTAssertEqual(try restarted.archive(receipt), body)
        XCTAssertEqual(receipt.state, .pending)
    }
    func testIdenticalRetryDoesNotCreateDuplicateReceipt() throws {
        let (receipt, body) = try staged()
        let again = try store.stage(body: body, endpoint: endpoint, puzzleID: receipt.puzzleID, automatic: true, consent: true)
        XCTAssertEqual(again, receipt)
        XCTAssertEqual(try store.list().count, 1)
    }
    func testDeletedRevisionCannotBeRequeuedByAutomaticSharing() throws {
        var (receipt, body) = try staged()
        receipt.state = .deleted; receipt.deletionRequested = true
        try store.save(receipt); try store.discardArchive(receipt)
        let repeated = try store.stage(body: body, endpoint: endpoint, puzzleID: receipt.puzzleID, automatic: true, consent: true)
        XCTAssertEqual(repeated.state, .deleted)
        XCTAssertThrowsError(try store.archive(repeated))
    }
    func testModifiedPendingArchiveIsRejected() throws {
        let (receipt, _) = try staged()
        try Data("tampered".utf8).write(to: root.appendingPathComponent(receipt.id + ".zip"))
        XCTAssertThrowsError(try store.archive(receipt))
    }
    func testCorruptReceiptDoesNotHideOtherReceiptsAndIsNotOverwritten() throws {
        let (receipt, _) = try staged()
        let damagedID = String(repeating: "a", count: 64)
        let damaged = root.appendingPathComponent(damagedID + ".json")
        try Data("broken".utf8).write(to: damaged)
        XCTAssertEqual(try store.list(), [receipt]); XCTAssertFalse(store.warnings.isEmpty)
        XCTAssertEqual(try Data(contentsOf: damaged), Data("broken".utf8))
    }
    func testServerRepliesMustMatchReceiptAndHTTPStatus() throws {
        let digest = String(repeating: "a", count: 64)
        func json(_ status: String, _ id: String = String(repeating: "a", count: 64)) -> Data { Data("{\"receipt\":\"\(id)\",\"status\":\"\(status)\"}".utf8) }
        XCTAssertNoThrow(try ReportClient.validateUpload(code: 201, body: json("accepted"), expected: digest))
        XCTAssertNoThrow(try ReportClient.validateUpload(code: 200, body: json("already_received"), expected: digest))
        XCTAssertThrowsError(try ReportClient.validateUpload(code: 200, body: json("accepted"), expected: digest))
        XCTAssertThrowsError(try ReportClient.validateUpload(code: 201, body: json("already_received"), expected: digest))
        XCTAssertThrowsError(try ReportClient.validateUpload(code: 201, body: json("accepted", String(repeating: "b", count: 64)), expected: digest))
        XCTAssertThrowsError(try ReportClient.validateUpload(code: 201, body: Data("broken".utf8), expected: digest))
        XCTAssertNoThrow(try ReportClient.validateDeletion(code: 200, body: json("deleted"), expected: digest))
        XCTAssertNoThrow(try ReportClient.validateDeletion(code: 200, body: json("already_deleted"), expected: digest))
        XCTAssertThrowsError(try ReportClient.validateDeletion(code: 503, body: json("deleted"), expected: digest))
        XCTAssertThrowsError(try ReportClient.validateDeletion(code: 200, body: json("accepted"), expected: digest))
        XCTAssertThrowsError(try ReportClient.validateDeletion(code: 200, body: json("deleted", String(repeating: "b", count: 64)), expected: digest))
    }
    func testEndpointRejectsInsecureURLsAndCredentials() {
        for value in ["http://example.test/v1/reports", "https://user:secret@example.test/v1/reports", "https://example.test/v1/reports?token=x", "https://example.test/evilv1/reports", "file:///v1/reports"] {
            XCTAssertFalse(ReportClient.validEndpoint(URL(string: value)!))
        }
        XCTAssertTrue(ReportClient.validEndpoint(endpoint))
    }
    func testUploadUsesExactArchiveAndConsentHeader() async throws {
        let (receipt, body) = try staged()
        let transport = RecordingTransport()
        try await ReportClient(transport: transport).upload(receipt, body: body)
        let request = await transport.last!
        XCTAssertEqual(request.httpMethod, "POST"); XCTAssertEqual(request.httpBody, body)
        XCTAssertEqual(request.value(forHTTPHeaderField: "Content-Length"), String(body.count))
        XCTAssertEqual(request.value(forHTTPHeaderField: "Content-Type"), "application/zip")
        XCTAssertEqual(request.value(forHTTPHeaderField: "X-Sudoku-Training-Consent"), "yes")
    }
    func testDeletionRequestedReceiptNeverSendsPOST() async throws {
        var (receipt, body) = try staged(); receipt.deletionRequested = true; try store.save(receipt)
        let transport = RecordingTransport()
        do { try await ReportClient(transport: transport).upload(receipt, body: body); XCTFail("Should refuse upload") } catch {}
        let request = await transport.last
        XCTAssertNil(request)
        XCTAssertTrue(try ReceiptStore(directory: root).list()[0].deletionRequested)
    }
    func testDeletionFailureRetainsReceiptAndArchiveForRetry() async throws {
        var (receipt, body) = try staged(); receipt.deletionRequested = true; try store.save(receipt)
        let transport = RecordingTransport(fail: true)
        do { try await ReportClient(transport: transport).delete(receipt); XCTFail("Should fail") } catch {}
        XCTAssertEqual(try store.archive(receipt), body)
        XCTAssertEqual(try ReceiptStore(directory: root).list()[0].state, .pending)
    }
    func testRedirectIsNotFollowed() {
        let transport = HTTPSReportTransport(), session = URLSession(configuration: .ephemeral)
        let task = session.dataTask(with: endpoint)
        let response = HTTPURLResponse(url: endpoint, statusCode: 307, httpVersion: nil, headerFields: ["Location": "https://other.example.test/"])!
        var called = false
        transport.urlSession(session, task: task, willPerformHTTPRedirection: response, newRequest: URLRequest(url: URL(string: "https://other.example.test/")!)) { request in
            called = true; XCTAssertNil(request)
        }
        XCTAssertTrue(called); session.invalidateAndCancel()
    }
    func testBugDraftIsBoundedEscapedAndDoesNotAttachPhotos() {
        let url = BugDraft.url(version: "1.0\nInjected", build: "1", device: "iPhone & ?", system: "27", diagnostics: String(repeating: "😀```", count: 3000))!
        XCTAssertLessThanOrEqual(url.absoluteString.count, 7500)
        XCTAssertEqual(url.host, "github.com")
        let body = URLComponents(url: url, resolvingAgainstBaseURL: false)!.queryItems!.first { $0.name == "body" }!.value!
        XCTAssertTrue(body.contains("- iOS: 27")); XCTAssertFalse(body.contains("- Android:"))
        XCTAssertTrue(body.contains("No photos are attached automatically"))
        XCTAssertFalse(body.contains("\nInjected"))
        XCTAssertEqual(body.components(separatedBy: "```").count, 3)
    }
}

private actor RecordingTransport: ReportTransport {
    private(set) var last: URLRequest?
    let fail: Bool
    init(fail: Bool = false) { self.fail = fail }
    func send(_ request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        last = request
        if fail { throw URLError(.notConnectedToInternet) }
        let upload = request.httpMethod == "POST"
        let digest = upload ? ReportArchive.digest(request.httpBody!) : request.url!.lastPathComponent
        return (Data("{\"receipt\":\"\(digest)\",\"status\":\"\(upload ? "accepted" : "deleted")\"}".utf8),
                HTTPURLResponse(url: request.url!, statusCode: upload ? 201 : 200, httpVersion: nil, headerFields: nil)!)
    }
}
