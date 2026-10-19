import XCTest

final class PuzzleFlowTests: XCTestCase {
    func testAndroidLayoutPortraitLandscapeAndEditing() {
        continueAfterFailure = false
        let app = isolatedApp(); app.launch()
        XCTAssertTrue(app.buttons["loadExample"].waitForExistence(timeout: 15))
        assertBoardGeometry(app)
        attachScreenshot(app, name: "Android parity - empty portrait")
        app.buttons["loadExample"].tap()
        app.buttons["startPuzzle"].tap()
        waitForPuzzleReady(app)
        app.buttons["cell-2"].tap()
        XCTAssertTrue(app.buttons["cellEditorDone"].waitForExistence(timeout: 5))
        attachScreenshot(app, name: "Android parity - cell editor")
        app.buttons["Enter 1"].tap(); finishCellEditing(app)
        app.buttons["checkAnswers"].tap()
        expectation(for: NSPredicate(format: "label CONTAINS %@", "A red square"), evaluatedWith: app.staticTexts["puzzleGuidance"])
        waitForExpectations(timeout: 20)
        attachScreenshot(app, name: "Android parity - check")
        app.buttons["cell-2"].tap(); app.buttons["Erase"].tap(); finishCellEditing(app)
        app.buttons["requestHint"].tap()
        assertFourStageHint(app)
        attachScreenshot(app, name: "Android parity - four stage answer")
        app.buttons["showSolution"].tap()
        XCTAssertTrue(app.staticTexts["solutionLegend"].waitForExistence(timeout: 20))
        attachScreenshot(app, name: "Android parity - solution")
        XCUIDevice.shared.orientation = .landscapeLeft
        waitForOrientation(app, landscape: true)
        XCTAssertLessThan(app.buttons["cell-80"].frame.maxX, app.buttons["showSolution"].frame.minX)
        assertBoardGeometry(app)
        XCTAssertTrue(app.buttons["checkAnswers"].isHittable)
        XCTAssertTrue(app.buttons["showSolution"].isHittable)
        attachScreenshot(app, name: "Android parity - landscape")
        XCUIDevice.shared.orientation = .portrait
        waitForOrientation(app, landscape: false)
        app.buttons["puzzleHistory"].tap()
        XCTAssertTrue(app.buttons["historyDone"].waitForExistence(timeout: 5))
        attachScreenshot(app, name: "Android parity - history drawer")
        app.buttons["historyDone"].tap()
        app.buttons["moreMenu"].tap(); app.buttons["appSettings"].tap()
        XCTAssertTrue(app.navigationBars["Settings"].waitForExistence(timeout: 5))
        attachScreenshot(app, name: "Android parity - settings")
    }

    func testAndroidPhotoLayoutAndReviewSheet() {
        continueAfterFailure = false
        let app = isolatedApp(); app.launchArguments.append("--scan-review-fixture"); app.launch()
        openReadingReview(app)
        XCTAssertTrue(app.buttons["startPuzzle"].isHittable)
        assertBoardGeometry(app, photographed: true)
        attachScreenshot(app, name: "Android parity - photographed reading")
        app.buttons["cell-2"].tap()
        XCTAssertTrue(app.staticTexts["cellReading"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Enter 9"].isHittable)
        XCTAssertLessThan(abs(app.buttons["Enter 1"].frame.minX - app.buttons["Enter 7"].frame.minX), 1)
        attachScreenshot(app, name: "Android parity - reading correction")
        app.buttons["Enter 4"].tap()
        finishCellEditing(app)
        app.terminate(); app.launchArguments.removeAll { $0 == "--scan-review-fixture" }; app.launch()
        XCTAssertTrue(app.buttons["reviewReading"].waitForExistence(timeout:15))
        XCTAssertEqual(app.buttons["cell-2"].label,"Row 1, column 3, 4, entry")
        XCTAssertFalse(app.buttons["startPuzzle"].exists)
        XCTAssertTrue(app.buttons["reviewReading"].waitForExistence(timeout: 15))
        app.buttons["reviewReading"].tap()
        XCTAssertTrue(app.buttons["reportReading"].waitForExistence(timeout: 5))
        app.buttons["scanPuzzle"].tap(); app.buttons["Scan another puzzle"].tap()
        XCTAssertTrue(app.buttons["Choose a photo"].waitForExistence(timeout: 10))
        attachScreenshot(app, name: "Android parity - scanner import")
    }

    private func finishCellEditing(_ app: XCUIApplication) {
        if app.buttons["cellEditorDone"].exists && app.buttons["cellEditorDone"].isHittable { app.buttons["cellEditorDone"].tap() }
    }
    private func waitForPuzzleReady(_ app: XCUIApplication) {
        let ready = NSPredicate { _, _ in !app.buttons["startPuzzle"].exists && app.buttons["checkAnswers"].isEnabled }
        expectation(for: ready, evaluatedWith: app)
        waitForExpectations(timeout: 30)
    }
    func testSolveAmbiguityAndContradictoryClues() {
        continueAfterFailure = false
        let app = isolatedApp(); app.launchArguments += ["--puzzle-fixture", "1" + String(repeating: ".", count: 80)]; app.launch()
        XCTAssertTrue(app.buttons["showSolution"].waitForExistence(timeout: 15))
        XCTAssertFalse(app.buttons["tutorMenu"].exists)
        XCTAssertFalse(app.buttons["requestHint"].isEnabled)
        app.buttons["showSolution"].tap()
        XCTAssertTrue(app.staticTexts["solutionLegend"].waitForExistence(timeout: 20))
        let first = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", "cell-")).allElementsBoundByIndex.map(\.label)
        app.buttons["showSolution"].tap()
        let second = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", "cell-")).allElementsBoundByIndex.map(\.label)
        XCTAssertNotEqual(first, second)
        attachScreenshot(app, name: "Ambiguous puzzle - second solution")
        app.terminate(); app.launch()
        XCTAssertTrue(app.staticTexts["solutionLegend"].waitForExistence(timeout: 20))
        XCTAssertEqual(second, app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", "cell-")).allElementsBoundByIndex.map(\.label))
        app.buttons["readPuzzle"].tap()
        XCTAssertTrue(app.buttons["cell-2"].label.contains("empty"))
        app.terminate()
        let bad = isolatedApp(); bad.launchArguments += ["--puzzle-fixture", "55" + String(repeating: ".", count: 79)]; bad.launch()
        XCTAssertTrue(bad.buttons["showSolution"].waitForExistence(timeout: 15))
        bad.buttons["showSolution"].tap()
        XCTAssertTrue(bad.staticTexts["solutionLegend"].waitForExistence(timeout: 20))
        XCTAssertFalse(bad.buttons["requestHint"].isEnabled)
        XCTAssertFalse(bad.buttons["tutorMenu"].exists)
        XCTAssertGreaterThan(bad.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@ AND label CONTAINS %@", "cell-", "supporting evidence")).count, 0)
        attachScreenshot(bad, name: "Contradictory clues - minimal correction")
    }

    func testRefusedPhotoHistoryAndBugDraftStayLocal() {
        continueAfterFailure = false
        let app = isolatedApp(); app.launchArguments.append("--refused-fixture"); app.launch()
        XCTAssertTrue(app.buttons["puzzleHistory"].waitForExistence(timeout: 15))
        app.buttons["puzzleHistory"].tap()
        XCTAssertTrue(app.staticTexts["Would not read"].waitForExistence(timeout: 15))
        XCTAssertTrue(app.buttons["Send this photo"].exists)
        attachScreenshot(app, name: "Refused original - history")
        let delete = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", "refused-delete-")).firstMatch
        XCTAssertTrue(delete.exists); delete.tap()
        app.buttons.matching(identifier: "confirmRefusedDeletion").firstMatch.tap()
        XCTAssertFalse(app.staticTexts["Would not read"].exists)
        app.buttons["historyDone"].tap()
        app.launchArguments.removeAll { $0 == "--refused-fixture" }; app.terminate(); app.launch()
        app.buttons["puzzleHistory"].tap(); XCTAssertFalse(app.staticTexts["Would not read"].exists); app.buttons["historyDone"].tap()
        app.buttons["moreMenu"].tap(); app.buttons["reportBug"].tap()
        let diagnostics = app.switches["includeBugDiagnostics"]
        XCTAssertTrue(diagnostics.waitForExistence(timeout: 10)); XCTAssertEqual(diagnostics.value as? String, "0")
        app.buttons["Review reading"].tap()
        XCTAssertTrue(app.staticTexts["bugDraftPreview"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["bugDraftPreview"].label.contains("Refused scans retained"))
        diagnostics.tap()
        XCTAssertEqual(diagnostics.value as? String, "1")
        XCTAssertTrue(app.staticTexts["bugDraftPreview"].label.contains("Refused scans retained"))
        app.swipeUp()
        XCTAssertTrue(app.buttons["openBugDraft"].waitForExistence(timeout: 5))
        attachScreenshot(app, name: "Reviewable public bug draft - no submission")
    }

    func testCameraControlsMatchAndroidAndPersistAutoPhoto() {
        continueAfterFailure = false
        let app = isolatedApp(); app.launchArguments.append("--camera-test-start"); app.launch()
        let automatic = app.switches["cameraAutoCapture"]
        XCTAssertTrue(automatic.waitForExistence(timeout: 20))
        XCTAssertEqual(automatic.value as? String, "1")
        automatic.tap(); XCTAssertEqual(automatic.value as? String, "0")
        XCTAssertTrue(app.buttons["cameraHistory"].exists)
        app.buttons["cameraMore"].tap()
        XCTAssertTrue(app.buttons.matching(identifier: "cameraStrategies").firstMatch.exists)
        XCTAssertTrue(app.buttons.matching(identifier: "cameraAbout").firstMatch.exists)
        XCTAssertTrue(app.buttons.matching(identifier: "cameraBug").firstMatch.exists)
        app.buttons.matching(identifier: "cameraSettings").firstMatch.tap()
        XCTAssertTrue(app.switches["autoCapture"].waitForExistence(timeout: 10))
        XCTAssertEqual(app.switches["autoCapture"].value as? String,"0")
        app.buttons["settingsDone"].tap()
        XCTAssertTrue(automatic.waitForExistence(timeout: 10))
        XCTAssertEqual(automatic.value as? String,"0")
        attachScreenshot(app,name:"Camera controls - Android parity")
        app.buttons["cameraHistory"].tap()
        XCTAssertTrue(app.buttons["historyDone"].waitForExistence(timeout: 10))
        app.buttons["historyDone"].tap()
        app.terminate(); app.launch()
        XCTAssertTrue(automatic.waitForExistence(timeout: 15))
        XCTAssertEqual(automatic.value as? String,"0")
    }

    func testNativeCameraControlsWithoutHardware() {
        continueAfterFailure = false
        let app = isolatedApp(); app.launchArguments += ["--camera-test-start", "--camera-preview-fixture"]; app.launch()
        XCTAssertTrue(app.switches["cameraAutoCapture"].waitForExistence(timeout: 20))
        XCTAssertTrue(app.buttons["Retry camera"].waitForExistence(timeout: 10))
        attachScreenshot(app,name:"Native camera preview - controls and brackets")
        app.buttons["cameraMore"].tap(); app.buttons.matching(identifier: "cameraSettings").firstMatch.tap()
        XCTAssertTrue(app.switches["autoCapture"].waitForExistence(timeout: 15))
        app.buttons["settingsDone"].tap()
        XCTAssertTrue(app.switches["cameraAutoCapture"].waitForExistence(timeout: 15))
        attachScreenshot(app,name:"Native camera preview - returned from settings")
    }

    func testFreshLaunchStartsWithScanner() {
        continueAfterFailure = false
        let app = isolatedApp(); app.launchArguments.append("--camera-test-start"); app.launch()
        XCTAssertTrue(app.buttons["Choose a photo"].waitForExistence(timeout: 15))
        XCTAssertTrue(app.buttons["Take photo"].exists)
        attachScreenshot(app, name: "First launch - scanner")
    }

    func testRevealHintsPreferencesAndAutomaticSharingConsent() {
        continueAfterFailure = false
        let app = isolatedApp(); app.launchArguments.append("--mock-feedback-service"); app.launch()
        app.buttons["loadExample"].tap(); app.buttons["startPuzzle"].tap(); waitForPuzzleReady(app)
        app.buttons["moreMenu"].tap(); app.buttons["appSettings"].tap()
        XCTAssertTrue(app.switches["explainHints"].waitForExistence(timeout: 10))
        app.switches["explainHints"].tap(); app.switches["shortChains"].tap(); app.switches["autoCapture"].tap()
        for name in ["explainHints", "shortChains", "autoCapture"] { XCTAssertEqual(app.switches[name].value as? String, "0") }
        let automatic = app.switches["automaticSharing"]
        automatic.tap(); XCTAssertTrue(app.buttons.matching(identifier: "confirmAutomaticSharing").firstMatch.waitForExistence(timeout: 5), app.debugDescription)
        app.buttons.matching(identifier: "cancelAutomaticSharing").firstMatch.tap(); XCTAssertEqual(automatic.value as? String, "0")
        automatic.tap(); app.buttons.matching(identifier: "confirmAutomaticSharing").firstMatch.tap(); XCTAssertEqual(automatic.value as? String, "1")
        automatic.tap(); XCTAssertEqual(automatic.value as? String, "0")
        app.buttons["settingsDone"].tap()
        app.buttons["requestHint"].tap()
        XCTAssertTrue(app.staticTexts["hintExplanation"].waitForExistence(timeout: 20))
        XCTAssertFalse(app.buttons["hint-stage-0"].exists)
        attachScreenshot(app, name: "Reveal-only hint")
        app.buttons["requestHint"].tap(); XCTAssertFalse(app.staticTexts["hintExplanation"].exists)
        app.terminate(); app.launch()
        app.buttons["moreMenu"].tap(); app.buttons["appSettings"].tap()
        for name in ["explainHints", "shortChains", "autoCapture", "automaticSharing"] { XCTAssertEqual(app.switches[name].value as? String, "0") }
    }
    func testLargeTextKeepsPuzzleActionsAndStagesReachable() {
        continueAfterFailure = false
        let app = isolatedApp(); app.launchArguments.append("--large-text-fixture"); app.launch()
        XCTAssertTrue(app.buttons["loadExample"].waitForExistence(timeout: 20))
        app.buttons["loadExample"].tap(); app.buttons["startPuzzle"].tap(); waitForPuzzleReady(app)
        XCTAssertTrue(app.buttons["requestHint"].isHittable)
        XCTAssertTrue(app.buttons["showSolution"].isHittable)
        app.buttons["requestHint"].tap()
        XCTAssertTrue(app.buttons["hint-stage-0"].waitForExistence(timeout: 20))
        attachScreenshot(app, name: "Large text - hint and pinned actions")
        let first = app.buttons["cell-0"].frame, last = app.buttons["cell-80"].frame
        XCTAssertLessThan(first.width, 30, "The accessibility font scale must actually reduce the board, not merely set a launch argument.")
        XCTAssertEqual((last.maxX - first.minX) / first.width, 9, accuracy: 0.1)
        XCTAssertTrue(app.buttons["cell-80"].isHittable)
    }

    func testForcingChainContradictionRejectionAndRestart() {
        continueAfterFailure = false
        let app = isolatedApp()
        app.launchArguments += ["--puzzle-fixture", ["8........", "..36.....", ".7..9.2..", ".5...7...", "....457..", "...1...3.", "5.1...368", "..85..91.", ".9....4.."].joined()]
        app.launch()
        XCTAssertTrue(app.buttons["requestHint"].waitForExistence(timeout: 20))
        app.buttons["requestHint"].tap()
        XCTAssertTrue(app.buttons["hint-stage-2"].waitForExistence(timeout: 30))
        app.buttons["hint-stage-2"].tap()
        let branch = app.buttons["Why not 2? · Forcing chain"]
        XCTAssertTrue(branch.waitForExistence(timeout: 20))
        if !branch.isHittable { swipeContentUp(app) }; branch.tap()
        let test = app.buttons["Test assumption"]
        XCTAssertTrue(test.waitForExistence(timeout: 20))
        test.tap()
        XCTAssertTrue(app.staticTexts["hintExplanation"].waitForExistence(timeout: 20))
        attachScreenshot(app, name: "Forcing chain - assumption")
        var rejected = false
        for at in 0..<82 {
            let reject = app.buttons["Reject assumption"]
            if reject.exists {
                if !reject.isHittable { swipeContentUp(app) }
                attachScreenshot(app, name: "Forcing chain - contradiction")
                reject.tap(); rejected = true; break
            }
            let next = app.buttons["Show contradiction"].exists ? app.buttons["Show contradiction"] : app.buttons["Next consequence"]
            XCTAssertTrue(next.waitForExistence(timeout: 10))
            if !next.isHittable { swipeContentUp(app) }
            next.tap()
            if at == 1 {
                let caption = app.staticTexts["hintExplanation"].label
                app.terminate(); app.launch()
                XCTAssertTrue(app.staticTexts["hintExplanation"].waitForExistence(timeout: 20))
                XCTAssertEqual(app.staticTexts["hintExplanation"].label, caption)
                attachScreenshot(app, name: "Forcing chain - restored consequence")
            }
        }
        XCTAssertTrue(rejected)
        XCTAssertTrue(app.staticTexts["hintExplanation"].label.contains("assumption has been rejected"))
        XCTAssertEqual(app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@ AND label CONTAINS %@", "cell-", "hypothetical")).count, 0)
        attachScreenshot(app, name: "Forcing chain - rejected and candidates restored")
    }

    func testPhotographedFourStageHint() {
        continueAfterFailure = false
        let app = isolatedApp(); app.launchArguments.append("--scan-fixture"); app.launch()
        waitForPuzzleReady(app)
        app.launchArguments.removeAll { $0 == "--scan-fixture" }
        app.buttons["requestHint"].tap()
        assertFourStageHint(app)
        attachScreenshot(app, name: "Photographed hint - Answer")
    }

    private func assertFourStageHint(_ app: XCUIApplication) {
        XCTAssertTrue(app.buttons["hint-stage-0"].waitForExistence(timeout: 20))
        XCTAssertFalse(app.buttons["hint-stage-0"].isEnabled)
        XCTAssertFalse(app.staticTexts["hintAnswer"].exists)
        XCTAssertFalse(app.scrollViews["puzzleLegend"].exists)
        attachScreenshot(app, name: "Hint 1 - Where")
        app.buttons["requestHint"].tap()
        XCTAssertFalse(app.buttons["hint-stage-1"].isEnabled)
        XCTAssertFalse(app.staticTexts["hintAnswer"].exists)
        attachScreenshot(app, name: "Hint 2 - Square candidates")
        app.buttons["requestHint"].tap()
        XCTAssertFalse(app.buttons["hint-stage-2"].isEnabled)
        XCTAssertFalse(app.staticTexts["hintAnswer"].exists)
        attachScreenshot(app, name: "Hint 3 - Trace")
        app.buttons["hint-final-proof"].tap()
        XCTAssertTrue(app.staticTexts["hintExplanation"].waitForExistence(timeout: 20))
        attachScreenshot(app, name: "Hint 3 - Placement evidence")
        app.terminate(); app.launch()
        XCTAssertTrue(app.staticTexts["hintExplanation"].waitForExistence(timeout: 20))
        XCTAssertFalse(app.buttons["hint-stage-2"].isEnabled)
        app.buttons["hint-back"].tap()
        XCTAssertTrue(app.buttons["hint-final-proof"].waitForExistence(timeout: 20))
        app.buttons["requestHint"].tap()
        XCTAssertTrue(app.staticTexts["hintAnswer"].waitForExistence(timeout: 20))
        XCTAssertFalse(app.buttons["hint-stage-3"].isEnabled)
        app.buttons["requestHint"].tap()
        XCTAssertFalse(app.buttons["hint-stage-3"].isEnabled)
        app.terminate(); app.launch()
        XCTAssertTrue(app.staticTexts["hintAnswer"].waitForExistence(timeout: 20))
        XCTAssertFalse(app.buttons["hint-stage-3"].isEnabled)
    }

    private func waitForOrientation(_ app: XCUIApplication, landscape: Bool) {
        var last = CGRect.zero
        var changed = Date()
        let stable = NSPredicate { _, _ in
            let frame = app.frame
            if frame != last { last = frame; changed = Date() }
            return (frame.width > frame.height) == landscape && Date().timeIntervalSince(changed) > 1
        }
        expectation(for: stable, evaluatedWith: app)
        waitForExpectations(timeout: 10)
    }

    private func swipeContentUp(_ app: XCUIApplication) {
        let pane = app.scrollViews["puzzleMessages"]
        if pane.exists && pane.isHittable { pane.swipeUp() } else { app.swipeUp() }
    }

    private func assertBoardGeometry(_ app: XCUIApplication, photographed: Bool = false) {
        let first = app.buttons["cell-0"].frame, last = app.buttons["cell-80"].frame
        XCTAssertGreaterThan(first.width, 20)
        XCTAssertGreaterThan(last.width, 20)
        XCTAssertEqual(last.maxX - first.minX, last.maxY - first.minY, accuracy: photographed ? 12 : 2)
        if !photographed {
            XCTAssertEqual((last.maxX - first.minX) / first.width, 9, accuracy: 0.05)
            XCTAssertEqual(first.width, first.height, accuracy: 0.5)
        }
        XCTAssertTrue(app.buttons["cell-80"].isHittable)
    }

    func testReportConsentReceiptRestartAndDeletion() {
        continueAfterFailure = false
        let app = isolatedApp()
        app.launchArguments += ["--scan-review-fixture", "--mock-feedback-service"]
        app.launch()
        openReadingReview(app)
        app.buttons["reportReading"].tap()
        let form = app.scrollViews["reportForm"]
        XCTAssertTrue(form.waitForExistence(timeout: 10))
        let submit = app.buttons["submitReport"]
        for _ in 0..<5 { if submit.isHittable { break }; form.swipeUp() }
        XCTAssertTrue(submit.exists); XCTAssertFalse(submit.isEnabled)
        // SwiftUI may expose a label child instead of its enclosing accessibility ID.
        let labelledConsent = app.buttons.matching(NSPredicate(format: "label == %@ AND identifier != %@", "Submit reading", "reportReading")).firstMatch
        let consent = app.buttons["reportConsent"].exists ? app.buttons["reportConsent"] : labelledConsent
        if !consent.isHittable { form.swipeDown() }
        XCTAssertTrue(consent.isEnabled)
        XCTAssertFalse(consent.isSelected)
        consent.tap()
        XCTAssertTrue(consent.isSelected)
        XCTAssertTrue(submit.isEnabled)
        attachScreenshot(app, name: "Explicit report consent")
        if !submit.isHittable { form.swipeUp() }
        submit.tap()
        let receipts = app.buttons["reportReceipts"]
        for _ in 0..<4 { if receipts.exists && receipts.isHittable { break }; form.swipeUp() }
        XCTAssertTrue(receipts.waitForExistence(timeout: 20))
        receipts.tap()
        XCTAssertTrue(app.staticTexts["receipt-state-accepted"].waitForExistence(timeout: 20))
        app.terminate()
        app.launchArguments.removeAll { $0 == "--scan-review-fixture" }
        app.launch()
        app.buttons["moreMenu"].tap(); app.buttons["appSettings"].tap()
        app.buttons["settingsReceipts"].tap()
        XCTAssertTrue(app.staticTexts["receipt-state-accepted"].exists)
        app.buttons["deleteReport"].tap()
        let confirm = app.buttons.matching(identifier: "confirmReportDeletion").firstMatch
        if confirm.exists { confirm.tap() } else { app.buttons.matching(identifier: "Delete report").element(boundBy: app.buttons.matching(identifier: "Delete report").count - 1).tap() }
        XCTAssertTrue(app.staticTexts["receipt-state-deleted"].waitForExistence(timeout: 20))
        app.terminate(); app.launch()
        app.buttons["moreMenu"].tap(); app.buttons["appSettings"].tap(); app.buttons["settingsReceipts"].tap()
        XCTAssertTrue(app.staticTexts["receipt-state-deleted"].exists)
        XCTAssertFalse(app.buttons["retryReport"].exists)
    }

    func testTutorRouteRestoresPositionAndSolutionDoesNotChangeProgress() {
        let app = isolatedApp(); app.launch()
        app.buttons["loadExample"].tap(); app.buttons["startPuzzle"].tap()
        XCTAssertTrue(app.buttons["checkAnswers"].waitForExistence(timeout: 20))
        let original = app.buttons["cell-2"].label
        let solution = app.buttons["showSolution"]
        if !solution.isHittable { swipeContentUp(app) }; solution.tap()
        XCTAssertTrue(app.staticTexts["solutionLegend"].waitForExistence(timeout: 20))
        if !solution.isHittable { swipeContentUp(app) }; solution.tap()
        XCTAssertEqual(app.buttons["cell-2"].label, original)
        app.buttons["tutorMenu"].tap()
        XCTAssertTrue(app.staticTexts["studyStepCount"].waitForExistence(timeout: 40))
        XCTAssertEqual(app.staticTexts["studyStepCount"].label, "0 / 51")
        XCTAssertTrue(app.staticTexts["studyIntroduction"].exists)
        app.buttons["nextStudyStep"].tap()
        XCTAssertEqual(app.staticTexts["studyStepCount"].label, "1 / 51")
        attachScreenshot(app, name: "Android parity - tutor first step")
        let practice = app.switches["studyPractice"]
        XCTAssertTrue(practice.waitForExistence(timeout: 10))
        practice.tap()
        app.buttons["5"].tap()
        XCTAssertEqual(app.staticTexts["studyStatus"].label, "Tap a square on the grid first.")
        app.buttons["study-cell-40"].tap(); app.buttons["5"].tap()
        XCTAssertTrue(app.staticTexts["studyStatus"].label.contains("Correct."))
        practice.tap()
        app.buttons["nextStudyStep"].tap()
        XCTAssertEqual(app.staticTexts["studyStepCount"].label, "2 / 51")
        let first = app.buttons["study-cell-0"].frame, last = app.buttons["study-cell-80"].frame
        XCTAssertEqual((last.maxX - first.minX) / first.width, 9, accuracy: 0.1)
        attachScreenshot(app, name: "Android parity - tutor route")
        app.terminate(); app.launch()
        XCTAssertTrue(app.staticTexts["studyStepCount"].waitForExistence(timeout: 40))
        XCTAssertEqual(app.staticTexts["studyStepCount"].label, "2 / 51")
        app.buttons["studyDone"].tap()
        XCTAssertEqual(app.buttons["cell-2"].label, original)
        if !app.buttons["tutorMenu"].isHittable { swipeContentUp(app) }
        app.buttons["requestHint"].tap(); app.buttons["hint-stage-2"].tap()
        app.buttons["Follow the full proof in order"].tap()
        XCTAssertTrue(app.staticTexts["studyStepCount"].waitForExistence(timeout: 40))
        XCTAssertTrue(app.buttons["Back to visual hint"].exists)
        app.buttons["studyDone"].tap()
    }

    func testPhotoRecognitionAndReview() { assertPhotoReading(argument: "--scan-fixture") }
    func testPhotoOrientationMetadata() { assertPhotoReading(argument: "--scan-fixture-rotated") }

    func testLanguageChoiceRelocalizesSavedHintAndSurvivesRelaunch() {
        let app = isolatedApp()
        app.launch()
        app.buttons["loadExample"].tap()
        app.buttons["startPuzzle"].tap()
        XCTAssertTrue(app.buttons["requestHint"].waitForExistence(timeout: 20))
        app.buttons["requestHint"].tap()
        XCTAssertTrue(app.buttons["hint-stage-3"].waitForExistence(timeout: 20))
        app.buttons["hint-stage-3"].tap()
        app.buttons["hint-final-proof"].tap()
        XCTAssertTrue(app.staticTexts["hintExplanation"].waitForExistence(timeout: 20))
        app.buttons["moreMenu"].tap(); app.buttons["appSettings"].tap()
        app.buttons["languageSettings"].tap()
        app.buttons["language-fr"].tap()
        XCTAssertTrue(app.navigationBars["Choisir la langue"].waitForExistence(timeout: 5))
        app.buttons["languageDone"].tap()
        attachScreenshot(app, name: "French settings")
        app.buttons["settingsDone"].tap()
        XCTAssertEqual(app.buttons["checkAnswers"].label, "Vérifier")
        XCTAssertFalse(app.staticTexts["hintTechnique"].label.contains("single"))
        XCTAssertFalse(app.staticTexts["hintExplanation"].label.contains("Starting candidates"))
        app.terminate()
        app.launch()
        XCTAssertEqual(app.buttons["checkAnswers"].label, "Vérifier")
        XCTAssertFalse(app.buttons["revealHint"].exists)
        XCTAssertTrue(app.staticTexts["hintExplanation"].exists)
        app.buttons["moreMenu"].tap(); app.buttons["appInfo"].tap()
        let licenses = app.buttons["licenses"]
        for _ in 0..<4 {
            if licenses.isHittable { break }
            swipeContentUp(app)
        }
        XCTAssertTrue(licenses.exists)
        licenses.tap()
        app.buttons["notice-Kotlin-NOTICE"].tap()
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label CONTAINS %@", "JetBrains")).firstMatch.exists)
        attachScreenshot(app, name: "French offline notices")
    }

    func testArabicLayoutKeepsGridColumnOrder() {
        let app = isolatedApp()
        app.launchArguments += ["--app-language", "ar"]
        app.launch()
        XCTAssertEqual(app.buttons["loadExample"].label, "جربها بنفسك")
        let left = app.buttons["cell-0"]
        let right = app.buttons["cell-8"]
        XCTAssertLessThan(left.frame.midX, right.frame.midX)
        XCTAssertTrue(left.label.contains("صف"))
        attachScreenshot(app, name: "Arabic board")
        app.buttons["puzzleHistory"].tap()
        XCTAssertGreaterThan(app.buttons["historyScan"].frame.midX, app.frame.midX)
        attachScreenshot(app, name: "Arabic puzzle drawer")
        app.buttons["historyDone"].tap()
        app.buttons["moreMenu"].tap(); app.buttons["appSettings"].tap()
        app.buttons["languageSettings"].tap()
        app.buttons["language-system"].tap()
        XCTAssertTrue(app.navigationBars["Choose language"].exists)
    }

    func testBulgarianSolverMessages() {
        let app = isolatedApp()
        app.launchArguments += ["--app-language", "bg"]
        app.launch()
        app.buttons["loadExample"].tap()
        XCTAssertEqual(app.buttons["startPuzzle"].label, "Реши")
        app.buttons["startPuzzle"].tap()
        XCTAssertTrue(app.buttons["checkAnswers"].waitForExistence(timeout: 20))
        waitForPuzzleReady(app)
        app.buttons["checkAnswers"].tap()
        XCTAssertFalse(app.staticTexts["puzzleGuidance"].label.contains("Everything"))
        XCTAssertTrue(app.staticTexts["puzzleGuidance"].exists)
        attachScreenshot(app, name: "Bulgarian puzzle")
    }

    private func attachScreenshot(_ app: XCUIApplication, name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    private func isolatedApp() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--history-test-session", UUID().uuidString]
        return app
    }

    func testProgressHistoryAndCurrentPuzzleSurviveRelaunch() {
        let app = isolatedApp()
        app.launch()
        app.buttons["Try an example"].tap()
        app.buttons["Start puzzle"].tap()
        XCTAssertTrue(app.buttons["checkAnswers"].waitForExistence(timeout: 20))
        app.buttons["cell-2"].tap()
        app.buttons["Enter 4"].tap()
        app.terminate()
        app.launch()
        XCTAssertTrue(app.buttons["checkAnswers"].waitForExistence(timeout: 15))
        XCTAssertEqual(app.buttons["cell-2"].label, "Row 1, column 3, 4, entry")
        app.buttons["moreMenu"].tap(); app.buttons["New puzzle"].tap()
        app.buttons["Start new puzzle"].tap()
        app.terminate()
        app.launch()
        XCTAssertTrue(app.buttons["Try an example"].waitForExistence(timeout: 15))
        XCTAssertTrue(app.buttons["cell-2"].label.contains("empty"))
        app.buttons["cell-0"].tap()
        app.buttons["Enter 1"].tap()
        app.terminate()
        app.launch()
        XCTAssertEqual(app.buttons["cell-0"].label, "Row 1, column 1, 1, printed clue")
        app.buttons["puzzleHistory"].tap()
        let example = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", "history-open-")).element(boundBy: 1)
        XCTAssertTrue(example.waitForExistence(timeout: 10))
        let exampleID = example.identifier
        example.tap()
        XCTAssertTrue(app.buttons["checkAnswers"].waitForExistence(timeout: 10))
        XCTAssertEqual(app.buttons["cell-2"].label, "Row 1, column 3, 4, entry")
        app.terminate()
        app.launch()
        XCTAssertTrue(app.buttons["checkAnswers"].waitForExistence(timeout: 15))
        XCTAssertEqual(app.buttons["cell-2"].label, "Row 1, column 3, 4, entry")
        app.buttons["puzzleHistory"].tap()
        app.buttons[exampleID.replacingOccurrences(of: "history-open-", with: "history-delete-")].tap()
        app.buttons["Delete saved puzzle"].tap()
        expectation(for: NSPredicate(format: "exists == false"), evaluatedWith: app.buttons[exampleID])
        waitForExpectations(timeout: 5)
        app.buttons["historyDone"].tap()
        app.terminate()
        app.launch()
        XCTAssertTrue(app.buttons["Try an example"].waitForExistence(timeout: 15))
        XCTAssertTrue(app.buttons["cell-2"].label.contains("empty"))
        app.buttons["puzzleHistory"].tap()
        XCTAssertFalse(app.buttons[exampleID].exists)
        XCTAssertEqual(app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", "history-open-")).count, 1)
    }

    func testScanReviewSurvivesRelaunch() {
        let app=isolatedApp();app.launchArguments.append("--scan-review-fixture");app.launch()
        openReadingReview(app)
        app.terminate();app.launchArguments.removeAll { $0 == "--scan-review-fixture" };app.launch()
        XCTAssertTrue(app.buttons["reportReading"].waitForExistence(timeout:15))
        app.buttons["cell-2"].tap();app.buttons["Enter 4"].tap();finishCellEditing(app)
        XCTAssertTrue(app.buttons["reviewReading"].waitForExistence(timeout:10))
        XCTAssertFalse(app.buttons["reportReading"].exists)
        app.terminate();app.launch()
        XCTAssertTrue(app.buttons["reviewReading"].waitForExistence(timeout:15))
        XCTAssertEqual(app.buttons["cell-2"].label,"Row 1, column 3, 4, entry")
        app.buttons["reviewReading"].tap()
        XCTAssertTrue(app.buttons["reportReading"].waitForExistence(timeout:10))
        XCTAssertFalse(app.buttons["startPuzzle"].exists)
        assertBoardGeometry(app,photographed:true)
    }

    func testConfidentPhotoOpensAsPuzzleWithoutForcedReview() {
        let app=isolatedApp();app.launchArguments.append("--scan-fixture");app.launch()
        waitForPuzzleReady(app)
        XCTAssertFalse(app.buttons["reviewReading"].exists)
        XCTAssertFalse(app.buttons["reportReading"].exists)
        XCTAssertTrue(app.buttons["cell-0"].label.contains("5"))
        attachScreenshot(app,name:"Confident photograph - Android normal entry")
        app.buttons["readPuzzle"].tap()
        XCTAssertFalse(app.buttons["reportReading"].exists)
        XCTAssertFalse(app.buttons["reviewReading"].exists)
        app.buttons["cell-2"].tap();XCTAssertTrue(app.staticTexts["cellReading"].waitForExistence(timeout:5));finishCellEditing(app)
        XCTAssertFalse(app.buttons["startPuzzle"].exists)
        app.terminate();app.launchArguments.removeAll { $0 == "--scan-fixture" };app.launch()
        waitForPuzzleReady(app)
        XCTAssertFalse(app.buttons["reviewReading"].exists)
        XCTAssertFalse(app.buttons["reportReading"].exists)
    }

    private func openReadingReview(_ app: XCUIApplication) {
        let ready=NSPredicate { _,_ in
            app.buttons["reportReading"].exists || (app.buttons["reviewReading"].exists && app.buttons["checkAnswers"].isEnabled)
        }
        expectation(for:ready,evaluatedWith:app);waitForExpectations(timeout:60)
        if !app.buttons["reportReading"].exists { app.buttons["reviewReading"].tap() }
        XCTAssertTrue(app.buttons["reportReading"].waitForExistence(timeout:10))
    }

    private func assertPhotoReading(argument: String) {
        let app=isolatedApp();app.launchArguments.append(argument);app.launch()
        waitForPuzzleReady(app)
        XCTAssertTrue(app.buttons["cell-0"].label.contains("5"))
        XCTAssertTrue(app.buttons["cell-80"].label.contains("9"))
        XCTAssertFalse(app.buttons["reviewReading"].exists)
        XCTAssertFalse(app.buttons["reportReading"].exists)
        XCTAssertFalse(app.buttons["startPuzzle"].exists)
    }

    func testExampleCheckingAndStagedHint() {
        let app = isolatedApp()
        app.launch()
        app.buttons["Try an example"].tap()
        app.buttons["Start puzzle"].tap()
        let status = app.staticTexts["puzzleGuidance"]
        waitForPuzzleReady(app)

        app.buttons["cell-2"].tap()
        app.buttons["Enter 1"].tap(); finishCellEditing(app)
        app.buttons["checkAnswers"].tap()
        let incorrect = NSPredicate(format: "label CONTAINS %@", "A red square")
        expectation(for: incorrect, evaluatedWith: status)
        waitForExpectations(timeout: 15)

        app.buttons["cell-2"].tap(); app.buttons["Erase"].tap(); finishCellEditing(app)
        app.buttons["requestHint"].tap()
        assertFourStageHint(app)
        app.buttons["closeHint"].tap()
        XCTAssertFalse(app.buttons["hint-stage-0"].exists)
        app.buttons["requestHint"].tap()
        XCTAssertTrue(app.buttons["hint-stage-0"].waitForExistence(timeout: 20))
        XCTAssertFalse(app.buttons["hint-stage-0"].isEnabled)

    }
}
