# iOS shared-image import — local candidate 1.0.5 (3)

From another app's image share sheet, choose **Sudoku Buddy**, then **Keep**. Open Sudoku Buddy and choose **Read**. The app uses the same
photo recognition, correction, refused-photo, feedback-consent and puzzle-history
path as camera/imported photos. The current puzzle is saved first. **Keep** in the app keeps
the handoff for later; **Delete this photo** discards it. The extension does not launch the app.

The share extension accepts one image (not web links, text, PDFs or multiple
images), at most 20 MiB / 40 megapixels. A private App Group holds up to five
pending images, excluded from backup with complete file protection. Expired
handoffs are deleted on the next inbox access after seven days. The extension
has no network or feedback code, camera access or Photos-library access. The app's
existing explicit/optional automatic feedback policy applies after recognition.
The app uses its selected language and existing catalogs. The extension bundles
an identical subset for all 71 supported locales, including the shared-image label,
existing actions, errors and privacy copy. It follows the app's selected language
via a protected, backup-excluded language-choice file in the App Group; an empty
choice follows the device language. Arabic, Hebrew and Persian use RTL layout.
The extension has a separate process and cannot link the app's SwiftUI/Kotlin
translator; its small Foundation loader reads the same generated translations.
Brand names, numeric limits and SI unit symbols remain language-neutral.

Both app and extension need the App Group
`group.org.freevia.sudokubuddy.ios`. The new extension bundle ID is
`org.freevia.sudokubuddy.ios.share`. On 1 November, Publishing registered the group,
enabled it for both bundle IDs, and generated matching App Store profiles. A signed
1.0.5 (3) archive and IPA were exported; both bundles carry the expected App Group
entitlement and Freevia EOOD distribution signature. The private handoff copy was
verified byte-for-byte. It has not been uploaded to App Store Connect.

Historical provisioning inspection on 29 October 2026: the signed-in Freevia EOOD
team `8C782Q826S` App Groups list showed **Getting Started with App Groups**, with no
registered groups. The installed Sudoku Store profile
`3e6bbf27-d022-45b6-a458-f60b34a12f2f` covered the app but had no App Group entitlement,
and no installed profile covered the share extension. This was superseded on
1 November by the registered group and matching App Store profiles above.

The signed archive's app and extension entitlements were verified against the new
App Store profiles. The signed simulator share-sheet check passed on 1 November:
Keep retained the photo across relaunch, Read recognized the 30-clue fixture and
consumed it, and Delete removed a second shared image. The simulator inbox ended
empty. This does not replace physical-device qualification.

Required physical checks before release:

- Signed installation on iPhone and iPad; extension appears in Photos, Files and
  another app's image/screenshot share sheet, including More/enable-extension.
- Cancel does not enqueue; save survives extension termination; cold/warm app
  launch, Later, Discard and multiple successive shares work without duplicates.
- PNG/JPEG/HEIF, rotated images, oversized/corrupt inputs, locked-device recovery,
  full inbox and expired handoffs; existing puzzle remains in History.
- Recognition/correction/save/relaunch, VoiceOver and large text; no extension
  upload, and app feedback remains governed by existing consent settings.

Apple's 1.0.2 (1) rejection under 4.3.0 Design: Spam remains unresolved. The sent
nine-point response includes the approved external-beta and third-party provenance
facts. This feature does not guarantee approval or establish that Apple's concerns
are resolved. Follow the current release-status record and Publishing handoff for
store actions; this implementation note does not itself authorize an upload or review
submission.

Implementation references: [Apple share-extension declarations](https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/ExtensionScenarios.html)
and [required-reason API declarations](https://developer.apple.com/documentation/bundleresources/app-privacy-configuration/nsprivacyaccessedapitypes/nsprivacyaccessedapitypereasons).
