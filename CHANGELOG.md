# Changelog

## v0.2.0

- Odometer photo drafts infer distance travelled from previous refills, including through manually entered distance refills.
- Manual form shows both distance and odometer fields and updates one from the other in real time.
- Odometer reading is validated against the previous logged value.
- Warn when fuel efficiency is more than 1.5x the past average, with save anyway / cancel options.
- Camera capture and photo auto-classification (pump / odometer / receipt).

## v0.1.0

- Manual refill entry: volume, cost, distance, date, octane, station.
- Odometer-based input with automatic distance computation.
- Photo / OCR entry using on-device Tesseract.
- Auto-classification of pump, odometer, and receipt photos.
- Cached photo drafts that survive app restarts.
- Dashboard with cost, efficiency, and usage metrics.
- Refill history with edit and delete.
- FX conversion to home currency with cached ECB rates.
- Settings for home currency, default distance/volume/currency units.
