# Approved Auralis identity

Source: `Auralis Audiobook Icon Concept Sheet.png`, supplied and explicitly approved by the user.

The five colored marks are extracted from that sheet. The signature used at launch is the approved serif A with two flowing strokes underneath; it is not a substitute logo.

`tools/prepare_brand_assets.py` performs deterministic extraction, matte removal, safe-area placement, lossless WebP export and silhouette tracing. No generative image model is used. Original RGB artwork is retained, with transparent/feathered presentation-matte boundaries. Classic retains the original headphones, book, waveform, ribbon and illuminated round backdrop. The sheet's frames, drop shadows and wordmark are not baked into launcher foreground layers.

Assets:

- `app/src/main/res/drawable-nodpi/brand_*.webp`: tightly framed, 512px presentation marks derived from the approved source.
- `app/src/main/res/drawable-nodpi/ic_*_foreground.webp`: 432px adaptive foreground layers (108dp at 4×), inside Android's guaranteed safe circle.
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_*.xml`: adaptive foreground/background definitions for API 26–32.
- `app/src/main/res/mipmap-anydpi-v33/ic_launcher_*.xml`: adaptive definitions with monochrome layers for themed launchers.
- `app/src/main/res/drawable/ic_*_monochrome.xml`: vector silhouettes traced from the corresponding approved artwork.
- `app/src/main/res/values/brand_trace.xml`: the actual approved A's contours used by the opening's stroke reveal.
- `branding/approved/adaptive-preview.png`: circular and rounded mask review on dark and light surroundings.

The four smaller designs in the supplied sheet are approximately 220px across. Exports are correctly sized for Android, but resizing cannot invent additional source detail. A future larger original can be substituted through the same extraction pipeline without changing the icon-switching mechanism.

To reproduce, install Pillow, NumPy and OpenCV in a Python environment and run:

```bash
python3 tools/prepare_brand_assets.py
```

The wordmark uses Android's bundled serif family; no additional downloadable font, font service or startup audio is used. Existing interface typography is unchanged.
