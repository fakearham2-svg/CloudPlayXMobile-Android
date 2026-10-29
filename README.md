# CloudPlayXMobile Android

Native Android Kotlin app for browsing read-only Google Drive and compatible WebDAV servers, with a disk-backed block cache and Android document-provider integration.

## Build APK in GitHub Actions

1. Open the **Actions** tab.
2. Choose **Build CloudPlayX APK** and select **Run workflow**.
3. When the run completes, open it and download the `CloudPlayXMobile-debug-apk` artifact.
4. Extract the artifact ZIP to get `app-debug.apk`, then install it on your Android device.

The workflow runs unit tests and lint before building the debug APK. A committed workflow alone does not mean the APK was built; check the run result.

## Google Drive setup

Package/application ID: `app.cloudplayx.mobile`. Google Drive authorization requires a matching Android OAuth client in Google Cloud Console, the signing certificate SHA-1 for the APK, and the Drive API enabled. Source code changes cannot confirm successful authorization without a device test.

## Scope and limitations

This app supports browsing cloud files and saving selected files locally. It does not implement a full mounted filesystem, TeraBox backend, game installer, or game launcher. Many Android games require local files and cannot read a cloud document URI directly.