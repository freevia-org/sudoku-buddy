# Preview input scale replay

This replay compares the live-preview locator at the 4:3 CameraX target sizes 720×540 and 960×720. It resizes a center-cropped 4:3 view of each corpus photo with OpenCV `INTER_AREA`, then calls `GridLocator.locate`. A `Found` result maps to `StructuralGate` usable; `NoGrid` maps to rejected. Corner coordinates are normalized to each input image and the maximum corresponding-corner displacement is reported in pixels on a 960×720 frame.

The corpus contained 111 image files. All 111 were processed: 110 decoded directly with JVM ImageIO, and the valid 750×500 `sudoku.webp` was verified with Pillow and converted to a temporary PNG for the two locator calls. SHA-256 content hashing found 84 unique images among the 111 filenames, including 27 exact duplicate groups. The full per-file result is in `full-720-vs-960.csv`; decode handling is in `decode.csv`.

| Input size | Found / usable | NoGrid / rejected |
|---|---:|---:|
| 720×540 | 27 / 111 | 84 / 111 |
| 960×720 | 29 / 111 | 82 / 111 |

The two differing filenames are `0-02-05-c703ab14a107472dae2a19594676e1251c4b8ce92b2420c353a2982428397f8c_cd9618617edca60d.jpg` and `sudoku-buddy-2026-09-04-newsprint-welded-border.jpg`. Both are byte-identical with SHA-256 `96A39333B919EFAC7769C13620267448D1AF69F4200B3D08FEA00FDC00B9946A`. At 720×540 both return NoGrid; at 960×720 both return Found with score 0.3839. There are no Found-at-720 / NoGrid-at-960 cases. After deduplicating exact file content, 720×540 loses one unique found image relative to 960×720.

On 27 cases where both scales found a grid, maximum corner displacement had a median of 0.60 px and nearest-rank p90 of 12.61 px (6 cases exceeded 5 px). The outlier tail means a 720-only configuration is not a safe drop-in replacement despite matching most detections.

Latency was measured separately on 20 stratified decoded photos (7 app photos, 7 app exports, 6 other), with images pre-scaled so timings cover `GridLocator.locate` only. Each photo received one warmup at each size, then three paired repetitions with randomized order. The raw 120 measurements are in `paired-latency.csv`.

| Input size | Samples | p50 | p90 | NoGrid calls | Photos consistently rejected |
|---|---:|---:|---:|---:|---:|
| 720×540 | 60 | 249.2 ms | 454.0 ms | 42 / 60 | 14 / 20 |
| 960×720 | 60 | 353.4 ms | 665.1 ms | 42 / 60 | 14 / 20 |

No timing-subset photo changed outcome between repetitions. The smaller size was about 30% faster at p50 and 32% faster at p90 on this JVM, but missed the one unique photo found at 960×720. This is corpus replay, not a live-device camera benchmark; it does not validate CameraX's ISP scaling, preview crop metadata, frame cadence, or phone-specific performance. No runtime/source change is recommended from this evidence alone.

## Offline fallback-policy study

I deduplicated the 111 rows by source-file SHA-256 before counting policy triggers: 84 unique image contents remain. A policy falls back to 960×720 when the 720×540 locator returns NoGrid, or when a found result has grid score below 0.4, or any normalized corner outside the input frame. This filename-independent rule has zero Found/NoGrid mismatches and leaves zero Found quads more than 5 px from the 960 result on this corpus. It triggers on 77/84 unique images (91.7%); only five unique images need fallback to meet those parity checks (one NoGrid→Found case and four Found/Found corner outliers), leaving 72 additional fallbacks that do not improve the recorded outcome. The mismatch is duplicated under two filenames but has a single SHA-256 image digest.

Simpler rules leave corner errors: fallback on NoGrid alone triggers 66/84 and leaves four unique found quads over 5 px; adding score <0.4 triggers 70/84 and leaves three; adding out-of-frame geometry instead triggers 74/84 and leaves one. The combined rule is the first tested one with zero remaining errors, at a very high fallback rate. These thresholds were selected after reviewing this same corpus, so the apparent perfect parity is in-sample evidence, not an independent validation.

For latency, each of the 20 paired photos contributes its mean of three measured locator calls at each size. The estimated serial fallback cost is the 720 mean plus the 960 mean for each photo the rule triggers. The rule triggers on 17/20 sampled photos. Per-photo p50/p90 estimates are 596.2/1094.4 ms for the rule, versus 352.7/660.0 ms for always 960 and 243.9/458.0 ms for 720 without fallback. Thus the tested safe rule costs more than running 960 directly. The complete threshold study and per-photo estimate are in `policy-results.csv`, `policy-latency-estimate.csv`, and `policy-latency-by-photo.csv`.

No fallback implementation is recommended. The next useful test is a fresh, deduplicated holdout captured by the actual phone, with matched 720×540 and 960×720 preview frames, manual labels for grid presence, and quadrilateral corner review. It should measure trigger rate and latency on those unseen pages before any threshold is promoted.

The full replay ran with:

```text
./gradlew.bat :core:vision:test --tests '*PreviewGridFullScaleAuditTest.replayAllAndPairedLatency' --console=plain --rerun-tasks
```

The WEBP supplement ran with:

```text
./gradlew.bat :core:vision:test --tests '*PreviewGridFullScaleAuditTest.webpSupplement' --console=plain --rerun-tasks
```

Both completed successfully with zero skipped or failed tests. The generated Kotlin test source and converted WEBP PNG are temporary; the source photo corpus was read-only. SHA-256 digests of the saved result files:

```text
full-720-vs-960.csv  C75C0E036E4C4641E8F76D6C01171D28B6C00D61A7A9AF8D1185F0215BD3BC83
paired-latency.csv   078A7F5A8288CAEE4F44F358ECF9DC3F5D12CABAFDF48C8159E458652D426BFE
decode.csv           B3B032B5E47D52750EFB9DAF8850A60E6D3C47B943E39A0AAF0C1530A59C7F06
policy-results.csv   ACF430E5E5101D25F464817F052BBE5AA53A55BDC78454A30478324865B6CBA8
policy-latency-estimate.csv  53B77360E9283DD4D8A46669905E4B07D4348E023157B37A80A523236ED4CEC6
policy-latency-by-photo.csv  6C955A8087AEE304B01A47CBCDFBBE05D75A499CF0FE1D48B6AF41196719E2CA
```
