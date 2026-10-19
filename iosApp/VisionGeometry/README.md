# Native grid geometry

This package ports the Android geometry pipeline in `core/vision` to Objective-C++.
Swift gets RGB pixels, a side length, a geometry score and the surface-warp flag;
digit inference and puzzle acceptance remain in the shared Kotlin reader.

`Geometry.hpp` follows `QuadDetector`, `GridLocator`, `GridScorer` and `CellGrid`:
outline proposals at 1000/1600px, strict required-line scoring, contour growth,
long-rule alignment, cell-lattice fitting, quartic surface fitting and final
one-line occlusion allowance. Preview detection uses a cheaper outline pass and
never infers digits. A rectangle alone never triggers automatic capture.

OpenCV 4.12.0 is a pinned binary target with a checked SHA-256. Its distribution
URL and checksum match the maintainer's
[versioned manifest](https://github.com/yeatse/opencv-spm/blob/4.12.0/Package.swift).
It contains iOS device, simulator and macOS slices. The application bundles the
upstream license notices and required API declarations.

```sh
swift test --package-path iosApp/VisionGeometry
SUDOKU_CORPUS_ROOT="$PWD" swift test --package-path iosApp/VisionGeometry
```

The second command explicitly requests all 118 local media fixtures and fails
closed on missing input. Corpus media are not shipped, committed or uploaded.
`docs/recognition/ios-native-geometry.json` records detection outcomes; the actual
iOS importer/reader replay is separate and includes recognition refusals/errors.

When Android geometry changes, update this port and rerun both corpus benchmarks.
Different JPEG/color decoders, resampling and floating-point implementations can
produce different cell readings. The corpus overlaps classifier training; results
are regression evidence, not independent accuracy or physical-camera qualification.
