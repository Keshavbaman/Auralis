# Release preparation

1. Finish the outstanding release gates in FEATURES.md and test real local files in each advertised format on API 26 and modern Android. Test a 30+ hour book, thousands of files, locked-screen playback, airplane mode, calls/audio focus, Bluetooth/wired controls, process death and failed URI access.
2. Run `./gradlew testDebugUnitTest lintDebug connectedDebugAndroidTest` on the intended revision. Inspect the tests and lint reports under `app/build/reports/`.
3. Verify backup export/restore on a second device; audio grants must be relinked. Cover caches are intentionally excluded from the JSON backup.
4. Generate a private release key, outside the repository:

```bash
keytool -genkeypair -keystore "$HOME/auralis-release.jks" -alias auralis \
  -keyalg RSA -keysize 4096 -validity 10000
```

Keep the key and passwords securely backed up. Do not commit keys/passwords or put passwords into shell history. Configure Android Studio **Build → Generate Signed App Bundle or APK** using the release variant and your key. Alternatively configure a Gradle `signingConfigs` block using environment variables or an untracked keystore-properties file, and assign it to `buildTypes.release`.

5. `./gradlew assembleRelease bundleRelease` validates R8 and produces unsigned release artifacts unless signing has been configured. The debug key must never be used for a public release. Inspect the signed APK with `apksigner verify --verbose <apk>`.
6. Increment `versionCode` for every distributed release. Preserve all committed Room schemas; add and test explicit migrations for future schema changes. Do not use destructive migration fallback.
7. Review merged permissions, dependency licenses, store metadata, privacy description, original artwork/icon provenance and accessibility. The app does not request internet or broad storage access. Explain the user-initiated SAF grants and backup/export behavior accurately.

The source is licensed Apache-2.0; dependencies retain their own licenses. No Audible assets are included. API 36 is deliberately pinned to the available stable toolchain. Reassess target requirements before a store submission; this guide does not claim current store-policy certification.
