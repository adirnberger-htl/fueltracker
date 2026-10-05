# efficiency-analytics Specification

## Purpose
Calculates fuel and electric consumption metrics, tracks efficiency trends over time, and benchmarks performance against manufacturer baseline ratings.

## Requirements

### Requirement: Consumption Calculation Between Full Fills
The system SHALL calculate average consumption across consecutive full refuels including intermediate partial fills.

#### Scenario: Standard consecutive full fills
- **WHEN** user logs a full fill at 10,500 km with 50.0 L following a previous full fill at 10,000 km
- **THEN** system computes consumption as 10.0 L/100km for the 500 km interval

#### Scenario: Full fill after intermediate partial fills
- **WHEN** user logs a full fill after two intermediate partial fills
- **THEN** system aggregates all fuel volumes added during the interval and calculates consumption against the total distance between the two full fills

### Requirement: Efficiency Benchmarking and Eco-Scoring
The system SHALL compare actual consumption against the vehicle's configured baseline and display an efficiency score.

#### Scenario: Consumption exceeds baseline significantly
- **WHEN** actual consumption is more than 15% above the vehicle baseline
- **THEN** system flags the interval as inefficient (warning indicator) and displays the percentage variance

#### Scenario: Consumption aligns with or beats baseline
- **WHEN** actual consumption is within 5% of or below baseline
- **THEN** system flags the interval as efficient (eco indicator) and displays positive economy feedback
