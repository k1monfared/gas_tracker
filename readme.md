# gas_tracker

Fuel usage and cost tracking for one car, assuming a full tank at every refill.

Two implementations of the same core:

- `gas_tracker/`: Python library (reference implementation), stdlib only
- `android/`: Kotlin Android app (Jetpack Compose) with a mirrored Kotlin port

## What it does

Each refill records date, distance driven, fuel filled, total cost, optional octane level,
and optional gas station, with selectable units (km/mi, L/gal) and currency. The processing
pipeline then:

- canonicalizes everything to km and liters
- merges same-day refills so double refills cannot produce infinite consumption
- interpolates missing distances linearly over cumulative distance
- estimates missing costs from the interpolated price per liter
- converts foreign currencies to your chosen home currency using ECB reference rates from
  frankfurter.dev, with rates cached locally per date and pair

From that it derives consumption (L/100km, mpg, km/L), cost metrics (per km, per day/week/month/year,
average price per liter), monthly/weekly/yearly time series, and per-refill efficiency trends.
The dashboard defaults to the last 28 days and falls back to the last 90 days when data is sparse;
the yearly view shows both an extrapolated run-rate and the actual past-year total.

## Running

Python tests:

```
python3 -m pytest -q
```

Android build and JVM tests (from `android/`):

```
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The debug APK lands at `android/app/build/outputs/apk/debug/app-debug.apk`.

## Status

Milestone 1 (manual entry + dashboard) is implemented on both platforms.
Roadmap lives in `readme.log`: milestone 2 is odometer-based input, milestone 3
is photo/OCR-based entry.
