# The Stack 3D

## Desktop (full original game)

```sh
./gradlew :desktop:run
```

The original desktop source and assets remain in `src/main`. The working directory
stays at the project root, preserving existing preferences and the `towers/` collection.

## Android (temporary tower-only edition)

```sh
./gradlew :android:assembleDebug
```

APK: `android/build/outputs/apk/debug/android-debug.apk`.
Requires Android 6.0 or newer. Includes ARM64, ARMv7 and x86_64 natives.
Set your SDK path in the ignored `local.properties` (`sdk.dir=...`) or `ANDROID_HOME`.
Build requires JDK 21; game bytecode targets Java 17.

On Android startup, `made by Glob876 :D` fades in on black, stays briefly,
and fades out before the initial screen fades in. The intro lasts 3.4 seconds
and ignores taps until it finishes; returning from the background does not replay it.

Tap PLAY, then tap the screen to place each block. Precise placement preserves its
size; overhangs are cut away; missing the tower ends the run. Use the pause button
or Android Back to pause. Returning from the background keeps the game paused.

RECORDS shows the ten best completed runs and the all-time best. Records are
saved locally on this device; the all-time best is saved after each placement.
On first launch, choose Russian (the default) or English, then meet Glob for a short
guide. Tap a dialogue card to reveal its text, then advance with NEXT; SKIP starts
the game immediately. Settings in the main menu saves the language and lets you
replay the guide. Language and guide completion are stored locally.
Placements give a short vibration, perfect streaks give a longer pulse, and the
explosion gives a stronger pulse. Vibration can be switched off in Settings; this
preference is saved immediately. Devices without a vibrator continue normally.
This Android edition contains no exchange, tower collection or mini-games.
Desktop retains all of these features.

Perfect placements flash the background and shake the camera, with stronger effects
for combos. A missed block falls, then plays the original explosion animation and
sound before showing the result. Effects pause when the app goes into the background.
Android explosion assets can be regenerated from the desktop originals with
`python3 tools/pack_android_explosion.py` (requires Pillow).

All Android text uses a Cyrillic bitmap font rasterized from
[Liberation Sans](https://github.com/liberationfonts/liberation-fonts), with nearest
filtering for the original's subtle pixel look. Its SIL OFL license is included in
`android/src/main/assets/fonts/LICENSE-Liberation.txt`. Regenerate the atlas with
`python3 tools/pack_android_font.py` (requires Pillow); the source font is in
`tools/font-source/`.

The launcher icon is a slightly tilted tower of four slabs. Android 8+ uses an
adaptive vector icon, Android 13+ also supports themed icons, and older launchers
use density-specific PNGs. Regenerate all variants with
`python3 tools/pack_android_icon.py` (requires `rsvg-convert`). The editable SVG and
a 512px preview are in `artwork/`.

```sh
./gradlew :core:test :desktop:classes :android:assembleDebug
```
