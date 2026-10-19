import XCTest
@testable import PuzzlePersistence

final class PuzzleStoreTests: XCTestCase {
    private var root: URL!
    private var store: PuzzleStore!

    override func setUpWithError() throws {
        root = FileManager.default.temporaryDirectory.appendingPathComponent("SudokuStoreTests-\(UUID())")
        store = PuzzleStore(directory: root)
    }
    override func tearDownWithError() throws {
        if FileManager.default.fileExists(atPath: root.path) { try FileManager.default.removeItem(at: root) }
    }
    private func puzzle(_ title: String = "Example puzzle") -> SavedPuzzle {
        var clues = Array(repeating: 0, count: 81); clues[0] = 5
        var answers = Array(repeating: 0, count: 81); answers[2] = 4
        return SavedPuzzle(title: title, givens: clues, progress: answers, selected: 2, editingClues: false,
                           evaluation: Evaluation(status: "ready", message: "A hint", incorrect: [], hintIndex: 3,
                                                  hintDigit: 6, technique: "Single", explanation: "Place a 6.", supporting: [0, 2]),
                           explanationVisible: true, message: "A hint")
    }

    func testFramingAdvicePersistsAndMalformedOptionalAdviceKeepsPuzzleRecoverable() throws {
        var record=puzzle();record.framingNote="Fit the whole grid in view.";record.readerComplaint="Please check this reading."
        try store.save(record)
        var restored=try XCTUnwrap(PuzzleStore(directory:root).load().active)
        XCTAssertEqual(restored.framingNote,record.framingNote)
        XCTAssertEqual(restored.readerComplaint,record.readerComplaint)
        let file=root.appendingPathComponent(record.id.uuidString+".json")
        var json=try XCTUnwrap(JSONSerialization.jsonObject(with:Data(contentsOf:file)) as? [String:Any])
        json["framingNote"]=["bad":true];json.removeValue(forKey:"readerComplaint")
        try JSONSerialization.data(withJSONObject:json).write(to:file)
        restored=try XCTUnwrap(PuzzleStore(directory:root).load().active)
        XCTAssertEqual(restored.progress,record.progress)
        XCTAssertNil(restored.framingNote);XCTAssertNil(restored.readerComplaint)
    }

    func testFourStageHintAndProofStackRestoreWithoutChangingProgress() throws {
        for depth in 0...3 {
            var record = puzzle()
            record.overlayMode = "Hint"
            record.hint = HintBookmark(depth: depth, frame: HintPosition(step: 7, consequence: 3, before: false),
                parents: [HintPosition(), HintPosition(step: 2, consequence: 1, before: false)])
            try store.save(record)
            let restored = try XCTUnwrap(PuzzleStore(directory: root).load().active)
            XCTAssertEqual(restored.hint, record.hint)
            XCTAssertEqual(restored.overlayMode, "Hint")
            XCTAssertEqual(restored.progress, record.progress)
        }
    }
    func testCorruptOptionalHintDoesNotHidePuzzle() throws {
        let record = puzzle(); try store.save(record)
        let url = root.appendingPathComponent("\(record.id.uuidString).json")
        var json = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        json["hint"] = ["depth": 99, "frame": ["step": -8, "consequence": -1, "before": true], "parents": []]
        try JSONSerialization.data(withJSONObject: json).write(to: url)
        XCTAssertNil(PuzzleStore(directory: root).load().active?.hint)
        XCTAssertEqual(PuzzleStore(directory: root).load().active?.givens, record.givens)
    }

    func testStudyPositionAndPracticeStateSurviveRestart() throws {
        var record = puzzle()
        let givens = record.givens.map { $0 == 0 ? "." : String($0) }.joined()
        let progress = record.progress.map { $0 == 0 ? "." : String($0) }.joined()
        record.study = StudyBookmark(mode: "route", givens: givens, progress: progress, step: 7,
                                     after: true, chainDepth: 2, practice: true, selected: 4, practiceDigit: 6, revealed: true)
        record.study?.parents = [StudyPosition(step: 3, after: true, chainDepth: 2, selected: 7)]
        record.study?.practiceCell = 4; record.study?.introduction = false
        try store.save(record)
        XCTAssertEqual(PuzzleStore(directory: root).load().active?.study, record.study)
    }

    func testMalformedOptionalStudyNeverHidesSavedPuzzle() throws {
        let record = puzzle()
        try store.save(record)
        let file = root.appendingPathComponent(record.id.uuidString + ".json")
        var json = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: file)) as? [String: Any])
        json["study"] = ["mode": "future-format", "step": -2]
        json["recognition"] = ["unrecognized": true]
        try JSONSerialization.data(withJSONObject: json).write(to: file)
        let loaded = PuzzleStore(directory: root).load().active
        XCTAssertEqual(loaded?.givens, record.givens); XCTAssertEqual(loaded?.progress, record.progress)
        XCTAssertNil(loaded?.study); XCTAssertNil(loaded?.recognition)
    }

    func testRestartRestoresExactPuzzleProgressAndHintStage() throws {
        let record = puzzle()
        try store.save(record)
        let restarted = PuzzleStore(directory: root).load()
        XCTAssertEqual(restarted.active, record)
        XCTAssertEqual(restarted.puzzles, [record])
        XCTAssertTrue(restarted.warnings.isEmpty)
    }

    func testUpdatingOnePuzzleDoesNotCreateDuplicateHistory() throws {
        var first = puzzle()
        try store.save(first)
        let second = puzzle("Another puzzle")
        try store.save(second)
        first.progress[3] = 6; first.evaluation = nil; first.updatedAt = Date().addingTimeInterval(1)
        try store.save(first)
        let loaded = store.load()
        XCTAssertEqual(loaded.puzzles.count, 2)
        XCTAssertEqual(loaded.puzzles.first?.id, first.id)
        XCTAssertEqual(loaded.active?.progress[3], 6)
        XCTAssertEqual(try store.read(second.id), second)
    }

    func testNewBlankSessionDoesNotReopenAnOldPuzzleOrEraseHistory() throws {
        let record = puzzle()
        try store.save(record)
        try store.setActive(nil)
        let restarted = PuzzleStore(directory: root).load()
        XCTAssertNil(restarted.active)
        XCTAssertEqual(restarted.puzzles, [record])
    }

    func testScanReviewPhotoAndRolesSurviveRestart() throws {
        var record = puzzle("Scanned puzzle")
        record.editingClues = true; record.reviewingScan = true; record.editAsAnswer = true
        record.hasPhoto = true; record.uncertain = [2, 17]; record.evaluation = nil
        let photo = Data([10, 20, 30, 40])
        try store.save(record, photo: photo)
        let restarted = PuzzleStore(directory: root)
        XCTAssertEqual(restarted.load().active, record)
        XCTAssertEqual(try restarted.photo(record.id), photo)
        var changed = record; changed.progress[2] = 7
        try restarted.save(changed)
        XCTAssertEqual(try restarted.photo(record.id), photo, "An edit must not rewrite or discard the photo")
    }

    func testExpandedPhotoReviewIsIndependentOfManualClueEditing() throws {
        var record = puzzle("Photo review")
        record.hasPhoto = true; record.editingClues = false; record.reviewingScan = true
        record.uncertain = [2]; record.overlayMode = "Read"
        try store.save(record, photo: Data([1, 2, 3]))
        XCTAssertEqual(PuzzleStore(directory: root).load().active, record)
        record.reviewingScan = false
        try store.save(record)
        XCTAssertEqual(PuzzleStore(directory: root).load().active, record)
        record.hasPhoto = false; record.reviewingScan = true
        XCTAssertThrowsError(try store.save(record))
    }

    func testDeleteActivePuzzleAlsoRemovesPhotoAndLeavesBlankCurrentSession() throws {
        let older = puzzle("Older puzzle")
        try store.save(older)
        var current = puzzle(); current.hasPhoto = true
        try store.save(current, photo: Data([1, 2, 3]))
        try store.delete(current.id)
        let restarted = PuzzleStore(directory: root)
        XCTAssertNil(restarted.load().active)
        XCTAssertEqual(restarted.load().puzzles, [older])
        XCTAssertThrowsError(try restarted.photo(current.id))
        XCTAssertThrowsError(try restarted.read(current.id))
    }

    func testDeletingInactivePuzzleKeepsCurrentSession() throws {
        let older = puzzle("Older"); try store.save(older)
        let current = puzzle("Current"); try store.save(current)
        try store.delete(older.id)
        XCTAssertEqual(store.load().active, current)
    }

    func testCorruptAndFutureRecordsAreKeptWithoutHidingReadableHistory() throws {
        let valid = puzzle(); try store.save(valid)
        let badID = UUID()
        let badURL = root.appendingPathComponent(badID.uuidString).appendingPathExtension("json")
        let damaged = Data("{broken".utf8)
        try damaged.write(to: badURL)
        var future = puzzle("Future format"); future.version = 2
        let futureURL = root.appendingPathComponent(future.id.uuidString).appendingPathExtension("json")
        let futureBytes = try JSONEncoder().encode(future)
        try futureBytes.write(to: futureURL)
        let loaded = store.load()
        XCTAssertEqual(loaded.puzzles, [valid])
        XCTAssertEqual(loaded.active, valid)
        XCTAssertFalse(loaded.warnings.isEmpty)
        XCTAssertEqual(try Data(contentsOf: badURL), damaged)
        XCTAssertEqual(try Data(contentsOf: futureURL), futureBytes)
    }

    func testDamagedActivePointerRecoversLatestReadablePuzzle() throws {
        let record = puzzle(); try store.save(record)
        let pointer = root.appendingPathComponent("current.json")
        let damaged = Data("broken pointer".utf8)
        try damaged.write(to: pointer)
        let loaded = store.load()
        XCTAssertEqual(loaded.active, record)
        XCTAssertFalse(loaded.warnings.isEmpty)
        XCTAssertEqual(try Data(contentsOf: pointer), damaged, "Loading must not destroy recovery evidence")
    }

    func testMissingPhotoDoesNotHideSavedGrid() throws {
        var record = puzzle(); record.hasPhoto = true
        try store.save(record)
        XCTAssertEqual(store.load().active, record)
        XCTAssertThrowsError(try store.photo(record.id))
    }

    func testInvalidGridAndCellIndicesCannotBeSaved() throws {
        var invalid = puzzle(); invalid.givens.removeLast()
        XCTAssertThrowsError(try store.save(invalid))
        invalid = puzzle(); invalid.progress[5] = 10
        XCTAssertThrowsError(try store.save(invalid))
        invalid = puzzle(); invalid.progress[0] = 1
        XCTAssertThrowsError(try store.save(invalid))
        invalid = puzzle(); invalid.selected = 81
        XCTAssertThrowsError(try store.save(invalid))
        invalid = puzzle(); invalid.uncertain = [-1]
        XCTAssertThrowsError(try store.save(invalid))
        XCTAssertTrue(store.load().puzzles.isEmpty)
    }

    func testWriteFailureIsReportedInsteadOfPretendingToSave() throws {
        try Data([1]).write(to: root)
        XCTAssertThrowsError(try store.save(puzzle()))
        XCTAssertFalse(store.load().warnings.isEmpty)
    }
}
