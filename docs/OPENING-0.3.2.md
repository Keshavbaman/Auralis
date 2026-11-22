# Auralis 0.3.2 — Opening screen and logo

Uses the first supplied screenshot’s near-black opening composition, gold spaced sans-serif AURALIS lettering, neutral tagline and bottom phrase. The supplied second screenshot’s gold A with audio bars was isolated with image cleanup; original references and cleaned transparent artwork are saved in branding/approved-v2. The extracted logo replaces the Signature identity in the opening, in-app brand marks and default launcher icon. Alternative icons remain available. Android 13 monochrome icon uses the new silhouette.

The opening fades and gently settles into place; existing tap-to-skip, reduced-motion, service-first and once-per-process launch rules remain. No library, database or playback-engine changes.

Validation: debug and isolated verification builds succeeded; unit tests and lint passed; all six branding presentation tests passed on the attached Samsung phone, including narrow 320dp at 1.8 font scale, tablet landscape, reduced motion, skipping and icon gallery selection. Final opening screenshot inspected. An app data/preferences backup was taken before installing the update.

APK: dist/Auralis-0.3.2-opening-debug.apk
Preview: dist/Auralis-0.3.2-opening.png

The older tools/prepare_brand_assets.py reproduces the previous concept-sheet extraction. It does not produce the v2 Signature artwork; preserve the v2 adaptive icon references when re-exporting other legacy icons.
