# Auralis 0.3.0 — audit and validation

Preserves application ID `app.auralis`, local debug signing identity, SAF references and user data. No destructive migration or library rebuild.

## Causes and fixes

1. **Different percentages:** `LibraryBook.fraction` formerly switched to unique coverage when listening segments were available, while the player displayed timeline position. Home/Library now label position; book details labels position and unique coverage separately. Completion remains based on unique listened ranges.
2. **Chapter displays:** raw playlist index, persisted position and chapter-title strings could be read on different update cycles. One MediaController snapshot is now projected into the active library entry, with media-ID chapter resolution, immediate event updates and periodic progress ticks. Mini-player includes the numerical chapter count as well as source title. A source file can include an introduction before Chapter I, so its title numeral need not equal its playlist ordinal; the source title is retained.
3. **Stale coverage:** a mutable computed field was absent from data-class equality. Flow equality now includes computed unique coverage so segment updates cannot be suppressed.
4. **Heatmap:** 26 old columns already existed, but phones initially showed only about 14 oldest weeks and no date labels. The calendar now generates six actual calendar months, Monday-aligned into 26–28 weeks, shows exact range/month/weekday/legend labels and opens at the recent end of the horizontal scroll area. The stored local listening dates are used without converting them to a different current time zone.
5. **Short durations:** rounded decimal hours have been replaced by explicit duration units. Clock timestamps remain limited to playback positions.
6. **Rapid switching:** load now drains queued checkpoints before reading saved progress. The dedicated A→B→A device regression verifies the seek is preserved in the controller, database, mini-player and full player.
7. **Chapter checks:** completion indicators use unique content ranges in the current progress epoch. Merely seeking beyond a chapter cannot check it off. Shared range calculations clamp to known chapter bounds.

## Delivery checklist

- [x] Shared authoritative timeline and separate coverage; regression tests
- [x] Calendar range, labels, legend, recent viewport; charts and duration formatting
- [x] Design tokens; Home/library redesign; editable series migration and detail
- [x] Player/mini-player refinements, smart rewind and large-control car mode
- [x] Trophy room and rank progression
- [x] Wrapped stories and high-resolution exports with privacy confirmation
- [x] Profile/settings separation and history screen
- [x] Debug/minified unsigned release builds, unit/device/adaptive checks and migration validation

## Verification

Executed on Linux with JDK 17, API 36 SDK and the pinned existing dependency versions. Android verification uses a separate `app.auralis.verification` installation; personal imports and history are not test fixtures.

- 33 domain/parser regression tests: accounting, speeds, seeking, pauses/stalls, overlap, completion, midnight/DST/time-zone handling, streaks, achievement idempotence/progress, report period/privacy, calendar completeness, playback projection and rewind thresholds.
- 15 Android tests: real WAV import/background playback, speed/pause accounting, sleep timer, bookmarks/backup round trip, removal behavior and checkpoint ordering, v1→v4/v3→v4 migrations, four-destination navigation, rapid-switch consistency, phone/tablet player controls, 150% font scale, activity calendar, distinct badge semantics and exact exported bitmap dimensions.
- Forced 1400×876 dp Tab S8 Plus-like landscape and 360×720 dp phone at 150% fonts; device is Android phone / Android 16.
- Android Lint: zero errors. Dependency-update and existing style/version warnings remain; this release does not change the Android toolchain.
- APK signing certificate matches the existing 0.2.1 debug APK. Version code 4 / version name 0.3.0.
- Before upgrade, app data was saved privately outside source/distribution archives. After the actual 3→4 upgrade, all original database rows/columns were compared, including books, file URIs, chapters, bookmarks, sessions, segments, collections/memberships, achievements, sources, completions and challenges. Playback chapter/position/speed were identical; last-open timestamps may advance normally. Integrity and foreign-key checks passed. No history was deleted.

Commands:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug
./gradlew :app:assembleVerification :app:assembleVerificationAndroidTest -PauralisTestBuildType=verification
./gradlew :app:connectedVerificationAndroidTest -PauralisTestBuildType=verification
```

Full hardware/OEM/Bluetooth testing, exhaustive TalkBack/keyboard testing, huge-library benchmarks and a 30-hour soak remain outstanding. See CHANGELOG_0_3.md and FEATURES.md for remaining capabilities and limitations. Debug signing is retained for updates; a release key is still needed before publishing.

Shared-cover animation follows the [Android Compose shared-transition API](https://developer.android.com/develop/ui/compose/animation/shared-elements); reduced motion uses direct transitions instead.
