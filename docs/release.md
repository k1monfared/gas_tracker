# Release checklist

Product version is `0.3.0` in both `pyproject.toml` and `android/app/build.gradle.kts`. Keep those aligned unless you are publishing the Python library independently, which this project does not do.

## Before a signed build

1. From a clean checkout, install Python test tooling: `python3 -m pip install -e ".[test]"`
2. Run `python3 -m pytest -q`
3. From `android/`, run JVM tests and a debug APK with JDK 21:
   `./gradlew :app:testDebugUnitTest :app:assembleDebug`
4. Confirm `tests/fixtures/core_cases.json` still matches both suites.
5. Smoke-test backup restore: export JSON, corrupt `refills.json`, import the export, confirm history returns.
6. Smoke-test FX offline: airplane mode, foreign-currency refill, confirm costs show unavailable instead of invented amounts.
7. Confirm rotation keeps an unfinished log form, and that delete asks for confirmation.
8. Review `AndroidManifest.xml` permissions, backup rules, and FileProvider (`exported=false`).
9. Read `docs/privacy.md` against the shipped behavior.

## Signed artifact

1. Build a release APK or AAB with a local keystore. Release minifies with R8 and shrinks resources; Tesseract JNI and `data` package keep rules are in `android/app/proguard-rules.pro`. Expect about 20.5 MB for a universal unsigned APK.
2. Install on a device, log one refill, export, reinstall, import.
3. Tag the matching git revision (`v0.3.0`) and record the change in `CHANGELOG.md`.
