# Auralis 0.4.2 — Home

Redesigned Home with a balanced brand/greeting header, a dedicated import control, a gold-accented Continue Listening card with larger square artwork, chapter context, accessible progress and Resume/Open Player action. Remaining time respects the saved playback speed. A compact daily goal/streak strip opens goal settings. Book shelves have consistent margins, square artwork, restrained metadata and contextual subtitles. A library action at the end of the page is functional.

Existing local series suggestions, author-based picks, recently added and unfinished shelves remain. Books are deduplicated across shelves. Shelf reductions now run on Dispatchers.Default and fast player updates remain filtered out of Home. When no book is in progress, an unfinished recently added book can be featured for starting playback. The empty library retains working import actions.

Content is capped at 1120dp on wide displays; tablet hero artwork grows to 180dp. Light/dark themes, large text, accessible actions and scrolling are supported. No expensive blur or continuous animations were added. No database, import engine or playback service changes.

Validation: debug and R8-optimized builds, debug lint and optimized vital lint passed. Four new presentation/action checks and the existing app navigation regression passed on the attached Samsung device. Covered independent Resume/details actions, goals navigation, empty import, 320dp with 1.5 font scale in light mode, and a 1400×876dp tablet layout. Optimized APK installed as an in-place update; real Home inspected and existing chapter/speed/progress displayed correctly.

APK: dist/Auralis-0.4.2-home-optimized.apk
Preview: dist/Auralis-0.4.2-home.png
