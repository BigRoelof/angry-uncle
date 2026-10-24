# Angry Uncle

A pass-the-phone party game for Android. The screen is packed with uncles; take turns tapping one and it flies off.
One of them is secretly angry, and whoever taps him loses.

Play with the built-in cartoon uncles, or add your own heads: take a normal photo and an angry one
(or let the app fake the angry face), and optionally record their angry yell. Faces are cropped
automatically and the background is removed on the phone itself. Nothing is uploaded anywhere.

## Install

Download the APK from the [latest release](../../releases/latest) and open it on an Android 8.0+ phone.
Android will ask you to allow installs from your browser or file manager.

## Build

```sh
./gradlew testDebugUnitTest   # game rules
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease     # signed if keystore.properties exists (see app/build.gradle.kts)
```

## Sounds

`app/src/main/res/raw/`:

- `sfx_pop_*.ogg`, `sfx_punch.ogg`: from [Kenney](https://kenney.nl)'s Interface Sounds and Impact Sounds packs (CC0).
- `sfx_boing_*`, `sfx_sting`, `sfx_rumble`, `sfx_growl` (`.wav`): synthesized by `tools/gen_sounds.py`.
  Tweak and regenerate with:

  ```sh
  uv run --with numpy --with scipy --with soundfile tools/gen_sounds.py app/src/main/res/raw
  ```

## Credits

Font: [Fredoka](https://github.com/hafontia-zz/Fredoka), SIL Open Font License (`licenses/Fredoka-OFL.txt`).
Sound effects: [Kenney](https://kenney.nl), CC0 (`licenses/Kenney-CC0.txt`).
