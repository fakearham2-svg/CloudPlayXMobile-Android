# Repair notes for the supplied archive

This archive is a native Android Kotlin prototype. It currently provides read-only Google Drive v3 and WebDAV backends, an in-app remote browser, an Android `DocumentsProvider`, and a disk-backed 4 MiB block cache. It does **not** contain a TeraBox backend, archive extractor, game installer, game launcher, or general-purpose mounted filesystem.

## Changes made in this repair pass

- Hardened WebDAV XML parsing against DTD/external-entity expansion.
- Added WebDAV document-path traversal checks and made base-path stripping boundary-aware.
- Added validation for block-cache construction and destination byte-array ranges.
- Made cache block writes flush to disk before the temporary file is renamed into place.
- Added a long-press action in the built-in browser to save a cloud file to a user-selected Android Storage Access Framework destination. This streams in 1 MiB chunks and displays progress; a failed transfer can leave a partial destination file, which the UI warns about.
- Removed a CI step that rewrote `MainActivity.kt` during builds. CI now runs unit tests and lint before assembling the APK and verifies that the APK exists.
- Added common Gradle/build artifacts to `.gitignore`.

## Verified scope and limitations

No Android SDK is installed in the repair environment, and this archive has no Gradle wrapper scripts/JAR. Therefore a local Android APK build, Android lint, device tests, and live Google OAuth/Drive tests could not be run here. GitHub Actions is configured to build the checked-out source and run tests/lint, but those checks remain **unverified until an Actions run completes**.

The Google Drive Android OAuth client must still be configured in Google Cloud Console for the exact package `app.cloudplayx.mobile` and the signing certificate used for the APK. The WebDAV password is currently stored in ordinary app-private SharedPreferences, not encrypted preferences; use HTTPS for remote servers and avoid credentials over untrusted HTTP networks.

Game compatibility remains limited: Android-compatible apps that can read a SAF `content://` document may be able to open a cloud file. Most games require ordinary local files or app-private directories and cannot consume a virtual cloud file directly. Downloading a file to a user-selected location is supported after this repair; extraction, game-specific import, installation and launching workflows are not implemented.


## Follow-up UI and Google authorization feedback update (2026-10-29)

- Refreshed the dashboard heading, section numbering, connection description, status panel and Android document-picker button label. This is a native programmatic UI refresh; it is not a Compose migration or a fully new screen architecture.
- Removed the raw `Google auth result: resultCode=..., dataNull=...` diagnostic from the user-facing screen. Cancelled sign-in, missing result data and other result codes now produce distinct recovery messages.
- This does not by itself prove Google OAuth works. The signing SHA-1, Google Cloud configuration and real-device authorization/API calls still need testing.
