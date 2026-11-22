# Implementation checklist

Status reflects source behavior and verification, not promised future work.

## Phase 1 — Foundation

- [x] Native Android Studio project, Kotlin DSL and version catalog; API 26 minimum.
- [x] Compose, Material 3, Navigation Compose, Hilt, Room, DataStore, Coroutines/Flow, Coil and WorkManager.
- [x] Original charcoal/amber design, light/dark and optional wallpaper colors.
- [x] Four primary destinations, adaptive navigation rail, responsive grids, insets, empty states and mini-player.
- [x] Five approved adaptive launcher icons with themed vector layers, persisted alias switching and an Appearance gallery.
- [x] Official system splash integration, approved-monogram cold-launch sequence, reduced-motion/static path and warm/playback reentry bypass.
- [x] Launcher icon; offline core, no Auralis analytics or account requirement (optional Cast SDK diagnostics are documented).

## Phase 2 — Import and playback

- [x] SAF individual/multiple-file and recursive folder import, lasting URI grants and remembered folders.
- [x] Metadata, duration, sampled embedded artwork, natural filename order, duplicate URI detection and import review.
- [x] Separate files as books or group them; editable review titles.
- [x] MP3, M4B/M4A, AAC, FLAC, OGG/OPUS and WAV when supported by Android/Media3.
- [x] Multi-file chapter playlists and embedded Nero `chpl` M4B chapters.
- [x] Rescan and add new chapters to an identified existing book; preserve chapter identity/position when sorting changes.
- [x] MediaSessionService background playback, system media controls, audio focus and headphone disconnection handling.
- [x] Periodic position/speed saves and cold-start restoration when opening Auralis.
- [x] Missing-file errors and explicit relinking.
- [ ] Other embedded chapter formats (QuickTime text chapter tracks, ID3 CHAP/CTOC, Vorbis chapter tags).
- [ ] Arbitrary drag-and-drop regrouping, reordering and per-file relinking for ambiguous renamed multi-file books.
- [ ] Content fingerprint deduplication across distinct URIs and automatic moved-file matching.

## Phase 3 — Library

- [x] Books/Series/Authors/Collections browsing, editable series/number/edition, ordered series detail.
- [x] Search, grid/list, status/favourite/collection filters, title/author/duration/date/last-played/coverage sorting.
- [x] Favourites, custom collections, metadata and cover editing.
- [x] Expandable summaries and attributed Apple Books edition details, automatically looked up when opening a book online and cached locally.
- [x] Continue listening, recently added/played, unfinished shelves, local author-based suggestions.
- [x] Unique listened coverage, manual completion/unfinished state, reset preserving statistics.
- [x] Confirmed book removal from details, including loaded-player cleanup; keeps source audio and lifetime statistics.
- [ ] Collection rename/removal, broader storage-management UI and bulk operations.
- [x] Tablet library/detail split pane alongside adaptive rail and grids.

## Phase 4 — Player

- [x] 0.3.0 shared cover transition, compact mini-player, current-chapter scrolling, optional smart rewind, header swipe dismissal and large-control car mode.
- [x] 0.2.0 artwork-tinted player, animated transport/artwork/chapter transitions, bottom sheets, refined typography and floating mini-player; see docs/PREMIUM_UI.md.

- [x] Full player and mini-player, previous/next, configurable 10/15/30/60-second skip across chapter boundaries.
- [x] Chapter and whole-book seek; content and speed-adjusted remaining time, finish-clock estimate.
- [x] 0.5–3× speed in 0.05 increments; global default and remembered per-book speed.
- [x] Sleep presets, custom duration, chapter end, 5-minute extension, cancel and fade.
- [x] Exact-position bookmarks, titles/notes, search/edit/delete/jump.
- [ ] Cross-book queue and silence skipping.
- [ ] Mirror configurable skip intervals into system notification controls (currently 30 seconds there; player UI uses the selected interval).
- [ ] Full OEM/Bluetooth/long-duration interruption certification and system playback-resumption callback after service destruction.

## Phases 5–6 — Statistics and motivation

- [x] Actual elapsed/content time, speed, date, book/chapter/session, idempotent event persistence and unique-range completion.
- [x] Daily/weekly/monthly/yearly/lifetime totals, average/longest sessions, starts/completions/time saved and average speed.
- [x] Interactive 7-/30-day, 12-month and calendar-year bars; accessible textual totals.
- [x] Labeled six-month scrolling calendar initially focused on recent weeks, goal/chart axes/grid lines and natural duration units.
- [x] Six-month activity calendar with five intensity levels, earlier/later navigation and day details.
- [x] Custom daily goals, configurable streak threshold, longest/current/qualifying days, local midnight/DST handling.
- [x] Original medal gallery, locked/unlocked details and measured achievement progress; rank emblems/progression, compact profile and separate Settings.
- [x] Seven configurable listening levels and all twelve initial achievement rules with immutable unlock times.
- [x] Optional monthly/annual books/hours challenges, custom names and retained challenge history.
- [ ] Free-form date ranges/manual challenge types, achievement tiers and richer milestone celebration animations.
- [ ] Optional rest days, streak freezes, reminders/notification preferences (no reminder notifications are scheduled).
- [ ] Indexed aggregate statistics queries for multi-million-segment histories; current dashboard observes full history.

## Phase 7 — Reports and insights

- [x] Monthly/annual report dialog and privacy-confirmed PNG export.
- [x] Real listening totals, completed books, most heard book, longest period streak, pace, saved time and simple rule-based personality.
- [x] Most heard author/genre/narrator and most active hour/weekday; missing-data explanations.
- [x] Eight-page monthly/annual Wrapped, report-specific author/genre, privacy-confirmed 1080×1920 story / 1080×1350 summary PNGs, and month/year comparison totals.
- [ ] Report-specific most-active-month story and elaborate illustration timelines.

## Phase 8 — Reliability and polish

- [x] Versioned backup/restore, transactional merges, explicit URI relinking and original-media preservation.
- [x] Room migration chain and schema exports; debug APK; meaningful unit/device tests.
- [x] Tablet-sized and larger-font Compose device tests; accessible labels and Material touch targets.
- [x] Goal progress animation with reduced-motion preference; weekly stale cover-cache maintenance.
- [ ] Full TalkBack/keyboard audit, split-screen/OEM/tablet hardware matrix, 30-hour soak and thousand-file benchmarks.
- [ ] Release signing/publishing and independent security/backup-format fuzz review.
- [ ] Further repository separation, pagination and aggregate-query optimization before a large-scale production release.

## Phase 9 — Separate integrations

- [x] Optional Google Cast sender with local ranged audio streaming, a branded route sheet and receiver volume controls where supported (see [Cast setup and limitations](docs/CAST.md)).
- [ ] Verify chapter rollover, seeking and transcript synchronization during a long Cast session on multiple receiver models; receiver playback may continue while the app's chapter UI falls behind.
- [ ] Cross-device sync, Android Auto browsing and advanced voice commands.

See docs/FUTURE.md for extension boundaries. Core app playback does not depend on these integrations.

## Listening detail update — 0.6.0

- [x] Scoped Goals / Insights / Calendar redesign; original Listening landing page preserved.
- [x] Actual-data goal/streak/rank rings, editable targets, calendar day summaries and monthly/annual reports.
- [x] 56 JVM tests and 10 isolated device checks passed. See [validation and limits](docs/LISTENING-JOURNEY-0.6.0.md).
