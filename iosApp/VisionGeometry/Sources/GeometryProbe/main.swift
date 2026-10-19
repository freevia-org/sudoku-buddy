import Foundation
import CoreImage
import ImageIO
import GridGeometry

// Offline geometry bridge diagnostics. Inputs/outputs are local files, never app assets.
let args = CommandLine.arguments
 guard args.count >= 3 else { fatalError("Usage: GeometryProbe image output-directory [maximum-edge]") }
func decodePhoto(_ url:URL)->[String:Any]? {
    guard let data=try? Data(contentsOf:url),let source=CGImageSourceCreateWithData(data as CFData,nil),
          let properties=CGImageSourceCopyPropertiesAtIndex(source,0,nil) as? [CFString:Any],
          let w=properties[kCGImagePropertyPixelWidth] as? Int,let h=properties[kCGImagePropertyPixelHeight] as? Int,w>0,h>0 else { return nil }
    var sample=1
    while ((w+sample-1)/sample)*((h+sample-1)/sample)>12*1024*1024 { sample*=2 }
    return SudokuGridGeometry.decodePhoto(data,orientation:properties[kCGImagePropertyOrientation] as? Int ?? 1,sample:sample)
}
let input = URL(fileURLWithPath: args[1]), output = URL(fileURLWithPath: args[2], isDirectory: true)
try FileManager.default.createDirectory(at: output, withIntermediateDirectories: true)
let width: Int, height: Int
var rgb: Data
if input.pathExtension == "rgb" {
    let dimensions = try String(contentsOf: input.deletingLastPathComponent().appendingPathComponent("source.size")).split(whereSeparator: { $0.isWhitespace }).map { Int($0)! }
    width=dimensions[0];height=dimensions[1];rgb=try Data(contentsOf:input)
    guard rgb.count == width*height*3 else { fatalError("Invalid RGB size") }
} else if args.count==3,let decoded=decodePhoto(input),let pixels=decoded["rgb"] as? Data,let w=decoded["width"] as? Int,let h=decoded["height"] as? Int {
    width=w;height=h;rgb=pixels
} else {
guard var image = CIImage(contentsOf: input, options: [.applyOrientationProperty:true]) else { fatalError("Cannot decode photo") }
if args.count > 3, let maximum = Double(args[3]) {
    let scale = min(1,maximum/max(image.extent.width,image.extent.height))
    image = image.transformed(by:CGAffineTransform(scaleX:scale,y:scale))
}
image = image.transformed(by:CGAffineTransform(translationX:-image.extent.minX,y:-image.extent.minY))
width=Int(image.extent.width);height=Int(image.extent.height)
let context=CIContext(options:[.cacheIntermediates:false])
var rgba=Data(count:width*height*4);rgb=Data(count:width*height*3)
rgba.withUnsafeMutableBytes { context.render(image,toBitmap:$0.baseAddress!,rowBytes:width*4,bounds:CGRect(x:0,y:0,width:width,height:height),format:.RGBA8,colorSpace:CGColorSpace(name:CGColorSpace.sRGB)) }
rgba.withUnsafeBytes { source in rgb.withUnsafeMutableBytes { target in
    let a=source.bindMemory(to:UInt8.self),b=target.bindMemory(to:UInt8.self)
    for i in 0..<(width*height) { b[i*3]=a[i*4];b[i*3+1]=a[i*4+1];b[i*3+2]=a[i*4+2] }
} }
}
try rgb.write(to:output.appendingPathComponent("source.rgb"))
try "\(width) \(height)\n".write(to:output.appendingPathComponent("source.size"),atomically:true,encoding:.utf8)
if let result=SudokuGridGeometry.locateRGB(rgb,width:width,height:height) {
    try (result["rgb"] as! Data).write(to:output.appendingPathComponent("native.rgb"))
    try (result["gray"] as! Data).write(to:output.appendingPathComponent("native.gray"))
    let metadata=result.filter { !($0.value is Data) }
    try JSONSerialization.data(withJSONObject:metadata,options:[.sortedKeys,.prettyPrinted]).write(to:output.appendingPathComponent("native.json"))
    print(String(data:try JSONSerialization.data(withJSONObject:metadata,options:[.sortedKeys]),encoding:.utf8)!)
} else { throw NSError(domain:"GeometryProbe",code:1,userInfo:[NSLocalizedDescriptionKey:"No grid located"]) }

for (name, bytes) in SudokuGridGeometry.diagnosticStagesRGB(rgb,width:width,height:height) {
    try bytes.write(to:output.appendingPathComponent(name))
}
