# Desktop-to-Android Google Drive OAuth findings

Reviewed from the supplied CloudPlayX 0.7.0 desktop source ZIP.

## How the working desktop login works

The desktop app does not use Android Google Sign-In. It starts a local rclone remote-control daemon bound only to 127.0.0.1, calls rclone's `config/create` state machine with the Google Drive scope, and lets rclone open Google's official browser consent page. The browser redirects to rclone's loopback listener at 127.0.0.1:53682. rclone then stores the OAuth token (including refresh token) in CloudPlayX's private rclone config. CloudPlayX encrypts that config using rclone's config encryption and protects its encryption key with Windows DPAPI. The desktop supports a user-provided OAuth Desktop client JSON, an optional bundled client, or rclone's shared client.

The source describes two scopes: `drive.readonly` when read-only is selected, otherwise `drive`. It optionally sets a root folder ID. The app verifies the connection with a Drive quota/query call before considering the account connected.

## What must differ on Android

The desktop OAuth client is an **Installed/Desktop** client and its client secret must never be copied into an Android APK. Android needs its own OAuth client of type **Android**, configured for application ID `app.cloudplayx.mobile` and the SHA-1 of the certificate that signs the installed APK. The Google Drive API must be enabled and the Google consent screen/test-user settings must permit the account. A mismatch commonly causes the Android consent/resolution activity to fail or return cancelled.

The current Android prototype uses Google Play services' `Identity.getAuthorizationClient(...).authorize(...)` for the `drive.readonly` scope. It is not using the desktop rclone loopback OAuth flow, and its access-token cache is in memory only. Therefore the PC's persisted refresh-token behavior is not automatically carried over.

## Safe implementation direction

- Keep Android's native authorization flow; do not embed the desktop client secret in the APK.
- Configure the Android OAuth client for the actual package and signing SHA-1 (debug and release certificates may differ).
- Keep tokens in memory and request authorization again when needed, unless a supported secure Android token-storage/refresh design is implemented.
- After authorization, verify the token with an actual Drive API request and show its HTTP/API error rather than treating the consent result alone as a successful connection.
- Test the built APK on-device. A source change alone cannot prove Google OAuth works.
