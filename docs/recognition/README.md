# Camera recognition evaluation

The benchmark evaluates every labelled corpus image and every labelled cell, including
grid failures, missing ink, unreadable results, incorrect roles and editorial marks.
It also counts unlabelled images for grid detection. A digit classifier's accuracy
on extracted glyphs is reported separately from the app's output grid after repair.

## Reproduce

Run Gradle tasks sequentially: concurrent processes share and can overwrite compiled
JARs while another test JVM is still running.

```powershell
.\gradlew.bat :core:recognize:test --tests '*CorpusBenchmarkTest*' -Ddump=true --rerun-tasks
python tools/recognizer/benchmark.py core/recognize/build/corpus-benchmark.tsv --output docs/recognition/latest.json
python tools/recognizer/test_data_guards.py
```

`baseline.tsv` preserves the first run before grid detection changes. Its grayscale
columns are the recognition baseline; its color columns are an initial experimental
version, not the final color behavior. The final benchmark evaluates grayscale and
color against exactly the same accepted geometry, so their difference isolates the
recognition contribution from any improvement in grid detection.

## Latest replay (10 October 2026)

The fresh replay detected all 110/110 corpus grids, compared with 108/110 in the
baseline. Across the 2,835 labelled cells, color-aware role classification is
2,762/2,835 versus 2,750/2,835 in the baseline (+12 cells); grayscale remains
2,741/2,835. Digit classification is 1,882/1,897, unchanged from baseline. This
supports a measured grid-detection and cell-role improvement on these images; it
does not show independent generalization because the shipped digit model was trained
on the same corpus. Explicit candidate/artifact annotations remain sparse and partial.
Full output is in [`latest.json`](latest.json).

Color is optional and only processed for a captured image. Local paper-relative
chroma reduces sensitivity to warm illumination and colored paper. Colored ink is
separated only where neutral glyphs fit the printed size/position band and the
resulting clue set has a solution. Multiple-solution illustrations still remain
readable and are presented for confirmation. The grayscale path remains available;
color extraction abstains where a curved surface used a non-perspective transform.
The underlying digit weights are unchanged.

The reader caches each glyph's classifier result across its size and ink role passes,
and releases native ink-mask and normalization matrices explicitly. These changes
preserve the normalization and probabilities; device throughput and memory behavior
still need physical measurement.

## Dataset boundaries

There are 110 corpus JPEG images (camera photos, screenshots and illustrations),
35 with digit/role labels, covering 2,835 cells. The other 75 corpus images support
grid detection evaluation but have no digit accuracy ground truth. Seven additional
label files refer to separately rectified images rather than these corpus images.

Most empty-role labels merge blank squares with candidate notes and artifacts. One
visually reviewed booklet photo now includes 30 explicit candidate cells, two blank
cells and six positive erased-artifact overlays. Artifact annotations are partial,
can overlap current digits, and do not define negative examples. The reader's MARK
output does not yet distinguish a candidate list from every kind of artifact.

The shipped digit model was trained using corpus data. These corpus scores therefore
measure regression and pipeline behavior, not independent generalization. The prior
leave-one-photo-out training option can leak another photo of the same puzzle and
hand. `--lopg` now groups exact clue grids before holding them out; all 42 label files
form 27 such groups. Writer-independent validation additionally needs trusted writer
identity annotations and more writers. Group validation has been added but has not
been run or used to retrain the shipped model in this change.

One unlabelled corpus image,
`0-02-05-3c2309bc39761674fc696fed1abdc1d89ec9436d7f340d0b6332481fc818fb42_b2fcb79a0899d6dc.jpg`,
is an app result-screen screenshot with colored overlays, confidence bars and white
recognized digits covering the original photographed digits. It is an explicit
expected-refusal fixture in `GridReaderTest`: a fresh puzzle must not be inferred
from those overlapping annotations. The grid detector still locates its grid. This
does not exempt ordinary photographs or any labelled digit-accuracy assertions;
reading already-annotated result screens remains unsupported. Test reports count
this refusal separately from unexpected failures and excluded acceptance cases.

```powershell
# Re-export whenever extraction sources change. Old vanished-cell files are removed.
.\gradlew.bat :core:recognize:test --tests '*ExportNormalisedTest*' -Ddump=true --rerun-tasks
# Expensive training evaluation, keeping shipped weights unchanged:
python tools/recognizer/train.py --lopg --validation-only
```

Source freshness now checks the actual `org/freevia` analyzer path and upstream grid
and cell extraction sources against the oldest exported bitmap, rather than allowing
one new file to hide a stale export. It fails closed when exports are missing.

Live focus, motion, shutter timing, camera throughput, and recognizer performance on
new puzzles/writers require subsequent physical-device tests. The still corpus does
not establish those results.
