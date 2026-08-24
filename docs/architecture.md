# Architecture

Gas Tracker keeps a Python reference core and a Kotlin Android port. Both must stay behaviorally identical. Shared cases live in `tests/fixtures/core_cases.json` and are executed by both test suites.

## Pipeline

1. Canonicalize units to km and liters.
2. Merge same-day refills.
3. Derive missing distances from consecutive odometer readings.
4. Interpolate remaining interior distances over cumulative distance.
5. Interpolate originally missing costs from neighboring price-per-liter values.
6. Convert foreign-currency costs before building a dataset. Failed conversions are marked not interpolatable so they never become estimated home-currency amounts.

## Metrics

Consumption ratios (L/100km, mpg, km/L, cost/km) use only samples that have a known positive distance. Total volume and total cost still report every refill. Sparse windows (fewer than two refills or fewer than seven days of coverage) do not extrapolate a run rate.

## Android storage

- Refills: `files/refills.json` (schema version 1) with `refills.json.bak` and atomic writes.
- FX cache: `files/fx_cache.json`
- Photo drafts: `files/photo_cache.json` and `files/photos/`
- Preferences: SharedPreferences (`home_currency`, units, input currency)
- Tesseract English model: `assets/tessdata/eng.traineddata.gzip`, decompressed to `files/tessdata/eng.traineddata` on first OCR. The extra `p` in `.gzip` is required; AAPT2 would otherwise inflate a `.gz` asset and undo the size win.

A failed refill load is an error, not an empty history. Preference changes never rewrite refill history. Export/import uses versioned JSON through the system document picker.

## Mirrored files

| Python | Kotlin |
| --- | --- |
| `gas_tracker/models.py` | `android/.../core/Models.kt` |
| `gas_tracker/processing.py` | `android/.../core/Processing.kt` |
| `gas_tracker/metrics.py` | `android/.../core/Metrics.kt` |
| `gas_tracker/window.py` | `android/.../core/Windowing.kt` |
| `gas_tracker/fx.py` | `android/.../core/Fx.kt` |
| `gas_tracker/units.py` | `android/.../core/Units.kt` |
