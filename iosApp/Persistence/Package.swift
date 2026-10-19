// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "PuzzlePersistence",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [.library(name: "PuzzlePersistence", targets: ["PuzzlePersistence"])],
    targets: [
        .target(name: "PuzzlePersistence"),
        .testTarget(name: "PuzzlePersistenceTests", dependencies: ["PuzzlePersistence"])
    ]
)
