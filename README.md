# Gas Tracker

**Fuel usage and cost tracking for one car**

[Website](http://k1monfared.com/gas_tracker/) | [Download APK](https://github.com/k1monfared/gas_tracker/releases/latest) | [Changelog](CHANGELOG.md)

**Status**: POC | **Version**: 0.2.0 | **License**: MIT | **Min Android**: 10.0+

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
- History list with edit and delete.
- Foreign costs are converted to your home currency using ECB reference rates cached locally.

---

## Install

1. Download the latest APK from [GitHub Releases](https://github.com/k1monfared/gas_tracker/releases/latest).
2. Open the APK on your Android 10+ device and allow installation from your browser/file manager.
3. Open Gas Tracker and log your first refill.

For automatic updates, point [Obtainium](https://github.com/ImranR98/Obtainium) at this repository.

---

## Notes

- The APK is about 66 MB because it bundles the Tesseract OCR English model.
- OCR classification is rule-based. If a photo is misclassified, remove the draft and take a clearer photo.
- Camera capture works on real devices. The emulator camera app on the test AVD crashes, so the camera path has only been verified via intent setup there.

---

## For developers

### Architecture

The project has two mirrored cores:

- `gas_tracker/`: Python reference implementation and tests.
- `android/app/src/main/java/com/k1/gastracker/core/`: Kotlin port used by the Android app.

Both cores implement the same processing pipeline: canonicalize units, merge same-day refills, interpolate missing distances, compute windowed metrics, and convert costs via cached FX rates.

The Android UI is built with Jetpack Compose. State is held in `AppViewModel` and persisted with:

- `files/refills.json` for refills
- `files/fx_cache.json` for exchange rates
- `files/photo_cache.json` and `files/photos/` for OCR drafts
- `SharedPreferences` for settings

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
├── docs/                         # GitHub Pages site
├── README.md
├── readme.log                    # Original spec and roadmap
└── CHANGELOG.md
```

### Build and test

Python (from repo root):

```bash
python3 -m pytest -q
```

Android (from `android/`):

```bash
./gradlew -Dorg.gradle.java.home=/tmp/opencode/jdk-21.0.12.1+1 :app:testDebugUnitTest :app:assembleDebug
```

The system Java is a JRE without `javac`, so pass a full JDK with `-Dorg.gradle.java.home=...` or install `openjdk-21-jdk-headless` and drop the flag.

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

Gas Tracker is released under the **MIT License**. See [LICENSE](LICENSE).
