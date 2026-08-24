# Changelog

## v0.3.0

- Fail-closed refill persistence with schema versioning, atomic writes, backup recovery, and JSON export/import.
- Damaged history is no longer treated as an empty log; settings changes cannot overwrite it.
- FX conversion fails closed: missing rates are shown as unavailable and are not interpolated into costs.
- Consumption ratios use only volume paired with known distance; sparse history no longer invents extreme run rates.
- Refill models reject NaN and infinity; the log form validates cost and octane before save.
- Form state survives rotation; FX requests are cancelled on newer currency changes; photos are downsampled before OCR.
- Delete asks for confirmation; photo drafts clear when editing is cancelled.
- Cloud backup includes refill history and excludes receipt photos and OCR drafts.
- User-facing copy moved to string resources; charts expose TalkBack summaries.
- CI runs Python tests and Android JVM tests plus a debug APK on JDK 21.
- Debug APK dropped from about 68 MB to about 24 MB: gzipped Tesseract model, compressed native libs and dex, no 32-bit x86, R8 shrinking. Unsigned release is about 20.5 MB. OCR quality is unchanged.

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
