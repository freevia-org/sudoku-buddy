# Android / iOS full-corpus parity — 19 October 2026

Both actual importers completed the same **118 local images**: 111 originals and
seven rectified fixtures. **All 118 outputs match** for acceptance/refusal,
printed clues, handwritten answers and review markers. Both accept 117 images
and refuse the existing annotated result screenshot. This is now an actual
Android JNI / iOS simulator comparison, rather than a JVM approximation of Android.

| Measure | Android | iOS |
| --- | ---: | ---: |
| Images reaching review | 117 / 118 | 117 / 118 |
| Correct digit and role, all 42 labeled images | 3,299 / 3,402 (96.97%) | 3,299 / 3,402 (96.97%) |
| Correct role | 3,314 / 3,402 | 3,314 / 3,402 |
| Correct value on digit-bearing cells | 2,161 / 2,187 (98.81%) | 2,161 / 2,187 (98.81%) |
| Original photographs: correct digit and role | 2,769 / 2,835 | 2,769 / 2,835 |
| Rectified fixtures: correct digit and role | 530 / 567 | 530 / 567 |

Results: [Android](android-corpus.json), [iOS](ios-corpus.json),
[per-image Android output](android-corpus.jsonl),
[per-image iOS output](ios-corpus.jsonl), and
[the equivalence check](platform-corpus-parity.json).

## Corrections

The two new digit errors on the ambiguous screen photograph are fixed in both
apps: cell 22 reads 7 and cell 71 reads 5. The original iOS baseline had
3,297 correct digit-and-role cells and 2,157 correct nonempty digits; the
12-megapixel sampling alignment had reduced those to 3,295 and 2,155.
The final common pipeline improves both measures beyond those baselines.

Both apps now use the bundled OpenCV codecs for supported photo formats,
bit-exact detector resizing, integer grayscale conversion before rectification,
and shared Kotlin final perspective/surface sampling, rule alignment, grid-line
fitting, cell masks, glyph reduction and recognition. Curved-page color abstention
and the result-overlay guard are shared. The iOS-only Vision locator fallback
was removed. Geometry adapters retain native contour and lattice primitives.

A detached crossbar previously reached the classifier while role classification
measured only the shortened stem and discarded it. A narrow, confidence-checked
bar/body recovery now runs in both apps. Existing digits, ordinary candidate
notes and erasures keep their original rules. Synthetic regression tests guard
that distinction. No classifier weights, labels or filename-specific production
rules were changed.

Confident unique puzzles now open normally on iOS, matching Android. Open reading
questions still select Read; original predictions and confidence remain available
through explicit review. Framing advice is shared and persists across restart.

## Evaluation boundaries

Every labeled cell is counted, including empty cells; a refused labeled image
counts as 81 errors. Printed givens take precedence where labels also repeat them
in the written block. Completed replays and the comparison reject missing or
duplicate corpus entries and malformed cell arrays.

The 76 unlabeled images establish coverage and output equivalence, not accuracy.
The corpus overlaps model training. These results are regression evidence, not
independent generalization scores. There are still 103 digit-or-role errors on
the labeled set; identical outputs do not make every reading correct. Human
review remains necessary.

The Android API 36 emulator advertises SVE2 without executable SVE support on this
Mac. A separately generated, opt-in Debug wrapper masks that impossible flag for
these native tests. Ordinary and Release builds do not include the wrapper.
Native warp identity and real importer tests pass with that local workaround.

No physical iPhone is available. Optics, autofocus, motion, real-device memory,
thermal behavior and signing/distribution remain unqualified. No corpus image was
uploaded, bundled in the app, or added to Git. Feedback tests use a mock service.

## Reproduce

Build the iOS Debug app and Android Debug plus androidTest APKs, then run:

```sh
python3 tools/android/replay-corpus.py --prepare --collect --hwcap-shim --output .tools/android-corpus.json
python3 tools/ios/replay-corpus.py --device <review-simulator-uuid> --prepare
# Wait for the importer replay to finish, then collect.
python3 tools/ios/replay-corpus.py --device <review-simulator-uuid> --collect --output .tools/ios-corpus.json
python3 tools/ios/compare-platform-replays.py --android .tools/android-corpus.jsonl --ios .tools/ios-corpus.jsonl --output .tools/platform-comparison.json
```

For the affected local emulator only, generate the wrapper with
`tools/android/prepare-emulator-shim.py --ndk <installed-ndk>` and build with the
printed `emulatorHwcapShim` Gradle property. Omit that property and `--hwcap-shim`
on an unaffected Android device. Local private corpus folders are staged in app
storage; Release builds contain no corpus or replay entry points.
