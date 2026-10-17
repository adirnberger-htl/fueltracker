# Proposal

## Why

Currently, tapping the camera icon in the refuel screen only opens a simulation dialog with hardcoded mock numbers instead of launching the phone camera. Users cannot take real photos of fuel receipts or pump screens to record refueling, and captured receipt images are never persisted even though database schema supports storing receipt image paths.

## What Changes

- Implement real camera photo capture in `AddRefuelScreen` using Android `ActivityResultContracts.TakePicture` with `FileProvider` (and fallback to photo gallery picker).
- Add runtime camera and storage/media permission handling in Compose.
- Enhance OCR scanning in `OcrScanner` to extract volume, total cost, unit price, and station name from printed fuel receipts in addition to pump screens.
- Persist captured receipt image files to application storage and save the file path to `RefuelLog.receiptImagePath`.
- Display a receipt thumbnail preview in the refuel logging screen with an option to retake or remove the photo.

## Capabilities

### New Capabilities
<!-- None -->

### Modified Capabilities
- `refuel-logging`: Expand Camera OCR requirement to include printed fuel receipt document scanning, real camera photo capture integration, and receipt image attachment persistence.

## Impact

- **UI**: `AddRefuelScreen.kt` replaces mock `showOcrSimDialog` with camera photo capture launcher and photo preview.
- **Vision / OCR**: `OcrScanner.kt` updated with receipt parsing heuristics (`parseReceiptText`).
- **Android Manifest & Config**: Add `androidx.core.content.FileProvider` in `AndroidManifest.xml` and `file_paths.xml` for camera image output URIs.
- **Dependencies**: No external network dependencies; leverages existing Google ML Kit Text Recognition and AndroidX Activity Compose contracts.
