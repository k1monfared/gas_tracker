# Privacy

All refill history, photo drafts, and OCR text stay on the device. The app does not create an account and does not upload receipts.

## What is stored locally

- Refill history in `files/refills.json` (plus `.bak` and, if a read fails, `.corrupt`)
- Last unit and currency choices in SharedPreferences
- Exchange-rate cache in `files/fx_cache.json`
- Optional camera/gallery images and OCR text in `files/photos/` and `files/photo_cache.json`

Draft photos and OCR text are removed when a refill is saved, editing is cancelled, or the user removes a draft.

## Network

The only network use is fetching ECB reference rates from `https://api.frankfurter.dev` for dates and currency pairs that are not already cached. Amounts and station names are not sent. If a rate cannot be fetched, converted costs are omitted rather than estimated.

## Backup

Cloud backup and device transfer include refill history, the FX cache, and preferences. Receipt photos, OCR drafts, the Tesseract model, and corrupt-file copies are excluded. You can also export a JSON backup from Settings.

## Deletion

Deleting a refill removes it from `refills.json`. Clearing drafts deletes the stored images. Uninstalling the app removes local files unless a cloud backup exists.
