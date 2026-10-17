# Design

## Context

See `proposal.md` - Why.
The application attaches fuel receipts to refueling entries by launching an external camera activity via `ActivityResultContracts.TakePicture()` and subsequently running on-device Google ML Kit Text Recognition (`com.google.mlkit:text-recognition`).

Currently:
1. `android.permission.CAMERA` in `AndroidManifest.xml` triggers Android's intent security checks, causing `SecurityException` because the app never requested runtime permission for `ACTION_IMAGE_CAPTURE`.
2. Temporary capture URIs in `AddRefuelScreen.kt` use ephemeral `remember` instead of `rememberSaveable`, causing pending URIs to drop to `null` on camera process recreation or rotation.
3. ML Kit's `visionText.text` concatenates text blocks in arbitrary detection order, causing bottom footer slogans to precede header text.
4. Fallback numeric parsing picks the minimum decimal number as volume, which frequently matches tax amounts (e.g., MwSt / VAT) rather than fuel liters.

## Goals / Non-Goals

**Goals:**
- Provide zero-failure hardware camera photo capture across Android devices without runtime permission barriers.
- Retain staged image URIs across camera launch, rotation, and lifecycle recreation.
- Guarantee top-to-bottom spatial line order for OCR text parsing.
- Exclude tax numbers and footer greetings from receipt metric extraction.
- Cross-validate volume, unit price, and total cost using `FuelMath` invariant relationships (`Volume * UnitPrice ≈ TotalCost`).

**Non-Goals:**
- Implementing an in-app custom CameraX viewfinder (system camera intent via `TakePicture` remains the UX).
- Cloud OCR integrations (parsing remains 100% on-device and offline).

## Decisions

### Decision 1: Remove `android.permission.CAMERA` from Manifest
- **Choice**: Remove `<uses-permission android:name="android.permission.CAMERA" />` from `AndroidManifest.xml`.
- **Rationale**: According to official Android documentation, apps using `MediaStore.ACTION_IMAGE_CAPTURE` (delegating capture to the system camera app) do not require `CAMERA` permission. Declaring it in the manifest forces Android 6.0+ to demand runtime permission before firing the intent. Removing it allows seamless capture on all devices.
- **Alternative considered**: Request runtime permission with `ActivityResultContracts.RequestPermission()`. Rejected because unnecessary permission dialogs degrade user experience for external camera intents.

### Decision 2: Persist Pending Capture URI with `rememberSaveable`
- **Choice**: Store `tempCaptureUri` as `rememberSaveable` using its string representation, and explicitly call `tempFile.createNewFile()` before creating the `FileProvider` URI in `ImageStorageHelper`.
- **Rationale**: Camera apps consume significant RAM, often triggering background activity recreation or orientation resets. `rememberSaveable` preserves the capture target across lifecycle events. Creating the file on disk prevents camera app write failures.

### Decision 3: Spatial Line Ordering via Bounding Boxes
- **Choice**: In `OcrScanner`, extract all `Text.Line` instances from `visionText.textBlocks` and sort them by `boundingBox.top` ascending before generating line text.
- **Rationale**: Guarantees that the physical top of the receipt (station name, address) is processed first, and the physical bottom (totals, tax breakdown, footer slogans) is processed last.

### Decision 4: Tax Keyword Exclusion & Multiplier Matching
- **Choice**: Discard any lines containing tax keywords (`mwst`, `ust`, `vat`, `tax`, `netto`, `steuer`, `%`, `prozent`) from volume and pump candidate lists. Support volume expressions with multipliers (e.g. `45.20 x 1.659` or `45.20 * 1.659`).
- **Rationale**: Tax amounts are often the smallest numbers on a receipt (e.g., 10-15 EUR), which tricked `parsePumpText`'s `minOrNull()` logic into selecting tax instead of liters.

### Decision 5: FuelMath Invariant Validation
- **Choice**: Use `FuelMath.inferMissingVariable` to mathematically cross-check candidates: `Volume * UnitPrice ≈ TotalCost` within 1.5% tolerance. If volume is missing from direct regex matching, infer it from `TotalCost / UnitPrice`.
- **Rationale**: Total cost and unit price are generally the most salient numbers on fuel receipts. Enforcing mathematical consistency prevents erroneous volume assignment.

### Decision 6: Station Name Disambiguation & Footer Blacklist
- **Choice**: Blacklist footer phrases (`vielen dank`, `besuch`, `auf wiedersehen`, `kundenbeleg`, `kassabon`, `zahlung`, `terminal`, `uid`, `atu`). Disambiguate the brand `"Total"` by requiring that the line does not contain prices, currency words, or total amount indicators (`gesamt`, `eur`, `summe`, `amount`).
- **Rationale**: Prevents footer greetings and the payment total line from being misidentified as the filling station name.

## Risks / Trade-offs

- **[Risk]** Some receipts have heavily degraded print quality where unit price or total cannot be recognized.
  → **Mitigation**: The system populates whatever valid fields are recognized and leaves missing fields for user manual input, without blocking saving.
- **[Risk]** Multi-column receipts where items and prices are placed in separate horizontal blocks.
  → **Mitigation**: Spatial sorting groups lines by vertical position; tolerance clustering ensures words across the same horizontal plane are evaluated together.
