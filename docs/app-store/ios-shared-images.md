# iOS shared-image import — local candidate 1.0.5 (3)

From another app's image share sheet, choose **Sudoku Buddy**, then **Save for
Sudoku Buddy**. Open Sudoku Buddy and choose **Read image**. The app uses the same
photo recognition, correction, refused-photo, feedback-consent and puzzle-history
path as camera/imported photos. The current puzzle is saved first. **Later** keeps
the handoff; **Discard image** deletes it. The extension does not launch the app.

The share extension accepts one image (not web links, text, PDFs or multiple
images), at most 20 MiB / 40 megapixels. A private App Group holds up to five
pending images, excluded from backup with complete file protection. Expired
handoffs are deleted on the next inbox access after seven days. The extension
has no network or feedback code, camera access or Photos-library access. The app's
existing explicit/optional automatic feedback policy applies after recognition.
New share UI currently uses English fallback text.

Both app and extension need the App Group
`group.org.freevia.sudokubuddy.ios`. The new extension bundle ID is
`org.freevia.sudokubuddy.ios.share`. Register these capabilities and regenerate
profiles before signed device installation/export. Existing distribution profiles
for build 2 do not establish this candidate's App Group signing readiness.

Provisioning inspection on 29 October 2026: the signed-in Freevia EOOD team
`8C782Q826S` App Groups list shows **Getting Started with App Groups**, with no
registered groups. The installed Sudoku Store profile
`3e6bbf27-d022-45b6-a458-f60b34a12f2f` (expires 22 October 2027) covers
`8C782Q826S.org.freevia.sudokubuddy.ios`, has no App Group entitlement, no device
list, and debugging disabled. No installed profile covers the share extension.
No Developer portal capabilities or profiles were changed.

Publishing must register the App Group and the extension App ID, enable the group
on both `org.freevia.sudokubuddy.ios` and `org.freevia.sudokubuddy.ios.share`, then
create matching development/ad hoc profiles with the qualification devices (or
App Store profiles for a later authorized TestFlight upload). Regenerate the main
app profile too. Verify signed entitlements on both bundles match the profiles
before device qualification. Do not reuse the old Store profile as proof of this.

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
are resolved. No upload or review submission is authorized by this change.

Implementation references: [Apple share-extension declarations](https://developer.apple.com/library/archive/documentation/General/Conceptual/ExtensibilityPG/ExtensionScenarios.html)
and [required-reason API declarations](https://developer.apple.com/documentation/bundleresources/app-privacy-configuration/nsprivacyaccessedapitypes/nsprivacyaccessedapitypereasons).
