# Book removal — Auralis 0.2.1

Open a book's details and tap the trash icon at the top right, then **Remove book** in the confirmation dialog. Cancel leaves the book untouched.

Removal deletes that book's library metadata, chapter/file references, saved position, bookmarks/notes and collection memberships. It keeps the audio files in their original location, the collections themselves, and lifetime listening sessions, segments, completions and earned achievements. The book can be imported again later. Unreferenced cached covers are handled by the existing maintenance worker.

If the removed book is loaded in the player, the service saves the final listening interval, stops that book, cancels its sleep timer and clears the queue. Already queued checkpoints finish before database deletion so that a late save cannot recreate progress. Load/remove operations are serialized. Deleting a different book leaves the current player intact. The delete session command is available only to the app's UID. Navigation returns to the library only after successful deletion; failures remain visible with retry/cancel available.

## Validation

Two device tests passed in a separate `app.auralis.verification` installation:

- Confirmation/cancel flow, deletion of related references, preservation of a second book, original audio and historical listening records.
- Removal of a loaded paused book after 20 queued seeks/checkpoints, emptying its player queue and remembered last-book reference, and checking that progress does not reappear.

The tests never remove books from the user's normal installation and do not start audio playback. The existing 23 JVM tests pass, Android Lint has zero errors, and debug/minified release builds pass.

To repeat only these tests in an isolated installation:

```bash
./gradlew -PauralisTestBuildType=verification \
  -Pandroid.testInstrumentationRunnerArguments.class=app.auralis.BookRemovalTest \
  :app:connectedVerificationAndroidTest
```

These tests are skipped on the normal debug installation. This feature removes library entries; it does not delete source audio from device storage.
