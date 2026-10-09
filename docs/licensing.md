# Licensing

Sudoku Buddy's original source and documentation use Apache License 2.0. See the
root LICENSE and NOTICE. The license does not grant trademark or endorsement rights
to Freevia or Sudoku Buddy branding. Third-party material retains its own terms;
user-submitted puzzle reports are not licensed by this repository.

The Android app bundles full license texts and attribution notices under
`app/src/main/assets/licenses`, accessible offline through About > Licenses.
The asset copy of Sudoku Buddy's NOTICE must match the root NOTICE.

The runtime notice collection was extracted from the AAR/JAR artifacts resolved by
`:app:dependencies --configuration releaseRuntimeClasspath` on 10 October 2026.
`runtime-dependencies.txt` identifies the dependency coordinates; identical upstream
texts are grouped with their artifact owners. Kotlin's upstream NOTICE is included
separately. Build-only tools and test-only OpenCV JVM binaries are not app runtime
components.

OpenCV 4.12.0's arm64 binary build information identifies its native libraries:
CPU features, Protobuf, ADE, TBB, ITT, JPEG, WebP, PNG, TIFF, OpenJPEG, OpenEXR,
Carotene and KleidiCV. Their upstream texts, the LLVM C++ runtime license, and
additional notices from OpenCV's source tree are bundled. Some upstream source
notices cover optional components that are not enabled in this Android build;
including them does not assert those components are shipped. The product notice
includes the Independent JPEG Group acknowledgement.

`sources.json` records the public source of each collected upstream license.
Whenever runtime dependencies or OpenCV change, review this inventory, refresh
applicable license/NOTICE files, and check that the final APK contains the assets.
Do not assume naming a library and license in About replaces distributing its
required texts. Dataset attribution and rights must be preserved separately from
the code license when importing or redistributing training data.
