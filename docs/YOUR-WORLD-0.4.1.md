# Auralis 0.4.1 — Your world

Redesigned the personal page with a brand eyebrow and balanced heading, warm gradient level card with relative progress to the next listening level, three compact listening metrics, daily goal ring, padded achievement cards and grouped challenge/history/settings navigation. The tablet layout places the level and daily goal alongside each other, with content capped at 1040dp. Light/dark themes, large text and reduced motion are supported.

All content comes from the existing local statistics, library and achievement flows. No database, import or playback-engine changes. Existing destinations are retained. The animated goal reads its value in Canvas drawing; no new full-screen blur, continuous animations or history aggregation on the main thread were added.

Validation: debug and optimized builds succeeded; debug lint and optimized vital lint passed. Three new presentation/action tests plus the app navigation regression test passed on the attached Samsung phone. Covered level/goal/history actions, 320dp with 1.5 font scale in light mode (including Grandmaster label), and 1200dp tablet layout. The optimized update was installed in place and the real Your world screen inspected.

APK: dist/Auralis-0.4.1-your-world-optimized.apk
Preview: dist/Auralis-0.4.1-your-world.png
