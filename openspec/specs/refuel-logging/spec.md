# refuel-logging Specification

## Purpose
Provides rapid, minimal-friction recording of refueling and EV charging events at stations, including automated field calculation and camera OCR capture.

## Requirements

### Requirement: Two-Variable Auto-Calculation
The system SHALL automatically compute the missing third value whenever the user inputs any two out of Volume, Total Cost, and Price per Unit.

#### Scenario: User enters Volume and Total Cost
- **WHEN** user enters 40.0 L volume and €68.00 total cost
- **THEN** system automatically calculates and populates unit price as €1.70 per Liter

#### Scenario: User enters Volume and Unit Price
- **WHEN** user enters 40.0 L volume and €1.70 per Liter unit price
- **THEN** system automatically calculates and populates total cost as €68.00

### Requirement: Full vs Partial Fill Classification
The system SHALL allow users to mark each refueling or charging session as either a full top-up or a partial fill.

#### Scenario: User logs a full fill
- **WHEN** user saves a log with "Full Tank" selected
- **THEN** system marks the fill as complete and triggers consumption calculation against the previous full fill

#### Scenario: User logs a partial fill
- **WHEN** user saves a log with "Partial Fill" selected
- **THEN** system records the expense and volume but defers consumption calculation until the next full fill

### Requirement: On-Demand Gas Station Detection
The system SHALL query device geolocation only on foreground entry to determine the nearest gas station without continuous background tracking.

#### Scenario: User opens refuel screen near known station
- **WHEN** user opens the refuel screen with location permission granted
- **THEN** system queries foreground GPS once, identifies the nearest gas station, and pre-populates the station field

#### Scenario: User denies location permission
- **WHEN** user opens the refuel screen without location permission
- **THEN** system allows manual station entry or leaving station field blank without blocking log completion

### Requirement: Camera OCR for Pump and Odometer
The system SHALL provide optional on-device optical character recognition to extract numeric values from photos of pump displays and vehicle odometers.

#### Scenario: Scanning pump display
- **WHEN** user captures an image of the pump screen showing volume, price, and total
- **THEN** on-device OCR extracts the numbers and populates the corresponding input fields for user confirmation

#### Scenario: Scanning odometer display
- **WHEN** user captures an image of the vehicle instrument cluster odometer
- **THEN** on-device OCR extracts the mileage digits and populates the odometer field
