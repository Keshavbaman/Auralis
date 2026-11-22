# Auralis 0.3.1 — Library

Compact library layout based on the supplied list/grid references, retaining Auralis’s dark and amber theme. Replaces large segmented buttons with scrollable text tabs and a thin selected underline. Search opens on demand in the header. One compact toolbar provides the title count, sort menu, filters and grid/list toggle. Grid cards prioritize square artwork, title and author. List rows provide artwork, title, author, duration or remaining time, progress when started, and direct playback. Existing filters, group navigation, import and detail actions remain available. No database schema or playback-engine changes.

Validation: debug build, unit tests and lint passed; three dedicated device layout/action tests passed (360dp tab navigation, 320dp at 1.8 font scale, independent list play action). Inspected grid/list layouts and checked search on the connected Samsung A54. Installed as an in-place update to app.auralis; all 53 books preserved. A database/preferences backup was saved under device-backups before installation.

APK: dist/Auralis-0.3.1-library-debug.apk
