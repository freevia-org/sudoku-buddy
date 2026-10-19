# App Store screenshots

Seven portrait captures per family, English UI, captured 19 October 2026:

- iPhone 17 Pro / iOS 27.0: 1206 × 2622.
- iPad Pro 13-inch (M5) / iPadOS 27.0: 2064 × 2752.

01 shows an accepted photo; 02–05 show the four actual hint stages, Where, Square, Trace and Answer; 06 shows Read; 07 shows saved-photo History. Each family stays within Apple's ten-image maximum. The app itself includes 71 UI languages; these are the primary English store assets.

All show actual UI and a synthetic standard Sudoku imported by the real recognizer. No private corpus photo, pretend live camera, fabricated result, frame, caption or decorative overlay is included. The iPhone Read/History captures additionally came from normal launch and Apple's real photo picker, without fixture arguments. UI test captures used isolated history and the synthetic photo fixture; they exercise the same reader and app views. Version 1.0.1 (1), Debug simulator build; Release excludes test entry points.

PNG encoding removes metadata and an opaque alpha channel only. Every exported RGB pixel is compared with its original. Dimensions, hashes, source attachment IDs, device family and capture descriptions are in `manifest.json`. Selected images were visually inspected. Landscape XCTest attachments are excluded: their orientation metadata can misrepresent the captured window. The actual iPad landscape UI was separately verified in Device Hub and through a native simulator screenshot.

To reproduce selection after exporting XCTest attachments, use Python with Pillow:

```sh
python3 tools/ios/prepare-store-screenshots.py \
  --iphone .tools/submission-phone-screens --ipad .tools/submission-ipad-screens \
  --manual-dir .tools
python3 tools/ios/check-store-materials.py
```

The manual directory must contain `iphone-store-read.png`, `iphone-store-history.png`, `ipad-store-read.png` and `ipad-store-history.png`, captured after the corresponding native UI interactions. The selector does not invent missing captures. Upload only the fourteen manifest-listed PNGs, separating iPhone and iPad families; keep the JSON/README as local provenance.
