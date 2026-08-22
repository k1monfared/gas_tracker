# Gas Tracker

<img src="docs/logo-512.png" alt="Gas Tracker logo" width="120" height="120">

**Fuel usage and cost tracking for one car**

[Website](http://k1monfared.com/gas_tracker/) | [Download APK](https://github.com/k1monfared/gas_tracker/releases/latest) | [Changelog](CHANGELOG.md)

**Status**: POC | **Version**: 0.3.0 | **License**: GPL-3.0 | **Min Android**: 10.0+

---

## What is Gas Tracker?

Gas Tracker is a simple, private Android app for tracking how much fuel your car uses and how much it costs. It assumes you fill the tank at every refill, so efficiency and cost-per-distance calculations are straightforward.

All data stays on your device. There are no ads, no accounts, and no cloud dependency except for optional currency conversion rates.

---

## Features

### Manual or odometer entry

- Enter distance driven since the last refill, or just type the current odometer reading and let the app compute the distance.
- Volume, cost, date, octane, and station fields.
- Remembers your preferred distance, volume, and currency units.

### Photo / OCR entry

- Take a photo of the pump display or odometer, or pick one from the gallery.
- Tesseract OCR runs on your device and extracts the numbers.
- The app auto-classifies the photo as pump, odometer, or receipt.
- Extracted values appear as drafts at the top of the form and are prefilled for you to review before saving.
- Drafts are cached locally, so they survive app restarts.

### Dashboard and history

- Dashboard with recent metrics: cost per period, efficiency (L/100 km and MPG), average price per liter, and more.
- Consumption ratios only include fuel that has a known distance. Sparse history does not invent a monthly or yearly run rate.
- History list with edit and delete, plus JSON export and import in Settings.
- Foreign costs are converted to your home currency using ECB reference rates cached locally. Missing rates stay blank instead of being estimated.
- Refill history is versioned JSON with a backup copy. A damaged file is never treated as an empty log.

---

## Install

1. Download the latest APK from [GitHub Releases](https://github.com/k1monfared/gas_tracker/releases/latest).
2. Open the APK on your Android 10+ device and allow installation from your browser/file manager.
3. Open Gas Tracker and log your first refill.

For automatic updates, point [Obtainium](https://github.com/ImranR98/Obtainium) at this repository.

---

## Notes

- The debug APK is about 24 MB. The English Tesseract model is gzip-compressed in the APK and unpacked on first OCR use. OCR accuracy is unchanged.
- OCR classification is rule-based. If a photo is misclassified, remove the draft and take a clearer photo.
- Camera capture works on real devices. The emulator camera app on the test AVD crashes, so the camera path has only been verified via intent setup there.

---

## For developers

### Architecture

The project has two mirrored cores:

- `gas_tracker/`: Python reference implementation and tests.
- `android/app/src/main/java/com/k1/gastracker/core/`: Kotlin port used by the Android app.

Both cores implement the same processing pipeline: canonicalize units, merge same-day refills, interpolate missing distances, compute windowed metrics, and convert costs via cached FX rates. Shared numeric cases live in `tests/fixtures/core_cases.json`.

The Android UI is built with Jetpack Compose. State is held in `AppViewModel` and persisted with:

- `files/refills.json` for refills (schema version 1, plus `.bak`)
- `files/fx_cache.json` for exchange rates
- `files/photo_cache.json` and `files/photos/` for OCR drafts
- `SharedPreferences` for settings

Architecture, data format, privacy, and release notes live in `docs/`.

### Project structure

```
gas_tracker/
├── gas_tracker/                  # Python core library and tests
├── android/
│   ├── app/src/main/java/com/k1/gastracker/
│   │   ├── core/                 # Kotlin core (mirrors Python)
│   │   ├── data/                 # Persistence, FX, OCR, photo cache
│   │   └── ui/                   # Jetpack Compose screens
│   └── app/build.gradle.kts
├── docs/                         # GitHub Pages site plus architecture notes
├── README.md
├── readme.log                    # Original spec and roadmap
└── CHANGELOG.md
```

### Build and test

Python (from repo root):

```bash
python3 -m pip install -e ".[test]"
python3 -m pytest -q
```

Android (from `android/`, JDK 21):

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Use JDK 17 or 21 to run Gradle, not Android Studio's bundled JBR 25. CI pins Temurin 21.

Debug APK output:

```
android/app/build/outputs/apk/debug/app-debug.apk
```

### Tech stack

| | |
|---|---|
| Language | Kotlin (Android), Python (reference core) |
| Min SDK | API 29 (Android 10) |
| Target SDK | API 36 |
| UI | Jetpack Compose |
| Persistence | kotlinx.serialization + SharedPreferences |
| OCR | Tesseract (tess-two) |
| FX rates | frankfurter.dev / ECB |

### Contributing

1. Fork the repository.
2. Create a feature branch from `master`.
3. Keep the Python and Kotlin cores behaviorally identical.
4. Add or update tests in both codebases.
5. Submit a pull request with a clear description.

### License

Gas Tracker is released under the **GNU General Public License v3.0** (GPL-3.0).

This means anyone can use, study, modify, and distribute the app, but any distributed derivative must also be released under GPL-3.0 and include its source code. This keeps the project and its improvements open.

The bundled Tesseract OCR model and the `tess-two` library are Apache-2.0 licensed, which is compatible with GPL-3.0. Jetpack Compose and Material3 dependencies are also Apache-2.0 and compatible.
