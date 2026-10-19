# Final hardware and distribution checks

These cannot be certified by simulator or unsigned compilation. A tester's iPhone can be used after TestFlight is available; the owner does not need to buy one to finish local preparation.

- Install the exact signed candidate through TestFlight. Verify cold launch, upgrades, restored current puzzle/history/settings and deleting history.
- Exercise camera permission grant/denial/settings recovery, focus, low light, glare, motion, framing, manual/automatic capture, rotation, capture cancellation, background/foreground and interruptions. Scan real printed pages with clues, handwriting and pencil marks.
- Verify Photos import (including HEIF, large/rotated images), share sheet, mail/browser handoff, VoiceOver reading order, largest text, touch targets and sustained memory/thermal behavior.
- On a supported iPad, verify portrait/landscape, split screen and photo/camera presentation.
- With a deliberately non-private puzzle and explicit tester consent, verify production report consent, receipt/retry after network loss, automatic-sharing opt-in/off and per-revision deletion. Simulator tests use a mock service and do not establish live service delivery.
- Validate the signed archive in Xcode/App Store Connect, resolve provisioning/SDK/privacy diagnostics, upload, run TestFlight qualification, select exact build and submit for review.

Record tester, hardware/OS, candidate build, results and any issues. No item above is marked passed by this preparation packet.
