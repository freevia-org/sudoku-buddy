# Release pipeline integration notes

Updated 10 October 2026. These checks prepare candidates; they do not approve distribution.

The manual Play candidate workflow takes a full 40-character commit SHA and an explicit
version code. It checks the checkout identity, requires the existing signing secrets,
verifies the AAB certificate, and packages evidence with checksums. Code 121 is supported;
confirm it exceeds all uploaded Play codes before using it. The workflow cannot infer
that fact from the repository or GitHub run number.

The integrated manifest contract requires CAMERA and INTERNET for camera processing and
voluntary private reports. Additional permissions still fail the audit. The candidate
workflow now generates all signed APK variants from the AAB, verifies each against the
existing certificate, and checks ZIP alignment with `zipalign -c -P 16`. The three generated
APK evidence reports are included by the existing explicit evidence allowlist. Temporary
signing passwords stay outside the checkout and artifacts and are removed after use.
This does not install APKs, prove 16 KB runtime behavior, or prove Play-delivered signing.

Firebase distribution in CI now requires a manual run on main with
`distribute_firebase=true`; its default is false. Pushes and ordinary manual runs do not
distribute. Local distribution tasks still require separate explicit authorization.

Validation: all 20 release-tool tests passed; both workflow files parsed successfully,
and every shell step passed `bash -n`. These are static checks, not a completed hosted
candidate build. No Gradle build, app upload or publication was performed for these edits.

The integration chat owns the final coordinated commit, push and build. GitHub requires
the manual workflow to exist on the default branch before dispatch; publishing only the
preparation branch does not make a newly added workflow dispatchable. Ensure the default
branch has the opt-in distribution gate before bootstrapping the candidate workflow.
Select the immutable SHA only after the app integration and release tools are committed.

Remaining qualification: successful private-report consent/payload/receipt and deletion
tests; verified retention; matching live privacy policy and Play Data safety declarations;
final signed code-121 tests and certificate evidence; device/accessibility coverage; a
16 KB Android runtime; and Play-delivered installation/upgrade verification. Preserve the
sealed code-120 handoff3 as historical evidence and create a separate code-121 candidate.
