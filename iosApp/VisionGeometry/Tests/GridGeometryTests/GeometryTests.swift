import XCTest
import CoreImage
import ImageIO
import GridGeometry

final class GeometryTests: XCTestCase {
    func testBlankAndOrdinaryRectangleAreNeverSudoku() {
        let width = 800
        var pixels = Data(repeating: 255, count: width * width * 3)
        XCTAssertNil(SudokuGridGeometry.locateRGB(pixels, width: width, height: width))
        XCTAssertNil(SudokuGridGeometry.frameRGB(pixels, width: width, height: width))
        let frame = SudokuGridGeometry.framingRGB(pixels, width: width, height: width)
        XCTAssertEqual(frame?["accepted"] as? Bool, false)
        XCTAssertEqual((frame?["luminance"] as? Data)?.count, width * width)
        XCTAssertEqual(frame?["gridSide"] as? Int, 0)
        for y in 100..<700 { for x in 100..<700 where x < 104 || x >= 696 || y < 104 || y >= 696 {
            for channel in 0..<3 { pixels[(y * width + x) * 3 + channel] = 0 }
        } }
        XCTAssertNil(SudokuGridGeometry.locateRGB(pixels, width: width, height: width))
        XCTAssertNil(SudokuGridGeometry.locateRGB(Data([1,2]), width: 100, height: 100))
    }
    func testFindsGridInsideLargerPageAndKeepsPixelOrder() throws {
        let width = 1000
        var pixels = Data(repeating: 248, count: width * width * 3)
        for line in 0...9 {
            let position = 140 + line * 80
            for at in 140...860 { for delta in -2...2 {
                for channel in 0..<3 {
                    pixels[((position + delta) * width + at) * 3 + channel] = 0
                    pixels[(at * width + position + delta) * 3 + channel] = 0
                }
            } }
        }
        let found = try XCTUnwrap(SudokuGridGeometry.locateRGB(pixels, width: width, height: width))
        XCTAssertGreaterThanOrEqual(try XCTUnwrap(found["score"] as? Double), 0.35)
        XCTAssertEqual((found["rgb"] as? Data)?.count, 1152 * 1152 * 3)
        XCTAssertEqual((found["gray"] as? Data)?.count, 1152 * 1152)
        let corners = try XCTUnwrap(SudokuGridGeometry.frameRGB(pixels, width: width, height: width))
        XCTAssertEqual(corners.count, 4)
        let frame = try XCTUnwrap(SudokuGridGeometry.framingRGB(pixels, width: width, height: width))
        XCTAssertEqual(frame["accepted"] as? Bool, true)
        XCTAssertEqual(frame["gridSide"] as? Int, 1152)
        XCTAssertEqual((frame["gridLuminance"] as? Data)?.count, 1152 * 1152)
        XCTAssertEqual(corners[0][0].doubleValue, 0.14, accuracy: 0.02)
        XCTAssertEqual(corners[0][1].doubleValue, 0.14, accuracy: 0.02)
    }
    func testSourceLuminanceIsConvertedBeforePerspectiveWarp() throws {
        let width=801, height=1103
        var rgb=Data(count:width*height*3), grayRGB=Data(count:width*height*3)
        for y in 0..<height { for x in 0..<width {
            let line=(0...9).contains { abs(x-(75+$0*70))<=2 || abs(y-(170+$0*70))<=2 }
            let onGrid=x>=73 && x<=707 && y>=168 && y<=802
            let r=line && onGrid ? 20 : 200+x%45
            let g=line && onGrid ? 10 : 210+y%40
            let b=line && onGrid ? 15 : 180+(x+y)%65
            let at=(y*width+x)*3, luma=(299*r+587*g+114*b)/1000
            rgb[at]=UInt8(r);rgb[at+1]=UInt8(g);rgb[at+2]=UInt8(b)
            for c in 0..<3 { grayRGB[at+c]=UInt8(luma) }
        } }
        let color=try XCTUnwrap(SudokuGridGeometry.locateRGB(rgb,width:width,height:height))
        let gray=try XCTUnwrap(SudokuGridGeometry.locateRGB(grayRGB,width:width,height:height))
        XCTAssertEqual(color["gray"] as? Data,gray["gray"] as? Data)
        XCTAssertEqual(color["corners"] as? [[NSNumber]],gray["corners"] as? [[NSNumber]])
    }
    func testCanonicalCodecKeepsRGBOrderAndAppliesExifQuarterTurn() throws {
        let bytes=Data([255,0,0, 0,255,0, 0,0,255, 255,255,0, 0,255,255, 255,0,255])
        let provider=try XCTUnwrap(CGDataProvider(data:bytes as CFData))
        let image=try XCTUnwrap(CGImage(width:2,height:3,bitsPerComponent:8,bitsPerPixel:24,bytesPerRow:6,space:CGColorSpaceCreateDeviceRGB(),bitmapInfo:CGBitmapInfo(rawValue:0),provider:provider,decode:nil,shouldInterpolate:false,intent:.defaultIntent))
        let encoded=NSMutableData()
        let destination=try XCTUnwrap(CGImageDestinationCreateWithData(encoded,"public.png" as CFString,1,nil))
        CGImageDestinationAddImage(destination,image,nil);XCTAssertTrue(CGImageDestinationFinalize(destination))
        let normal=try XCTUnwrap(SudokuGridGeometry.decodePhoto(encoded as Data,orientation:1,sample:1))
        XCTAssertEqual(normal["rgb"] as? Data,bytes)
        let rotated=try XCTUnwrap(SudokuGridGeometry.decodePhoto(encoded as Data,orientation:6,sample:1))
        XCTAssertEqual(rotated["width"] as? Int,3);XCTAssertEqual(rotated["height"] as? Int,2)
        XCTAssertEqual(rotated["rgb"] as? Data,Data([0,255,255, 0,0,255, 255,0,0, 255,0,255, 255,255,0, 0,255,0]))
        XCTAssertNil(SudokuGridGeometry.decodePhoto(Data([1,2,3]),orientation:1,sample:1))
    }
    func testFullLocalCorpusGeometry() throws {
        guard let root = ProcessInfo.processInfo.environment["SUDOKU_CORPUS_ROOT"] else { throw XCTSkip("Local corpus not supplied") }
        let context = CIContext(options: [.cacheIntermediates: false])
        var rows: [[String: Any]] = []
        for folder in ["corpus", "corpus-rectified"] {
            let directory = URL(fileURLWithPath: root).appendingPathComponent(folder)
            for file in try FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil).sorted(by: {$0.lastPathComponent < $1.lastPathComponent})
                where ["jpg", "jpeg", "png", "webp"].contains(file.pathExtension.lowercased()) {
                let result: [String: Any] = autoreleasepool {
                    guard var image = CIImage(contentsOf: file, options: [.applyOrientationProperty: true]) else { return ["photo": file.lastPathComponent, "found": false, "decode": false] }
                    let edge = max(image.extent.width, image.extent.height)
                    if edge > 2400 { image = image.transformed(by: CGAffineTransform(scaleX: 2400 / edge, y: 2400 / edge)) }
                    image = image.transformed(by: CGAffineTransform(translationX: -image.extent.minX, y: -image.extent.minY))
                    let width = Int(image.extent.width), height = Int(image.extent.height)
                    var rgba = Data(count: width * height * 4)
                    rgba.withUnsafeMutableBytes { bytes in context.render(image, toBitmap: bytes.baseAddress!, rowBytes: width * 4, bounds: CGRect(x: 0, y: 0, width: width, height: height), format: .RGBA8, colorSpace: CGColorSpace(name: CGColorSpace.sRGB)) }
                    var rgb = Data(count: width * height * 3)
                    rgba.withUnsafeBytes { source in rgb.withUnsafeMutableBytes { target in
                        let src = source.bindMemory(to: UInt8.self), dst = target.bindMemory(to: UInt8.self)
                        for i in 0..<(width * height) { for c in 0..<3 { dst[i*3+c] = src[i*4+c] } }
                    } }
                    let found = SudokuGridGeometry.locateRGB(rgb, width: width, height: height)
                    return ["photo": file.lastPathComponent, "folder": folder, "found": found != nil, "score": found?["score"] ?? 0, "curved": found?["curved"] ?? false]
                }
                rows.append(result)
                let progress = URL(fileURLWithPath: root).appendingPathComponent(".tools/native-geometry-corpus.json")
                try JSONSerialization.data(withJSONObject: rows, options: [.prettyPrinted, .sortedKeys]).write(to: progress, options: .atomic)
                print("CORPUS \(rows.count): \(file.lastPathComponent) \(result["found"] ?? false)")
            }
        }
        let output = URL(fileURLWithPath: root).appendingPathComponent(".tools/native-geometry-corpus.json")
        try JSONSerialization.data(withJSONObject: rows, options: [.prettyPrinted, .sortedKeys]).write(to: output)
        XCTAssertEqual(rows.count, 118)
        XCTAssertEqual(rows.filter { ($0["found"] as? Bool) == true }.count, 118, "See .tools/native-geometry-corpus.json for failures")
    }
}
