# AGENTS.md

Single source of truth for this repo. All agent tools read this file.

## Project

Gas tracker: fuel usage and cost tracking for one car, full-tank assumption at each refill.
Two parts:

- `gas_tracker/`: Python core library (reference implementation)
- `android/`: Kotlin Android app (Jetpack Compose) with a mirrored Kotlin port of the core

Spec history lives in `readme.log`. Manual entry, dashboard, odometer input, and photo/OCR
entry are implemented. Architecture and release notes live in `docs/`.

Python and Android version metadata stay aligned (`0.3.0`). They are one product, not
independently versioned libraries.

## Commands

Python (run from repo root):

- `python3 -m pip install -e ".[test]"` installs pytest on a clean checkout
- `python3 -m pytest -q` runs the Python test suite

Android (run from `android/`):

- `./gradlew :app:testDebugUnitTest :app:assembleDebug` runs JVM tests and builds the debug APK
- Use JDK 17 or 21 to run Gradle (not Android Studio's bundled JBR 25). CI pins Temurin 21. The daemon is pinned in `android/gradle/gradle-daemon-jvm.properties`. If Studio reports an incompatible Gradle JVM, set Gradle JDK to 21 (Settings → Build → Gradle) or put that JDK in `android/gradle/config.properties` as `java.home`.
- APK output: `android/app/build/outputs/apk/debug/app-debug.apk`
- Native libs and dex are compressed in the APK; ABIs are `armeabi-v7a`, `arm64-v8a`, `x86_64`
- English Tesseract data is `android/app/src/main/assets/tessdata/eng.traineddata.gzip`
  (standard tessdata, gzipped under a non-`.gz` name so AAPT2 does not inflate it;
  decompressed into `files/tessdata/` on first OCR)
- Emulator: AVD `test_device` exists (`~/Android/Sdk/emulator/emulator -avd test_device -no-window`),
  SDK lives at `~/Android/Sdk`
- OCR instrumentation tests are not part of the default pull-request check

## Architecture notes

- Core logic exists twice by design: `gas_tracker/*.py` and
  `android/app/src/main/java/com/k1/gastracker/core/*.kt`. Keep both behaviorally identical;
  the test suites mirror each other case by case. Shared numeric cases live in
  `tests/fixtures/core_cases.json`
- Processing pipeline: canonicalize units (km, liters), merge same-day refills (avoids infinite
  consumption on double refills), linear interpolation of missing distances over cumulative
  distance, cost interpolation via price-per-liter only for originally missing costs
- Windowed dashboard metrics use the last 28 days by default, falling back to the last 90 days
  when fewer than two refills are in the 28-day window. Sparse windows do not extrapolate a
  run rate. The yearly view uses the last 365 days and shows an extrapolated yearly average
  only when coverage is sufficient, plus the actual past-year total
- Foreign-exchange conversion: non-home currencies are converted to the configured home currency
  using ECB reference rates from frankfurter.dev, cached locally per date and currency pair.
  Unavailable rates stay unavailable; they are not interpolated
- Android persistence: versioned refills in `files/refills.json` with `refills.json.bak`,
  FX cache in `files/fx_cache.json`, last unit/currency selections and home currency in
  SharedPreferences. A failed load is not an empty history and does not get overwritten by
  settings changes. Export/import is in Settings
- Charts are hand-drawn Canvas composables. Bar charts always use a zero baseline

## Conventions

- No code comments unless asked
- No emojis in UI or docs
- Prefer FOSS dependencies; stdlib-first on the Python side
