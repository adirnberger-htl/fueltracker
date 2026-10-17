# Spec Delta

## MODIFIED Requirements

### Requirement: Camera OCR for Pump and Odometer
The system SHALL provide optional on-device optical character recognition to extract numeric values from photos of pump displays, printed fuel receipts, and vehicle odometers via hardware camera capture or image selection.

#### Scenario: Scanning pump display
- **WHEN** user captures an image of the pump screen showing volume, price, and total
- **THEN** on-device OCR extracts the numbers and populates the corresponding input fields for user confirmation

#### Scenario: Scanning odometer display
- **WHEN** user captures an image of the vehicle instrument cluster odometer
- **THEN** on-device OCR extracts the mileage digits and populates the odometer field

#### Scenario: Scanning printed fuel receipt
- **WHEN** user captures an image of a printed fuel station receipt
- **THEN** on-device OCR extracts total cost, volume, unit price, and station name, and pre-populates the matching refuel form fields

## ADDED Requirements

### Requirement: Receipt Image Attachment Persistence
The system SHALL store captured fuel receipt images in internal application storage and persist the relative file path with the refuel log entry.

#### Scenario: Attaching receipt photo to refuel log
- **WHEN** user takes a photo or selects an image for a refueling entry and saves the log
- **THEN** the system persists the image file securely to internal app storage and records the image path in the refuel record

#### Scenario: Reviewing or removing attached receipt photo
- **WHEN** user views an attached receipt image on the refuel entry screen
- **THEN** user can preview the captured image, retake the photo, or delete the attachment before saving
