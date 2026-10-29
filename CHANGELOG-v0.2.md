# Changelog — repair pass after v0.2

- Hardened WebDAV XML parsing against external-entity expansion and added path traversal validation.
- Validated cache configuration and destination buffer ranges; synced cache blocks before atomic rename.
- Cleared cached Google bearer tokens after HTTP 401 responses.
- Added a user-selected local file copy action to the in-app browser, with chunked writes and progress reporting.
- Updated GitHub Actions to run unit tests and Android lint before assembling and verifying the debug APK from checked-out source.
- Added build artifacts to `.gitignore` and documented remaining OAuth, HTTP/WebDAV, and game compatibility limitations.

# Previous v0.2 changes

# CloudPlayX Mobile v0.2 source update

- Replaced the plain setup form with a dark dashboard layout and grouped connection/cache panels.
- Added an in-app browser with folder navigation and file opening via content URIs.
- Added a button to open Android's system document picker for checking `DocumentsProvider` visibility.
- Fixed Google authorization result handling to retain the access token returned from the permission flow.
- Changed the status label so a selected backend is not falsely described as connected before a real test succeeds.
- Added concise Google API HTTP error details to help diagnose OAuth/scope/API enablement failures.
- Clarified that a `DocumentsProvider` is not a system-wide mounted drive and may not appear inside every OEM file manager.

This is a source-only revision; the APK was not built in this environment because an Android SDK and downloadable Gradle dependencies were unavailable.
