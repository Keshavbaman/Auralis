# Auralis

**Your books. Your world.** A native, offline Android audiobook player built with Kotlin and Jetpack Compose. The current project version is **0.7.31** (version code 53). Auralis includes local audiobook import and playback, background MediaSession support, Chromecast streaming, transcript support, PDF reading, library organization, listening statistics, goals, achievements and backups. See [FEATURES.md](FEATURES.md) for implementation status and known limitations; not every item in the original master specification is complete.

**Try the Android beta:** [Download Auralis 0.7.31](https://github.com/Keshavbaman/Auralis/releases/latest) · [Project page](https://keshavbaman.github.io/Auralis/). The APK is a sideloadable community build, not a Play Store release.

See the [0.3.0 changelog](docs/CHANGELOG_0_3.md), [validation checklist](docs/UPDATE_0_3.md), and [approved branding / launcher / launch update](docs/BRANDING.md).

## Open and build on Linux

The project root is this directory. Open it in Android Studio and let Gradle sync. Use JDK 17 (Android Studio's configured Gradle JDK or your system JDK). Minimum Android: 8.0 / API 26. Compile and target: API 36, the newest installed SDK supported by this pinned toolchain.

```bash
# Debian/Ubuntu, if Java is absent:
sudo apt install openjdk-17-jdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
# Install Android SDK Command-line Tools through Android Studio's SDK Manager first.
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
./gradlew assembleDebug
```

The Gradle wrapper downloads Gradle 8.14.3. Dependencies need internet for the first build; core playback works offline. Optional Google Cast uses network access and Google Play services to discover receivers and stream the active book over Wi-Fi.

The version catalog pins stable versions compatible with AGP 8.13.2 and API 36. Newer Compose releases require API 37 / AGP 9, and newer Hilt releases require AGP 9. Upgrading them together is a separate toolchain change, rather than mixing incompatible releases.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. The optimized daily-use build is `app/build/outputs/apk/optimized/app-optimized.apk`.

## Install on a phone or tablet

Enable Developer options → USB debugging, connect the device and approve its debugging prompt:

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n app.auralis/.MainActivity
```

Alternatively, transfer the APK to the device and open it, allowing installation from your chosen file manager. Debug builds are signed with your local Android debug key.

Tap **+** on Home or Library. Choose audio files, or a folder containing chapter files or book subfolders. Review titles and grouping, then add the books. Files stay in their original storage location. The app uses persistable SAF URI grants, not unrestricted storage permission. Some cloud or temporary file providers do not support durable offline access; choose a local document provider for offline listening.

Open a book and choose **Start listening**. The mini-player opens the full player. Its controls provide chapter seeking, whole-book seeking, speed, bookmarks, and a service-owned sleep timer. The You dashboard links to goals, trophy room, ranks, personal challenges, history and dedicated Settings. Settings contains playback, appearance, accessibility, storage and backup. Book details provides metadata editing, cover selection, collections, progress reset and audio relinking.

Choose a launcher design in **You → Settings → Appearance → App icon**. Signature is the default; Classic, Horizon, Pulse and Quill use the approved concept artwork. Icon selection is saved without reinstalling. The opening draws the approved A once on a cold launch, with a static reduced-motion alternative. Tap the Cast control to discover speakers on the same Wi-Fi; the branded speaker sheet exposes receiver volume when the device supports remote volume control.

To remove a book, open its details → trash icon → **Remove book**. Audio files and listening history are kept. See [book removal and testing](docs/BOOK_REMOVAL.md).

## Architecture

- `data/`: normalized Room entities and DAOs, schema migrations, DataStore preferences, versioned JSON backups, weekly orphan-cover maintenance via WorkManager.
- `importer/`: document traversal, metadata and bounded artwork extraction, natural chapter sorting, review/commit transactions, bounded ISO-BMFF Nero `chpl` parser.
- `playback/`: Media3 ExoPlayer and MediaSessionService. The UI connects through a MediaController. The service owns playlists, audio focus, noisy-output behavior, timers and statistics checkpoints.
- `domain/`: activity-independent listening accounting, unique-range merging, local-date streaks, configurable levels and deterministic achievements.
- `ui/`: Compose/Material 3 screens and ViewModel flows, adaptive bottom navigation/rail, library grid/list, player, interactive bar charts and activity calendar.

Room schemas are committed in `app/schemas/`. Migrations 1→2 preserve books while adding a progress reset epoch; 2→3 adds challenges; 3→4 adds series/number/edition metadata without deleting data. No destructive migration fallback is used.

## Accounting rules

Actual listening time uses a monotonic clock and advancing audio positions. Pauses, stalls, large timing gaps and seek callbacks reset or reject anchors. Content consumed is a separate position delta. Statistics at 2× therefore distinguish elapsed time from content time. Adjacent samples coalesce before persistence. Checkpoints occur approximately every ten seconds of playback and on pause, seek, speed changes and orderly shutdown. An abrupt process kill can lose the final uncommitted interval; it cannot duplicate already committed segments.

Segments split at local midnight using the recorded IANA time zone. Historic dates retain the zone used while listening. A zone or wall-clock change drops the ambiguous interval instead of guessing. A streak may end yesterday while today is still in progress. Threshold edits recalculate streaks without deleting history.

Completion uses the union of listened content ranges, not the furthest seek position. Automatic completion requires 98% coverage. Resetting progress starts a new coverage epoch and retains lifetime listening history. Completed-book counts are unique books; relistening does not fabricate an additional book completion. Manually completed books are explicitly distinguished in metadata. Time saved is content time minus elapsed time, floored at zero. Average speed is total content time divided by actual elapsed listening time. A Speed Listener qualifying session requires at least one real minute at 2× or faster.

## Backup and privacy

Settings exports a versioned JSON file through SAF. It includes book/file references, chapters, positions, sessions, segments, bookmarks/notes, achievements, completions, collections, sources, challenges and preferences. It excludes audio and cached cover images. Restore merges immutable segment and achievement IDs transactionally, so importing the same backup twice does not duplicate history. Matching metadata and positions take backup values. Audio URIs may need relinking after device migration. Use **Relink audio** in book details; single-file books can be linked to a renamed replacement, and multi-file books currently match filenames.

When you open an audiobook online, Auralis can search Apple Books with the book’s title and author. It reads embedded summaries from supported audiobook files and checks the written-edition catalog when the audiobook listing has no summary. Matching online details are cached on the device. You can turn off these requests in **Settings → Storage and imports → Automatic book details**. Catalog details are separate from the information stored for your local recording.

There is no account, advertising or runtime broad-storage permission. Auralis adds no app analytics. Optional Google Cast includes Google’s SDK, which can send its own diagnostics after you open Cast; it is not initialized for ordinary offline listening. Android cloud backup and automatic device transfer are excluded; exports are explicitly user-selected. Wrapped export asks for confirmation and excludes private titles, covers, author and genre unless selected.

## Verification

```bash
./gradlew testDebugUnitTest lintDebug
# Requires an unlocked connected Android device or emulator:
./gradlew connectedVerificationAndroidTest -PauralisTestBuildType=verification
```

The verification build has its own application ID and does not touch the installed personal library. Import/removal tests require that isolated build.

Unit tests exercise shared playback projection, calendar coverage, explicit duration units, report privacy/periods, badge progress, smart rewind, real/content time, pause/buffering, seeks, overlaps, speed changes, midnight, DST, zone changes, streaks, achievement determinism, natural ordering and MP4 chapter parsing. Instrumentation exercises real imported WAV playback through the service with the activity stopped, full-player entry, chapter-end sleep, pause accounting, persisted chapter position, bookmarks, duplicate-safe backup restore, Room migrations, navigation, a 1400×876 dp Tab S8 Plus-like layout, and 150% text scaling. Synthetic audio exists only in tests; no demo books or fabricated statistics ship in the application.

See [docs/VALIDATION.md](docs/VALIDATION.md) for the actual run results and limits, and [docs/RELEASE.md](docs/RELEASE.md) for signing and release preparation.

## Optimized daily-use build

`./gradlew :app:assembleOptimized` builds the R8-optimized, non-debuggable APK, signed with the same local development certificate for in-place testing. This is the recommended local daily-use build; retain debug builds for development. See [0.4.0 validation and performance notes](docs/LISTENING-0.4.0.md).
