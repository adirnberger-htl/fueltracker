# Proposal

## Why

Hardware camera photo capture on the refuel logging screen currently fails on Android devices because `android.permission.CAMERA` is declared in `AndroidManifest.xml` without runtime permission handling when launching `ActivityResultContracts.TakePicture()`, causing a `SecurityException`. Furthermore, receipt OCR analysis erroneously extracts tax amounts (e.g. MwSt/VAT) as fuel volume and grabs footer slogans (e.g. "Vielen Dank") as station names due to unordered ML Kit text blocks, lack of tax-keyword exclusion, and absent mathematical verification against total cost.

## What Changes

- Remove unnecessary `CAMERA` permission declaration or handle runtime camera permission gracefully so `ActivityResultContracts.TakePicture()` successfully launches system camera without crashes or security blocks.
- Preserve staging camera capture URIs across Android Activity rotation and process lifecycle recreation using `rememberSaveable`.
- Ensure temporary capture file is created on disk prior to invoking camera intent to support all device camera providers.
- Implement spatial sorting (top-to-bottom bounding box Y coordinates) on ML Kit detected text lines so receipt header brand names precede footer slogans.
- Filter out tax lines (`MwSt`, `UST`, `VAT`, `Tax`, `Netto`, `%`) from volume extraction candidates.
- Add mathematical cross-verification using `FuelMath` (`Volume * UnitPrice ≈ TotalCost`) to eliminate invalid volume candidates and infer missing volume when total cost and unit price are available.
- Exclude receipt footer phrases and disambiguate monetary `"Total"` keywords from the petrol brand `"Total"`.

## Capabilities

### New Capabilities
<!-- None -->

### Modified Capabilities
- `refuel-logging`: Enhances Camera OCR extraction with spatial text sorting, tax number exclusion, and FuelMath cross-validation, and makes hardware camera photo capture reliable across device camera apps and orientation changes.

## Impact

- `app/src/main/AndroidManifest.xml`: Remove redundant `CAMERA` permission or configure runtime permission handling.
- `app/src/main/java/com/fueltracker/ui/screens/AddRefuelScreen.kt`: Robust camera intent launching with `rememberSaveable` state and error reporting.
- `app/src/main/java/com/fueltracker/util/ImageStorageHelper.kt`: Pre-create file inode for camera URI targets.
- `app/src/main/java/com/fueltracker/util/OcrScanner.kt`: Spatial line sorting, tax keyword filtering, footer phrase exclusion, brand disambiguation, and FuelMath-backed volume validation.
- Unit and instrumented tests in `OcrScannerTest.kt` verifying receipts with tax breakdowns and footer messages.
