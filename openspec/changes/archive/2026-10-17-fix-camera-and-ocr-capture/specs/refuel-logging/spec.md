# Spec Delta

## MODIFIED Requirements

### Requirement: Camera OCR for Pump and Odometer
The system SHALL provide optional on-device optical character recognition to extract numeric values and station names from photos of pump displays, printed fuel receipts, and vehicle odometers via hardware camera capture or image selection, applying spatial line ordering, tax-exclusion heuristics, and mathematical consistency checks.

#### Scenario: Scanning pump display
- **WHEN** user captures an image of the pump screen showing volume, price, and total
- **THEN** on-device OCR extracts the numbers and populates the corresponding input fields for user confirmation

#### Scenario: Scanning odometer display
- **WHEN** user captures an image of the vehicle instrument cluster odometer
- **THEN** on-device OCR extracts the mileage digits and populates the odometer field

#### Scenario: Scanning printed fuel receipt
- **WHEN** user captures an image of a printed fuel station receipt
- **THEN** on-device OCR extracts total cost, volume, unit price, and station name, and pre-populates the matching refuel form fields

#### Scenario: Scanning printed fuel receipt with tax breakdown
- **WHEN** user captures an image of a printed fuel station receipt containing tax breakdowns (such as MwSt, VAT, Netto, or percentage rates)
- **THEN** on-device OCR excludes tax amounts from candidate fuel volumes and extracts the true fuel volume, unit price, and total cost

#### Scenario: Scanning printed fuel receipt with footer text
- **WHEN** user captures an image of a printed fuel station receipt containing top station headers and bottom footer messages (such as greeting or payment transaction notes)
- **THEN** on-device OCR parses text lines in physical spatial order from top to bottom, extracting the station brand or header without selecting bottom footer text

#### Scenario: Mathematical cross-validation of OCR refuel metrics
- **WHEN** OCR extracts ambiguous numeric candidates from a receipt or pump image
- **THEN** the system validates candidates against the mathematical relationship (Volume * Unit Price ≈ Total Cost within rounding tolerance) before assigning values to form fields

### Requirement: Receipt Image Attachment Persistence
The system SHALL store captured fuel receipt images in internal application storage and persist the relative file path with the refuel log entry, ensuring reliable camera launch and state persistence across lifecycle events.

#### Scenario: Attaching receipt photo to refuel log
- **WHEN** user takes a photo or selects an image for a refueling entry and saves the log
- **THEN** the system persists the image file securely to internal app storage and records the image path in the refuel record

#### Scenario: Attaching receipt photo via camera capture
- **WHEN** user taps "Take Photo with Camera" on the refuel entry screen
- **THEN** the system prepares the storage URI and launches the camera interface without permission denial crashes, preserving the pending capture across device orientation changes and process recreation

#### Scenario: Attaching receipt photo from gallery
- **WHEN** user selects an image from the photo library for a refueling entry and saves the log
- **THEN** the system copies the image securely to internal app storage and records the image path in the refuel record

#### Scenario: Reviewing or removing attached receipt photo
- **WHEN** user views an attached receipt image on the refuel entry screen
- **THEN** user can preview the captured image, retake the photo, or delete the attachment before saving
