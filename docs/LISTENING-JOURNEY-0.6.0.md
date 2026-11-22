# Auralis 0.6.0 — Goals, Insights and Calendar

This is the scoped listening-detail update toward the Signature Experience. The existing blue Listening landing page is preserved, including its navigation, charts and mini-player. Open **Listening → Goals, calendar & more insights** for the new sections.

## Implemented

- **Goals:** editorial serif headings, three rings for the actual daily goal, seven-day streak milestone and current-rank progress; real listening duration in the centre; compact lifetime/streak/time-saved metrics; next listening rank; seven-day ritual. The editor supports presets, custom daily minutes and a custom streak threshold (1–1440). Changing targets does not delete listening records.
- **Insights:** actual monthly total and a clearly labelled comparison with the full previous month; interactive 7-day, 30-day, rolling 12-month and year-to-date charts; natural duration labels; accessible date-selection slider; actual time/content played/time saved distinguished; weekly/yearly totals, average and longest sessions, book counts, author/genre/narrator patterns; existing monthly and annual Wrapped reports and privacy-aware export.
- **Calendar:** Monday-first month grid, previous/next navigation, future dates disabled, five activity intensities, current-goal indicators, selected-day actual listening, distinct session counts and books heard, and the existing six-month heatmap. Goal comparisons explicitly use the current target because historic target versions were not recorded by the existing preferences store.
- Separate saved scroll states for each detail tab. The wider calendar scrolls horizontally when necessary to retain 48 dp date targets on narrow screens. Layout width is capped on tablets. Functional text uses the existing sans-serif type; feature titles use the platform's offline serif face.
- Restrained ring animation; reduced-motion preferences and Compose's system animation scale apply. Aggregations run on `Dispatchers.Default`, outside navigation and drawing. Reports keep a snapshot while open so incoming playback checkpoints do not reset report pages or export choices.

## Preserved

Application ID, signing configuration, database schema, imported books, manual metadata/artwork, preferences, audio references, history, bookmarks, achievements, Media3 playback, Cast implementation and the original Listening front page. No destructive migration or data reset was introduced.

This does not claim completion of the rest of the broad Auralis 1.0 brief: a full-app theme expansion, trophy artwork overhaul, Reading Room and widgets remain outside this scoped change.

## Verification

The optimized APK builds with vital lint. All **56 JVM tests passed**, including nine new tests for real-time versus accelerated content, duplicate segment IDs, monthly totals across a year boundary, future chart dates, leap February, rank thresholds, quiet-day averages, multi-day sessions, hour boundaries, DST and empty-history behaviour.

Device layout checks cover the new goal editor, actual monthly totals, chart date selection, calendar day details, disabled future-month navigation, a 320 dp phone at 1.5× font scale and a 1200×800 dp tablet/light theme. Device execution results are recorded separately after the run; compiled tests are not counted as passed tests.

## Device execution result

On the connected Android phone (Android 16): **10 instrumentation tests passed in 28.08 seconds**. Six cover the new journey views, three preserve the original Listening dashboard behaviour/layout, and one verifies rapid audiobook switching and consistent playback/seek state. The test copy used an isolated `.verification` application ID, so its fixtures and goal edits did not alter the main library. The 1200×800 dp tablet and 320 dp / 1.5× font-scale cases were forced configurations on this phone, not physical tablet runs.

Installed **0.6.0 / code 14**, signed using the existing optimized build configuration, over the existing main app with `adb install -r`. The optimized app received a visual check of the real-data Insights screen. Temporary test packages were removed. No database schema/migration or playback/Cast implementation changed.

No controlled scrolling frame-time, cold-start or battery benchmark was completed for this release. No numerical performance improvement is claimed; history aggregation remains off the UI thread and no artwork blur, continuous visualization or per-frame database work was introduced. The separate emulator image download was stopped once the physical phone became available.

Validation logs: `docs/validation/journey-0.6.0/`. APK: `dist/Auralis-0.6.0-listening-journey-optimized.apk`.
