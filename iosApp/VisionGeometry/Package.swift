// swift-tools-version: 5.9
import PackageDescription
let package = Package(name: "VisionGeometry", platforms: [.iOS(.v16), .macOS(.v13)],
    products: [.library(name: "GridGeometry", targets: ["GridGeometry"])], targets: [
        .binaryTarget(name: "opencv2", url: "https://github.com/yeatse/opencv-spm/releases/download/4.12.0/opencv2.xcframework.zip",
                      checksum: "13409e99956a74aaee1856c56889c211116fbc8be2bcbe373986ec9e10f536fc"),
        .target(name: "GridGeometry", dependencies: ["opencv2"], publicHeadersPath: "include", linkerSettings: [
            .linkedFramework("Accelerate"), .linkedFramework("AVFoundation"), .linkedFramework("CoreImage"),
            .linkedFramework("CoreMedia"), .linkedFramework("CoreVideo"), .linkedFramework("OpenCL", .when(platforms: [.macOS])), .linkedLibrary("c++")])
        , .executableTarget(name: "GeometryProbe", dependencies: ["GridGeometry"])
        , .testTarget(name: "GridGeometryTests", dependencies: ["GridGeometry"])
    ], cxxLanguageStandard: .cxx17)
