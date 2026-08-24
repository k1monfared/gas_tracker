# Data format

Refill history is a JSON object:

```json
{
  "version": 1,
  "refills": [
    {
      "date": "2026-01-01",
      "volume": 40.0,
      "distance": 500.0,
      "cost": 80.0,
      "distanceUnit": "KM",
      "volumeUnit": "LITER",
      "currency": "EUR",
      "octane": 95,
      "station": "Shell",
      "odometer": 125000.0
    }
  ]
}
```

`version` is required for new writes. Files written before versioning (`{"refills":[...]}`) still load as version 1.

Unsupported future versions fail closed: the file is left in place and the app does not treat the result as an empty history.

## Migrations

- v1: initial versioned schema. Same fields as the unversioned format.
- On write, the previous good file is copied to `refills.json.bak` before the new file is published.
- Unreadable files are copied to `refills.json.corrupt` and must be repaired or replaced via import.

## Backup and restore

Settings can export this JSON and import it later. Import replaces in-memory history only after the file parses as a valid v1 document. Photos and OCR drafts are not part of the export.
