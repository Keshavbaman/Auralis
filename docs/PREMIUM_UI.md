# Auralis 0.2.0 — player and visual refinement

Implemented in native Compose, using the existing MediaController/service transport and existing database. No library or history reset or schema change is needed.

- Artwork-derived ambient background, calculated from a small decoded thumbnail off the UI thread. Covers preserve their full image in the player and shelves.
- Artwork entrance and play/pause scale transitions, spring feedback on transport controls, crossfaded play/pause icons, chapter text transitions, and an active-listening indicator. Continuous decorative animation exists only while playing.
- A calmer player hierarchy: title, author/edition, optional narrator, current chapter, one primary timeline, transport, and labeled tools. Full-book seeking expands from the remaining-time summary.
- Smoothed timeline updates, direct scrub previews, and a short pending-seek state while the controller updates. Seek jumps snap rather than animating through unrelated content.
- Native bottom sheets for speed, sleep presets/custom timer/fade/extension, bookmarks/notes and chapter navigation. Bookmark positions are still captured when the bookmark tool opens.
- A floating rounded mini-player, restrained navigation styling and animated route transitions.
- System sans-serif typography, neutral surfaces, square library artwork, shorter Home headings, and progress rails without misleading endpoint markers at zero progress. Optional wallpaper accent colors are preserved.
- Responsive landscape/tablet arrangement and scrollable controls for short screens or enlarged fonts. Reduced motion disables the custom transitions and listening indicator animation. Standard platform/Material sheet behavior remains native.

Validation on 15 November 2026:

- Debug and minified unsigned release builds passed.
- 23 existing domain/parser unit tests passed.
- Android Lint passed with zero errors; dependency-update/style warnings remain.
- Three isolated player presentation tests passed on the connected Android phone: phone controls/callbacks and book-wide seeking, a 1400×876 dp tablet layout, and a 360×720 dp phone at 150% text scaling.
- The isolated tests use test-only book metadata and do not import, delete or modify the user's library/history.
- The actual installed player and floating mini-player were visually inspected with local audiobook artwork.

These checks validate this UI change. They do not replace the extended playback, accessibility and hardware release gates in FEATURES.md.
