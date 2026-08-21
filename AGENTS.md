# AGENTS.md

Single source of truth for this repo. All agent tools read this file.

## Project

Gas tracker: fuel usage and cost tracking for one car, full-tank assumption at each refill.
Two parts:

- `gas_tracker/`: Python core library (reference implementation)
- `android/`: Kotlin Android app (Jetpack Compose) with a mirrored Kotlin port of the core

Spec and roadmap live in `readme.log`. Milestone 1 (manual entry + dashboard) is implemented;
milestones 2 (odometer-based input) and 3 (photo/OCR entry) are future work.

## Commands

Python (run from repo root):

- `python3 -m pytest -q` runs the Python test suite

Android (run from `android/`):

- `./gradlew :app:testDebugUnitTest :app:assembleDebug` runs JVM tests and builds the debug APK
- System Java is a JRE without javac. Pass a full JDK explicitly, e.g.
  `-Dorg.gradle.java.home=/tmp/opencode/jdk-21.0.12.1+1`, or install `openjdk-21-jdk-headless`
  and drop the flag
- APK output: `android/app/build/outputs/apk/debug/app-debug.apk`
- Emulator: AVD `test_device` exists (`~/Android/Sdk/emulator/emulator -avd test_device -no-window`),
  SDK lives at `~/Android/Sdk`

## Architecture notes

- Core logic exists twice by design: `gas_tracker/*.py` and
  `android/app/src/main/java/com/k1/gastracker/core/*.kt`. Keep both behaviorally identical;
  the test suites mirror each other case by case
- Processing pipeline: canonicalize units (km, liters), merge same-day refills (avoids infinite
  consumption on double refills), linear interpolation of missing distances over cumulative
  distance, cost interpolation via price-per-liter
- Windowed dashboard metrics use the last 28 days by default, falling back to the last 90 days
  when fewer than two refills are in the 28-day window. The yearly view uses the last 365 days
  and shows both an extrapolated yearly average and the actual past-year total
- Foreign-exchange conversion: non-home currencies are converted to the configured home currency
  using ECB reference rates from frankfurter.dev, cached locally per date and currency pair
- Android persistence: refills in `files/refills.json` (kotlinx.serialization), FX cache in
  `files/fx_cache.json`, last unit/currency selections and home currency in SharedPreferences
- Charts are hand-drawn Canvas composables. Bar charts always use a zero baseline

## Conventions

- No code comments unless asked
- No emojis in UI or docs
- Prefer FOSS dependencies; stdlib-first on the Python side
