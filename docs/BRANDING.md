# Auralis 0.3.0 — approved branding and launch experience

Version name remains 0.3.0; version code advances from 4 to 5 so this APK updates the previous installation. Application ID, signing configuration, Room schema and all audiobook records remain unchanged.

## Artwork and icon selection

Five designs come directly from the approved concept sheet: Classic, Signature, Horizon, Pulse and Quill. Signature is the manifest default. The color layers retain the supplied artwork; themed silhouettes and the launch contour trace are vector assets. See branding/README.md for source regions, dimensions and reproducibility.

You → Settings → Appearance → App icon opens an adaptive gallery with previews, names, descriptions and selected state. Selection updates only launcher activity aliases and the `app_icon` DataStore key. The stable MainActivity and MediaSessionService are always enabled. Notification PendingIntents target MainActivity directly.

On API 33+, one PackageManager batch enables the selected alias and disables the others. On API 26–32, the new alias is enabled before any previous alias is disabled. All changes use DONT_KILL_APP. Failed preference writes attempt to restore the prior selection. Startup and backup restore reconcile stored preference with component state. Unknown/missing legacy preference values resolve to Signature.

A launcher may cache icons briefly; the gallery explains the refresh delay. No reinstall or broad permission is required. The app exposes one launcher entry at a time.

## Opening

AndroidX core-splashscreen 1.2.0 handles the system starting window across API 26+. Both window and Compose use obsidian #111113. The system owns the initial dark backdrop with a transparent placeholder icon, avoiding a separate static splash emblem before the custom reveal. There is no extra splash Activity or startup video.

Normal animation timeline:

| Time | Presentation |
|---|---|
| 0–200ms | Obsidian darkness |
| 200–650ms | Trace the approved monogram's contours |
| 650–950ms | Resolve into the original golden artwork with restrained warmth |
| 950–1250ms | Fade editorial wordmark and tagline |
| 1250–1500ms | Fade continuously into the already initialized app |

The app initializes underneath the skippable overlay. A tap or Back enters immediately. Underlying controls are hidden from accessibility while the overlay is visible. The signature drawing uses the approved silhouette; it does not draw a newly invented A.

A process-scoped, atomic launch policy permits the full sequence once. Activity restoration/configuration changes, warm reopens and a service-first playback/notification entry skip it. Navigating or minimizing the player cannot replay it. The reduced-motion preference and disabled system animators show a brief static alternative; nonzero system animation scale adjusts the timing. No startup sound, internet request, audio focus request, service stop or playback pause is introduced.

Signature is used subtly in Home and tablet navigation branding; Horizon in first-book welcome; Classic in audiobook empty states; Pulse in the player indicator; Quill in bookmark access. Other screens retain their existing layout.

## Verification

Validated on 15 November 2026:

- Debug and minified unsigned release builds passed; Android Lint passed with zero errors (dependency/style warnings remain).
- 38 unit tests passed. The 22-test isolated Android suite passed; after the final startup handoff change, all six launch/gallery/navigation checks were rerun and passed.
- All five choices resolve to one enabled launcher, persist across recreation, provide adaptive/themed layers, and retain the stable activity. Both the API 33+ atomic path and the legacy enable-first path were exercised.
- The real Media3 test cycled all five choices during background audio and confirmed playback continued.
- Cold and warm launch recordings were reviewed. The approved monogram, wordmark and Home handoff are visible without a white flash; warm reentry skips the introduction. Startup work is completed before starting the animation clock so it cannot consume the stroke phase.
- Existing APK and branding APK signing certificates match. Installed version 0.3.0, version code 5, on the Samsung phone.
- Saved a private pre-update backup. Compared all existing Room rows after installation: 41 books, 250 audio references, 1,491 chapters, bookmarks, history, achievements, collections, sources and challenges are preserved. Saved chapter, position and speed are identical; last-open timestamps may advance normally. Integrity and foreign-key checks passed.
- The actual updated installation exposes exactly one launcher entry: Signature. The test installation was removed.

Private device backups and recordings are excluded from the source archive.

Tests include phase boundaries and system scale; cold/restore/service-first policy; skippable/reduced-motion presentation; phone/landscape typography; gallery selection and persistence; adaptive/themed layers; single launcher resolution; the sequential API 26–32 switching path; and real Media3 background playback while cycling all five choices.

Only the isolated verification installation is used for mutating automated tests. The personal app is backed up before the actual update and its database contents are compared afterward.

Compatibility limits: physical testing uses Android phone / Android 16. The legacy ordering path is exercised there, but API 26–32 hardware and additional OEM launchers remain untested. The launch timeline is approximately 1.5 seconds at normal animation scale, plus device startup work; user-selected slower system animation scales intentionally lengthen it and remain skippable.

Implementation references: [Android SplashScreen](https://developer.android.com/develop/ui/views/launch/splash-screen), [AndroidX core-splashscreen release](https://developer.android.com/jetpack/androidx/releases/core#core-splashscreen-1.2.0), [PackageManager component settings](https://developer.android.com/reference/android/content/pm/PackageManager#setComponentEnabledSettings(java.util.List)).
