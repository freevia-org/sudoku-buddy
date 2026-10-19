import CoreGraphics
import XCTest
@testable import PuzzlePersistence

final class CaptureGateTests: XCTestCase {
    func testAspectFillMapsCroppedImageEdgesOutsidePreview() {
        let point = CaptureGate.onScreen(.zero, frame: CGSize(width: 1600, height: 900), preview: CGSize(width: 400, height: 800))
        XCTAssertLessThan(point.x, 0); XCTAssertEqual(point.y, 0)
    }
    func testExplicitNullEvidenceFieldsRoundTrip() throws {
        let data = Data(#"{"cell":0,"roleUncertain":false}"#.utf8)
        let reading = try JSONDecoder().decode(CellRecognition.self, from: data)
        let encoded = try JSONEncoder().encode(reading)
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: encoded) as? [String: Any])
        XCTAssertTrue(json["digit"] is NSNull); XCTAssertTrue(json["confidence"] is NSNull)
        let correction = ReadingCorrection(cell: 0, oldDigit: nil, newDigit: 4, oldSource: "EMPTY", newSource: "GIVEN")
        let encodedCorrection = try JSONEncoder().encode(correction)
        XCTAssertEqual(try JSONDecoder().decode(ReadingCorrection.self, from: encodedCorrection), correction)
        XCTAssertTrue((try JSONSerialization.jsonObject(with: encodedCorrection) as? [String: Any])?["oldDigit"] is NSNull)
    }
}
