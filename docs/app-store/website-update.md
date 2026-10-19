# Website publication draft

The live product page and policy responded with HTTP 200 on 19 October 2026. The page currently advertises Android only; the Help link points to the repository's `.github/SUPPORT.md`. Keep the existing Play link. Before the iOS listing goes live:

- Publish the reviewed cross-platform source from `../privacy-policy.md` to the existing privacy URL.
- Replace “It needs an Android phone with a camera” with “Scan with a compatible iPhone, iPad or Android device, or choose a saved photo. Scanning, checking and hints work offline.”
- Replace “free Android app” with “free Sudoku companion for iOS and Android” once iOS is actually available.
- Add the real App Store product link only after App Store Connect assigns it; no guessed app ID or coming-soon purchase link is included here.
- Keep Android captures labeled Android, and add the approved iOS captures separately.
- Add iOS support instructions: camera permission in Settings, importing through Choose a photo, local History/current-puzzle restoration, optional report consent, Settings > Submission receipts for per-revision deletion, and private email support at info@freevia.org.
- Verify the support and data-deletion anchors against the final published page. The landing-page link currently uses `privacy#uninstalling`, but the published policy has `id="deleting-your-data"`. Change the link to `privacy#deleting-your-data`.

No website was changed by this preparation. Final website text must reflect the actual release state rather than announcing an unavailable iOS app.
