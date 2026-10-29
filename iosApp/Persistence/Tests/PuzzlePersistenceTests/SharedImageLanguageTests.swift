import XCTest
@testable import PuzzlePersistence

final class SharedImageLanguageTests: XCTestCase {
    func testExplicitAppLanguageOverridesDeviceAndDeviceChoiceResumesSystemMatching() throws {
        let container = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: container, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: container) }
        let supported = ["en", "de", "ar"]
        try SharedImageLanguage.save(selection: "de", container: container)
        XCTAssertEqual(SharedImageLanguage.resolve(container: container, supported: supported, preferred: ["ar"]), "de")
        try SharedImageLanguage.save(selection: "", container: container)
        XCTAssertEqual(SharedImageLanguage.resolve(container: container, supported: supported, preferred: ["ar"]), "ar")
        try SharedImageLanguage.save(selection: "unsupported", container: container)
        XCTAssertEqual(SharedImageLanguage.resolve(container: container, supported: supported, preferred: ["de-DE"]), "de")
    }
    func testNoSharedContainerFallsBackToDeviceLanguage() {
        XCTAssertEqual(SharedImageLanguage.resolve(container: nil, supported: ["en", "ar"], preferred: ["ar"]), "ar")
    }
}
