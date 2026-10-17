# Tasks

## 1. Camera Permissions & Storage Reliability

- [x] 1.1 Remove redundant `android.permission.CAMERA` declaration from `app/src/main/AndroidManifest.xml` and verify manifest merges cleanly without camera permission requirements.
- [x] 1.2 Update `ImageStorageHelper.createTempPictureUri` to invoke `tempFile.createNewFile()` prior to `FileProvider.getUriForFile`, and verify `ImageStorageHelperTest` passes.
- [x] 1.3 Migrate `tempCaptureUri` state in `AddRefuelScreen.kt` to `rememberSaveable` with a URI-string saver to preserve the capture target across device rotation and process recreation.

## 2. Spatial Text Ordering & Station Name Extraction

- [x] 2.1 Update `OcrScanner` to sort detected text lines by vertical coordinate (`boundingBox.top` ascending) before processing, ensuring top header lines are parsed first.
- [x] 2.2 Add footer slogan exclusion blacklist (`Vielen Dank`, `Auf Wiedersehen`, `Kassabon`, `Kundenbeleg`, `Terminal`, `UID`, `ATU`) and disambiguate petrol brand `"Total"` from payment totals in `OcrScanner.parseReceiptText`.

## 3. Tax Exclusion & Mathematical Cross-Validation

- [x] 3.1 Implement tax keyword filtering (`MwSt`, `UST`, `VAT`, `Tax`, `Netto`, `Steuer`, `%`) in `OcrScanner` to prevent tax amounts from being selected as fuel volume.
- [x] 3.2 Add mathematical validation and auto-inference using `FuelMath.inferMissingVariable` to verify candidate values against `Volume * UnitPrice ≈ TotalCost` and infer volume when total cost and unit price are present.
- [x] 3.3 Add comprehensive unit tests in `OcrScannerTest.kt` for receipts with VAT/MwSt breakdowns, footer slogans, and table layouts, verifying all test assertions pass.

## 4. Verification & Integration

- [x] 4.1 Execute full test suite (`./gradlew testDebugUnitTest`) and verify zero test regressions across repository engines, repository implementations, and OCR parsers.
