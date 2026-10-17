# Tasks

## 1. FileProvider & Storage Setup

- [x] 1.1 Create `app/src/main/res/xml/file_paths.xml` and register `androidx.core.content.FileProvider` in `AndroidManifest.xml` for secure temporary and permanent receipt image URIs; verify compilation.
- [x] 1.2 Implement `ImageStorageHelper.kt` to generate temporary capture URIs, copy staged images to app internal storage (`filesDir/receipts/`), and delete orphaned files; verify file operations with unit tests.

## 2. Receipt OCR Engine & Heuristics

- [x] 2.1 Implement `parseReceiptText(rawText: String)` in `OcrScanner.kt` to extract Volume, Total Cost, Unit Price, and Station Brand/Name using multi-lingual receipt keyword patterns.
- [x] 2.2 Add `recognizeTextFromUri(context: Context, uri: Uri)` in `OcrScanner.kt` using ML Kit `InputImage.fromFilePath` for memory-efficient text extraction.
- [x] 2.3 Add unit tests in `OcrScannerTest.kt` verifying `parseReceiptText` across diverse sample gas receipts (German, Austrian, and English formats).

## 3. Camera Capture & Form UI Integration

- [x] 3.1 Replace the mock `showOcrSimDialog` in `AddRefuelScreen.kt` with `rememberLauncherForActivityResult` contracts for `TakePicture` (camera) and `PickVisualMedia` (gallery).
- [x] 3.2 Add an image action dialog/bottom sheet letting users choose between "Take Photo with Camera" and "Choose from Gallery".
- [x] 3.3 Add receipt photo preview card with thumbnail, retake button, and delete attachment button in `AddRefuelScreen.kt`.
- [x] 3.4 Wire OCR results to automatically populate volume, total cost, price per unit, and station name in the input fields with `recalculateMissing()` trigger.
- [x] 3.5 Persist staged receipt image to internal storage on "Save Refuel Log" and set `receiptImagePath` on the generated `RefuelLog`.

## 4. Lifecycle Cleanup & Verification

- [x] 4.1 Update `RefuelRepositoryImpl.deleteLog` to delete associated receipt image files on disk when a refuel log is deleted; verify with repository unit test.
- [x] 4.2 Run `./gradlew testDebugUnitTest` and verify all tests pass without regression.
