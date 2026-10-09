# Signing the release

Every build that reaches a tester must be signed with the same key. Android refuses to
update an installed app whose signature has changed, so a release signed with a different
key each time cannot be updated at all: it arrives as an uninstall, and the tester loses
their puzzles, their photographs and their settings.

That is what was happening. `buildTypes.release` used `signingConfigs.getByName("debug")`,
and a CI runner has no debug keystore — so the Android plugin generated a fresh one on
every run. Two consecutive builds were signed by two different certificates:

    0.1.60   CN=Android Debug   SHA-256 0e467993f0438d83b3ab971bfa3e5f3c…
    0.1.61   CN=Android Debug   SHA-256 8bd7c03cb97d31de9e13c743997c0b22…

## Making a key: historical setup

The app already has a stable key, now also imported into Play App Signing. Do not run
this example to replace it. Keep the existing file and passwords safe: they are needed
for direct Firebase/local updates. Play upload-key recovery is described below.

```bash
keytool -genkeypair -v \
  -keystore sudoku-buddy-release.jks \
  -alias sudokubuddy \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Sudoku Buddy, O=Sudoku Buddy, C=GB"
```

## Telling CI about it

Four repository secrets. The keystore is binary, so it travels base64-encoded:

```bash
base64 -w0 sudoku-buddy-release.jks       # the value for ANDROID_KEYSTORE_BASE64
```

| Secret | What it is |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | the keystore file, base64-encoded |
| `ANDROID_KEYSTORE_PASSWORD` | the store password |
| `ANDROID_KEY_ALIAS` | `sudokubuddy`, if you used the command above |
| `ANDROID_KEY_PASSWORD` | the key password |

The workflow writes the keystore to a temporary file and sets `SIGNING_KEYSTORE` to its
path; `app/build.gradle.kts` picks it up from there.

## What happens without it

A local build still works — it falls back to the debug key, which is what you want on a
development machine. What it will not do is reach anybody: `checkReleaseSigning` runs
before `appDistributionUpload` and `distributeLocal` and fails the job, because a debug-signed release that
testers cannot update is worse than no release at all.

The check also requires the store password, key alias and key password. Local
distribution uses the same native-alignment check and versioned release notes as CI.
Building an APK or bundle by itself still permits debug signing; inspect the certificate
of any candidate intended for Play and run `checkReleaseSigning` explicitly.

That check only started failing once a key existed to use. While there was none it warned
instead, since stopping the upload would have taken away the only route a build had to a
phone — which is a thing to say out loud, not to decide quietly on somebody's behalf.

## The key in use

Set up on 2 September 2026. The keystore and its password live outside the repository, on
the machine that made them; the four secrets are on the repository. What follows is public
- it is the certificate every genuine build carries, and it is the thing to check an APK
against if you ever need to know whether it came from here.

    CN=AI Sudoku, O=AI Sudoku, C=BG
    SHA-256  a5:10:d8:2b:87:e7:06:9b:53:d5:20:be:ca:91:5b:35:2a:e9:6c:ea:85:0c:68:ca:00:61:17:e3:5d:75:d3:e5

The subject retains the app's earlier name. On 8 October 2026, the supplied local
release keystore and the phone's installed build 117 both matched this fingerprint.
Do not generate a replacement merely to change the certificate's display name.

To check a build:

```bash
apksigner verify --print-certs app-arm64-v8a-release.apk
```

Anything reporting `CN=Android Debug` was built without the secrets and cannot be updated
over, whoever built it.

## Historical move from debug signing

The original move from changing CI debug keys to the stable release key required a new
installation. This is historical; do not uninstall current stable-key Firebase builds
for the Play migration. Uninstalling removes their app data.

## Google Play signing

Google holds the *app signing key* used for Play-delivered APKs. On 8 October 2026, the
existing release key was imported with explicit user authorization. The same certificate
is also registered as the *upload key*, which signs the bundles submitted to Play. Local
and Firebase builds continue to use this stable key.

Google can reset the upload key on request without changing Play's app-signing identity.
That does not recover the private key needed for direct Firebase/local updates. Keep
the existing keystore and passwords safe and outside the repository and release handoff.

The configured Play app-signing identity now matches Firebase App Distribution builds.
Verify the certificate and data retention of an actual Play-delivered upgrade before
treating that migration as tested.

### Verified Console migration on 8 October 2026

At approximately 19:43 UTC (22:43 Kyiv), the existing Java-keystore import was saved
using the Console's PEPK encrypted ZIP flow. The downloaded App signing key certificate
was verified directly:

    CN=AI Sudoku, O=AI Sudoku, C=BG
    RSA 4096 bits
    SHA-256 a5:10:d8:2b:87:e7:06:9b:53:d5:20:be:ca:91:5b:35:2a:e9:6c:ea:85:0c:68:ca:00:61:17:e3:5d:75:d3:e5
    SHA-1   41:38:05:79:9d:26:53:e6:2f:f7:75:b9:f5:b6:d8:27:34:51:8b:c4

The Upload key certificate is now registered with the same SHA-256; Digital Asset Links
also matches. This supersedes the earlier `d0:80:a4:a5:...:92:76` observation. The Previous
keys section is no longer shown, and App signing is marked **In use** with no pending
processing. Only the authorized encrypted-key import was performed:
no AAB/APK upload, tester change, rollout or new terms acceptance occurred.

The successful local 117-to-120 upgrade and matching configured certificates do not prove
a Firebase-to-Play upgrade preserves data. The current phone already has 120, so the
later authorized Play test needs a same-key lower-version baseline on an agreed device
or a future higher-code Play build. Verify the delivered certificate and retained history,
photographs, corrections and settings. Do not uninstall or clear data to bypass a mismatch.
