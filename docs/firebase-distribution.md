# Distributing test builds through Firebase

Product: **Sudoku Buddy**

Android package: **`org.freevia.sudokubuddy`**

Firebase / Google Cloud project ID: **`sudoku-buddy-freevia`**

Project number: **`1032115531436`**

Firebase Android App ID: **`1:1032115531436:android:a1c0f2b6d7edbea2257909`**

Firebase App Distribution is a build-time service only. The application does not include
Firebase SDKs and sends no user data to Firebase. The app's `INTERNET` permission
supports the separate, voluntary private puzzle-report service.

## Required Firebase setup

The project is owned through `info@freevia.org`. Its registered Android app uses package
name `org.freevia.sudokubuddy` and nickname **Sudoku Buddy Android**. App Distribution is
enabled and the `testers` group exists.

The package name is permanent for a Firebase Android registration. Do not reuse a
registration belonging to another package.

`google-services.json` is deliberately not used. App Distribution only needs the
Firebase App ID during the release upload.

## GitHub Actions configuration

The repository needs these Actions secrets:

- `FIREBASE_APP_ID`: the generated App ID for `org.freevia.sudokubuddy`.
- `FIREBASE_TOKEN`: a Firebase CLI refresh token authenticated as `info@freevia.org`.
- The existing Android signing secrets remain unchanged.

Set the values with:

```bash
gh secret set FIREBASE_APP_ID --repo freevia-org/sudoku-buddy
gh secret set FIREBASE_TOKEN --repo freevia-org/sudoku-buddy
```

Pushes and ordinary manual CI runs build without distributing to testers. Distribution
requires an explicitly authorized manual CI run on `main` with the
`distribute_firebase` input enabled (default: false). The distribution job checks
credentials before sending the signed arm64 APK to the `testers` group.

## Distributing from this machine

Authenticate the Firebase CLI as `info@freevia.org`, set the Firebase App ID in the
environment, and run:

```bash
firebase login
FIREBASE_APP_ID=<generated-app-id> ./gradlew :app:distributeLocal
```

On PowerShell:

```powershell
$env:FIREBASE_APP_ID = "<generated-app-id>"
.\gradlew.bat :app:distributeLocal
```

Configure the stable signing key and all three signing variables described in
[signing.md](signing.md), and set `BUILD_NUMBER` to the intended version code. The task
refuses to upload without the Firebase App ID or complete stable signing configuration.
It also checks native alignment and sends release notes headed by the app's actual
version name and code.

## Testers

Manage testers at:

https://console.firebase.google.com/project/sudoku-buddy-freevia/appdistribution

Or add a tester from the CLI:

```bash
firebase appdistribution:testers:add your.email@example.com --project sudoku-buddy-freevia
```

Adding a tester sends an invitation email, so only add addresses that Freevia intends to
invite.

## Retirement of the previous project

Keep the previous Firebase project intact until a signed Sudoku Buddy build has reached
at least one tester through the new project. After that verification, the previous
project can be disabled or deleted separately.
