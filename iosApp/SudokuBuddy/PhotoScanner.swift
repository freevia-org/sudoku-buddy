import UIKit
import CoreImage
import ImageIO
import SudokuShared
import PuzzlePersistence
import GridGeometry

// UIImage is immutable here; it is constructed on the worker and displayed on the main actor.
struct ScannedPuzzle: @unchecked Sendable {
    let givens: [Int]
    let progress: [Int]
    let uncertain: Set<Int>
    let message: String
    let framingNote: String?
    let readerComplaint: String?
    let image: UIImage
    let evidence: RecognitionEvidence
    let locator: String
    let photoData: Data
}

enum ScanFailure: LocalizedError {
    case message(String)
    var errorDescription: String? { if case .message(let text) = self { return text }; return nil }
}

enum PhotoScanner {
    static func read(_ data: Data) throws -> ScannedPuzzle {
        if let source = CGImageSourceCreateWithData(data as CFData,nil),
           let properties = CGImageSourceCopyPropertiesAtIndex(source,0,nil) as? [CFString:Any],
           let width = properties[kCGImagePropertyPixelWidth] as? Int, let height = properties[kCGImagePropertyPixelHeight] as? Int,
           width > 0, height > 0, width <= Int(Int32.max), height <= Int(Int32.max) {
            let sample = Int(CaptureSamplingEngine().sampleSize(width:Int32(width),height:Int32(height)))
            let orientation = properties[kCGImagePropertyOrientation] as? Int ?? 1
            if let decoded = SudokuGridGeometry.decodePhoto(data,orientation:orientation,sample:sample),
               let rgb = decoded["rgb"] as? Data, let w=decoded["width"] as? Int,let h=decoded["height"] as? Int {
                return try readRGB(rgb,width:w,height:h)
            }
        }
        guard var image = CIImage(data: data, options: [.applyOrientationProperty: true]) else {
            throw ScanFailure.message("That photo could not be opened. Try another photo.")
        }
        let sample = Int(CaptureSamplingEngine().sampleSize(width: Int32(image.extent.width), height: Int32(image.extent.height)))
        if sample > 1 {
            let width = ceil(image.extent.width / CGFloat(sample)), height = ceil(image.extent.height / CGFloat(sample))
            image = image.transformed(by: CGAffineTransform(scaleX: width / image.extent.width, y: height / image.extent.height))
        }
        image = image.transformed(by: CGAffineTransform(translationX: -image.extent.minX, y: -image.extent.minY))
        let context = CIContext(options: [.cacheIntermediates: false])
        let fullWidth = Int(image.extent.width), fullHeight = Int(image.extent.height)
        let sourceRGB = renderRGB(image, width: fullWidth, height: fullHeight, context: context)
        return try readRGB(sourceRGB,width:fullWidth,height:fullHeight)
    }
    private static func readRGB(_ sourceRGB: Data,width fullWidth:Int,height fullHeight:Int) throws -> ScannedPuzzle {
        let aligner=IosRuleAligner(width:Int32(fullWidth),height:Int32(fullHeight),rgb:sourceRGB)
        guard let located = SudokuGridGeometry.locateRGB(sourceRGB, width: fullWidth, height: fullHeight,ruleAligner: { corners in
            let text=corners.flatMap { $0 }.map { $0.stringValue }.joined(separator:",")
            let values=aligner.align(corners:text).split(separator:",").compactMap { Double($0) }
            return stride(from:0,to:values.count,by:2).map { [NSNumber(value:values[$0]),NSNumber(value:values[$0+1])] }
        }),
              let pixels = located["rgb"] as? Data, let gray = located["gray"] as? Data, let side = located["side"] as? Int else {
            throw ScanFailure.message("Point the camera at a sudoku puzzle.")
        }
        let corners = (located["corners"] as? [[NSNumber]])?.flatMap { $0 }.map { $0.stringValue }.joined(separator: ",") ?? ""
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("--pixel-probe") {
            let root=FileManager.default.urls(for:.documentDirectory,in:.userDomainMask)[0].appendingPathComponent("PixelProbe")
            try FileManager.default.createDirectory(at:root,withIntermediateDirectories:true)
            try sourceRGB.write(to:root.appendingPathComponent("source.rgb"))
            try "\(fullWidth) \(fullHeight)".write(to:root.appendingPathComponent("source.size"),atomically:true,encoding:.utf8)
            try JSONSerialization.data(withJSONObject:located.filter { !($0.value is Data) },options:.sortedKeys).write(to:root.appendingPathComponent("ios.json"))
        }
        #endif
        let note = PhotoFramingEngine().note(width: Int32(fullWidth), height: Int32(fullHeight), corners: corners)
        if (located["curved"] as? Bool) != true {
            let rectified=IosScanner().rectify(width:Int32(fullWidth),height:Int32(fullHeight),rgb:sourceRGB,corners:corners)
            #if DEBUG
            if ProcessInfo.processInfo.arguments.contains("--pixel-probe") {
                let root=FileManager.default.urls(for:.documentDirectory,in:.userDomainMask)[0].appendingPathComponent("PixelProbe")
                try rectified.rgb.write(to:root.appendingPathComponent("rectified.rgb"))
                try rectified.luminance.write(to:root.appendingPathComponent("rectified.gray"))
            }
            #endif
            return try reading(rectified.rgb,side:side,framingNote:note.isEmpty ? nil : note,locator:"opencv",luminance:rectified.luminance)
        }
        if let surface=located["surface"] as? [NSNumber],surface.count==30 {
            let rectified=IosScanner().rectifySurface(width:Int32(fullWidth),height:Int32(fullHeight),rgb:sourceRGB,coefficients:surface.map { $0.stringValue }.joined(separator:","))
            return try reading(rectified.rgb,side:side,framingNote:note.isEmpty ? nil : note,locator:"cell-surface",luminance:rectified.luminance)
        }
        return try reading(pixels,side:side,framingNote:note.isEmpty ? nil : note,locator:"cell-surface",luminance:gray)
    }
}

private extension PhotoScanner {
    static func renderRGB(_ image: CIImage, width: Int, height: Int, context: CIContext) -> Data {
        var rgba = [UInt8](repeating: 0, count: width * height * 4)
        rgba.withUnsafeMutableBytes { bytes in
            context.render(image, toBitmap: bytes.baseAddress!, rowBytes: width * 4,
                           bounds: CGRect(x: 0, y: 0, width: width, height: height), format: .RGBA8,
                           colorSpace: CGColorSpace(name: CGColorSpace.sRGB))
        }
        var rgb = Data(count: width * height * 3)
        rgb.withUnsafeMutableBytes { bytes in
            let out = bytes.bindMemory(to: UInt8.self)
            for i in 0..<(width * height) { out[i * 3] = rgba[i * 4]; out[i * 3 + 1] = rgba[i * 4 + 1]; out[i * 3 + 2] = rgba[i * 4 + 2] }
        }
        return rgb
    }
    static func reading(_ rgb: Data, side: Int, framingNote: String? = nil, locator: String, luminance: Data? = nil) throws -> ScannedPuzzle {
        let scanner = IosScanner()
        let result = luminance.map { scanner.readRectified(width: Int32(side), height: Int32(side), rgb: rgb, luminance: $0, colorAvailable: locator != "cell-surface") }
            ?? scanner.read(width: Int32(side), height: Int32(side), rgb: rgb, verifiedGeometry: locator != "vision")
        guard result.status == "review" else { throw ScanFailure.message(result.message) }
        var display = rgb
        if let luminance {
            display = Data(count: side * side * 3)
            display.withUnsafeMutableBytes { target in
                luminance.withUnsafeBytes { source in
                    let src = source.bindMemory(to: UInt8.self), dst = target.bindMemory(to: UInt8.self)
                    for i in 0..<(side * side) { dst[i*3] = src[i]; dst[i*3+1] = src[i]; dst[i*3+2] = src[i] }
                }
            }
        }
        guard let provider = CGDataProvider(data: display as CFData),
              let cg = CGImage(width: side, height: side, bitsPerComponent: 8, bitsPerPixel: 24, bytesPerRow: side * 3,
                               space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.none.rawValue),
                               provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent),
              let evidence = try? JSONDecoder().decode(RecognitionEvidence.self, from: Data(result.evidenceJson.utf8)), evidence.isValid else {
            throw ScanFailure.message("That photo could not be opened. Try another photo.")
        }
        let photo = UIImage(cgImage: cg)
        guard let jpeg = photo.jpegData(compressionQuality: 0.95) else { throw ScanFailure.message("That photo could not be opened. Try another photo.") }
        return ScannedPuzzle(givens: result.givens.map { $0.wholeNumberValue ?? 0 }, progress: result.progress.map { $0.wholeNumberValue ?? 0 },
                             uncertain: Set(result.uncertainCells.split(separator: ",").compactMap { Int($0) }), message: result.message, framingNote: framingNote, readerComplaint: result.readerComplaint.isEmpty ? nil : result.readerComplaint,
                             image: photo, evidence: evidence, locator: locator, photoData: jpeg)
    }
}
