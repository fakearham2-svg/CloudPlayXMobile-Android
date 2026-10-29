# Build the APK with GitHub Actions (no Android Studio needed on your PC)

1. Sign in to GitHub and create a **new repository**. For a personal prototype, choose **Private** if you do not want to publish the source publicly.
2. Extract this ZIP on your PC. Open the `CloudPlayXMobile` folder.
3. Upload the *contents* of that folder to the repository root (the `app`, `gradle`, `.github`, `build.gradle.kts`, and `settings.gradle.kts` items should be at the repository root, not inside another nested `CloudPlayXMobile` folder). GitHub's **Add file → Upload files** can upload files/folders in the browser; make sure hidden `.github` folder is included.
4. Commit the files to `main` (or `master`). The workflow should start automatically. Alternatively open the repository's **Actions** tab, select **Build CloudPlayX APK**, then **Run workflow**.
5. Open the latest workflow run. Wait for the job to finish. If it fails, open the failed step and copy its error log.
6. If it succeeds, scroll to **Artifacts** at the bottom of the run page and download `CloudPlayXMobile-debug-apk`. Extract that artifact ZIP; it contains `app-debug.apk`. Transfer the APK to your Android 12 device and install it. You may need to allow installation from the browser/file manager you used.

## Important
- This builds a **debug APK**, not a signed Play Store release.
- A successful build does not prove Google Drive authentication or cloud browsing works on-device. Test those features after installing.
- The project currently has no TeraBox provider.
- If GitHub Actions reports a Kotlin/Gradle compile error, open the failed step and share the relevant log so the source can be corrected.
- Do not upload OAuth client secrets, access tokens, passwords, or other private credentials.
