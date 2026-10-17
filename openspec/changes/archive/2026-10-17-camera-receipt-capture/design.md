# Design

## Context

See `proposal.md` for motivation and `specs/refuel-logging/spec.md` for behavioral requirements.

The existing refueling screen [`AddRefuelScreen.kt`](file:///home/arthur/AndroidStudioProjects/fueltracker/app/src/main/java/com/fueltracker/ui/screens/AddRefuelScreen.kt) currently contains a stubbed `CameraAlt` icon button that launches an `AlertDialog` applying hardcoded sample numbers. [`OcrScanner.kt`](file:///home/arthur/AndroidStudioProjects/fueltracker/app/src/main/java/com/fueltracker/util/OcrScanner.kt) uses Google ML Kit text recognition, but only parses simple pump numbers (`parsePumpText`) and odometer digits (`parseOdometerText`).

The database entity [`RefuelLogEntity`](file:///home/arthur/AndroidStudioProjects/fueltracker/app/src/main/java/com/fueltracker/data/local/entity/Entities.kt) already has a `receiptImagePath: String?` column, but it is currently never populated.

## Goals / Non-Goals

**Goals:**
- Enable taking real photos with device camera and picking images from device gallery.
- Provide on-device ML Kit OCR recognition from captured image files.
- Parse printed station receipts for Volume, Total Cost, Unit Price, and Station Name.
- Preview captured receipt image in the refuel logging form.
- Persist confirmed receipt images into app-internal storage (`filesDir/receipts/`) and store file paths in `RefuelLog.receiptImagePath`.

**Non-Goals:**
- Custom CameraX embedded viewfinder with live augmented reality bounding boxes (system camera app intent is simpler, lighter, and provides superior camera hardware optimization).
- Cloud receipt OCR or cloud image storage (remains 100% private and offline-capable).
- General accounting expense itemization (we only extract refueling metrics: volume, unit price, total price, station).

## Decisions

### 1. Camera Integration via `ActivityResultContracts.TakePicture` + `FileProvider`
- **Decision**: Use Android's standard `TakePicture` contract pointing to a secure cache Uri provided by `androidx.core.content.FileProvider`. Also provide `PickVisualMedia` for selecting existing photos from gallery.
- **Rationale**: Avoids heavyweight CameraX lifecycle management and camera orientation bugs across OEM hardware. Native camera app automatically handles exposure, HDR, auto-focus, and flash.
- **Alternatives Considered**:
  - `TakePicturePreview`: Rejected because it only returns a downscaled thumbnail bitmap, degrading OCR recognition accuracy on receipt text.
  - CameraX: Rejected because it adds ~10MB APK overhead and substantial preview UI complexity without meaningful user benefit over system camera.

### 2. Receipt Parsing Heuristics in `OcrScanner`
- **Decision**: Add `parseReceiptText(rawText: String): ParsedReceiptData` combining regex patterns for European and US gas station receipt formats:
  - Total Cost: Detects labels like `SUMME`, `TOTAL`, `BETRAG`, `GESAMT`, `ENDSUMME`, `EUR`, `€` followed by decimal currency.
  - Volume: Detects decimal quantities followed by `L`, `l`, `LTR`, `LITER`, or `KWH`.
  - Unit Price: Detects patterns like `/L`, `EUR/L`, `€/L`, or defaults to `totalCost / volume` via `FuelMath`.
  - Station: Inspects top 3 lines of OCR text or scans for major brand keywords (Aral, Shell, BP, OMV, Jet, Eni, Total, Avia, Esso).
- **Rationale**: Station receipts vary wildly in layout. Keyword and line-based heuristic extraction handles noisy receipts gracefully and feeds extracted numbers directly into the existing `recalculateMissing()` auto-calc pipeline.

### 3. File Storage Lifecycle for Receipt Images
- **Decision**:
  - Staging: Photos are initially saved into `cacheDir/receipt_captures/temp_receipt.jpg`.
  - Confirmation: When user presses "Save Refuel Log", the temp image is copied to `filesDir/receipts/<uuid>.jpg` and its path is saved into `RefuelLog.receiptImagePath`.
  - Cancellation / Discard: If user deletes the preview or leaves the screen, temp file is cleared.
- **Rationale**: Prevents disk clutter from abandoned scans while ensuring saved receipts remain accessible offline permanently within app internal storage.

## Risks / Trade-offs

- **[Risk] Crumpled or faded thermal receipts yielding low OCR confidence**
  - *Mitigation*: Never lock input fields. OCR values pre-populate form fields, but user can edit any field manually. The 2-variable `FuelMath` engine automatically fills in the 3rd variable if one value is misread or unreadable.
- **[Risk] High-resolution camera images causing OOM when processing**
  - *Mitigation*: Pass `Uri` directly to `InputImage.fromFilePath(context, uri)` which handles memory-efficient bitmap subsampling internally within Google ML Kit.
- **[Risk] App storage bloat from many receipt images over time**
  - *Mitigation*: Save compressed JPEG images (85% quality) and delete associated receipt files when a refuel log is deleted via `RefuelRepository.deleteLog()`.
