# Auralis 0.3.0

Build 5 adds the [approved brand assets, selectable launcher icons and cold-launch experience](BRANDING.md), preserving the version name and existing data.

## Reliability

- Home, library shelves, mini-player and full player derive active position from one MediaController snapshot. Media ID resolves chapter identity; Media3 events publish immediately and a 400 ms ticker advances progress.
- Shelf percentage now means playback position. Book details separately labels unique listened coverage. Completion still requires 98% unique coverage or an explicit manual completion; seeking cannot finish a book or inflate listening time.
- Flow equality includes computed coverage, so new segments update coverage even when persisted position is unchanged.
- Rapid book switches drain pending checkpoints before reading a saved position. Updating the last-book preference changes only that key, preserving playback/appearance preferences.
- Six calendar months are generated from local dates, Monday-aligned into 26–28 complete week columns. Recent activity is initially visible; swipe horizontally for older weeks. Month/weekday labels, exact date range and five-level legend explain the chart.
- Calendar labels and streaks refresh while the app is foregrounded, including after midnight with paused playback. Historical segment dates retain their recorded local zone.
- Analytics use explicit minutes/hours/seconds, while player timestamps retain clock notation.

## Home and library

- Continue Listening leads Home with current chapter, timeline progress and speed-adjusted remaining time. Compact daily goal/streak follows.
- Continue the Series, recommendations based on local author history, recently added and unfinished shelves avoid duplicate books across sections.
- Books / Series / Authors / Collections categories; separate filter sheet for status, favourites, narrator, genre and edition; sorting and adaptive grid/list.
- Numeric series ordering and a dedicated group detail view with artwork, author, duration and ordered books. Numbered title prefixes provide inferred series; metadata editing allows explicit series name and fractional book number.
- Room 3→4 adds series name, optional numeric book number and edition without replacing rows or changing media URIs. Existing full-cast titles receive an edition label. Migration chain remains available from version 1.

## Player and design

- Central charcoal/amber palette, balanced system typography, spacing and shape tokens. Font scaling and optional light/wallpaper themes remain functional.
- Artwork shared-element transition between the compact mini-player and full player, disabled for reduced motion. Swipe down on the player header to dismiss it.
- Mini-player shows chapter number/title and the configured skip interval, with whole-book timeline progress. Existing Scaffold insets keep content above controls/navigation.
- Current chapter automatically scrolls into view. Playing/paused timestamp and unique-coverage completion indicators clarify chapter navigation. Existing chapter and whole-book seeking, speed, notes/bookmarks and sleep countdown remain service-connected.
- Optional smart rewind: 10 seconds after a pause of at least five minutes, including a cold resume. Rewinding resets listening anchors. Toggle in Playback settings.
- Large-control car mode from the speed sheet; audio still uses the same Media3 session.

## Listening, milestones and profile

- Labeled interactive charts with grid lines, selected values, accessible spoken totals and a daily-goal reference for daily views; monthly/yearly comparisons and existing listening-pattern insights.
- Twelve original vector-drawn shield medals, distinctive symbols/colors, recognizable muted locked states, real measurable progress and detail sheets. Unlock timestamps remain immutable. Optional haptic feedback when a new unlock arrives while the gallery is open.
- Seven distinct geometric rank emblems, current lifetime listening time, progress and time to next rank, and an all-ranks progression screen.
- Compact profile with rank, daily goal/streak, trophy preview, challenges, history and Settings. Dedicated Settings sections retain all existing controls, rescan and backup/restore. Notifications opens Android settings; no reminder system is fabricated.

## Wrapped

- Eight swipeable monthly/annual stories: time, completions, favourite book, author/genre, streak, pace/time saved, rule-based personality and summary.
- Earlier/current report-period selection and informative empty reports. All values derive from real segment/completion records.
- 1080×1920 story PNGs and 1080×1350 summary PNGs, rendered off the UI thread with wrapping typography, vector ornaments and optional book artwork.
- Every export confirms privacy. Private titles, covers, author and genre are excluded unless explicitly selected.

## Remaining limitations

- Verification uses an Android phone, forced large-tablet dimensions and enlarged text. Physical tablet/OEM/Bluetooth certification, an exhaustive TalkBack/keyboard audit and a 30-hour soak remain release gates.
- Shared-element artwork transitions are implemented; interactive predictive-back morphing and full-surface swipe dismissal are not. Header swipe and standard back are supported.
- Car mode is a large-control UI, not Android Auto. Cross-book queues, Cast/sync, silence skipping and other chapter container formats remain separate work.
- Gallery haptics are optional; a global celebration overlay and achievement tiers are not implemented. Wrapped uses original static ornaments and native swipe motion, without elaborate illustration timelines.
- Large histories still observe segment lists in memory. Indexed aggregate queries/paging remain future performance work.
- The periodic checkpoint strategy can lose the final uncommitted interval on abrupt OS process termination (about ten seconds); committed history is idempotent.
- Debug updates use the existing local debug signing key. The minified release APK is unsigned until a release signing key is configured.

See UPDATE_0_3.md for test and device-update evidence.
