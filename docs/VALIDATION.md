# Historical validation record — 15 November 2026

This records the checks performed on the 0.3.0-era build on that date. It is retained as a historical baseline and does not certify the current 0.7.31 build; see `FEATURES.md` for current implementation status and known validation gaps.

Environment: Linux, OpenJDK 17, Gradle 8.14.3, AGP 8.13.2, Android SDK 36. Physical device: Android phone, Android 16 / API 36, connected over ADB.

## Results

- Debug APK: built and installed successfully; Auralis launched on the device.
- Minified release APK: built successfully, unsigned. Release signing is intentionally left to the owner; see RELEASE.md.
- JVM unit tests: **23 passed, zero failures** (21 listening/domain cases and 2 MP4 parser cases).
- Device instrumentation tests: **5 passed, zero failures/skips**.
- Android Lint: **zero errors, 21 warnings**. Warnings include newer dependency versions, the intentionally exported media-session service, and minor style/tooling suggestions. This is not a claim of a warning-free build.

Verification command:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew \
  :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest \
  :app:lintDebug :app:connectedDebugAndroidTest
```

## What the device tests actually do

1. Generate quiet test WAV chapter files in the test app storage, import through content URIs, check metadata/duration/natural chapter order and duplicate references, then load the real service using MediaController. Open the mini-player into the full player and verify the visible pause control. Stop the activity while audio continues, verify progressing audio and 2× tracking, pause and check that elapsed totals stop growing, test end-of-chapter sleep, save a bookmark, check persisted chapter position, export a backup and restore it twice without duplicate segments/bookmarks. Test library records and added history are removed afterward; preferences and pre-existing achievements are preserved.
2. Upgrade a Room v1 database through v2 and v3, validate its schema, and verify existing book metadata and the default progress-reset epoch.
3. Navigate all four main destinations and open the visible import choices.
4. Run library/statistics navigation with a 1400×876 dp landscape configuration representative of a Galaxy Tab S8 Plus.
5. Run the phone import flow at 150% font scale.

## Visual inspection

The actual phone Home screen was inspected after installation. Insets, visible import control, goal card, empty state and bottom navigation fit the display. A screenshot is in `screenshots/home-phone.png`. Automated tests also verify the full player can open and its playback control is visible.

## Limits

The tablet test is a Compose device-configuration override on the phone, not a physical Tab S8 Plus test. No API 26 hardware/emulator, Bluetooth headset/call-interruption matrix, multi-window hardware test, 30-hour soak, million-event benchmark, real-world M4B format corpus or exhaustive TalkBack/keyboard audit was run. The MP4 chapter test uses a constructed Nero `chpl` atom tree; the real playback test uses WAV. Do not infer verification of every decoder or chapter format from those tests.

The debug-only fixture FileProvider is excluded from release. Test media and statistics are not bundled as application demonstration data. There are no fabricated dashboard values.

The complete implemented/pending checklist is ../FEATURES.md.
