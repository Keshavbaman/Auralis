# Auralis 0.4.3 — Refined panels

Book details: compact fixed toolbar, smaller centred artwork with a restrained warm tint, balanced identity/credits, grammatical chapter count, separate playback/unique-coverage metrics, rounded chapter and note cards, consistent primary playback action, collection action and overflow management menu. Favourite, metadata editing, cover changes, relinking, marking finished/unfinished, resetting progress, bookmarks and confirmed removal remain functional. Single-file recordings are labelled “Full recording” without claiming the recording is an unabridged book.

Bookmark editor: rounded tinted fields, optional title, multiline note, smaller consistent sheet heading and a matching Save button. The existing captured chapter/position anchor is preserved. The shared player-sheet scroll/IME handling remains; title/note entry is saveable in the editor. Other player tool sheets receive the quieter heading treatment.

Settings: grouped rounded sections, warm icon wells, explanatory subtitles and consistent navigation. Overview no longer collects the full history unnecessarily. Existing settings pages and preference writes are preserved, with a refined back header.

Listening history: asynchronous session grouping off the main thread, original listening dates and per-session time/speed retained, date sections, real total/session summary and compact rounded entries. Retained library books can be opened directly; missing books remain represented.

Validation: debug and R8-optimized builds succeeded; debug lint and optimized vital lint passed. Five new UI cases passed across final test runs: detail/play/collection/overflow/chapter/remove callbacks, narrow 320dp with 1.5 font scale/light theme, bookmark text input and save with keyboard open, Settings section navigation with enlarged text, and history book selection. The first narrow-layout test tried to find an uncomposed off-screen note field; corrected it to scroll the lazy list to the field, and the test passed. Two existing removal tests and the navigation regression passed. Removal tests used only the isolated verification app and verified original audio/history preservation, cancellation, and loaded-player checkpoint handling.

Installed in place on the Android phone. Inspected production Settings and history screens. Book-details and bookmark editor were verified in device UI tests. Further live navigation was stopped when the phone returned to its launcher. No library database schema, audio files or playback-engine code changed.

APK: dist/Auralis-0.4.3-refined-panels-optimized.apk
Screenshots: dist/Auralis-0.4.3-settings.png and dist/Auralis-0.4.3-history.png
