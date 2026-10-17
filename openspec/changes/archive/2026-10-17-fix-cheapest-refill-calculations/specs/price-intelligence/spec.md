# Spec Delta

## MODIFIED Requirements

### Requirement: Recommended Fill-Up Time Window
The system SHALL generate an optimal refill recommendation combining predicted depletion schedule and historical price valleys, presenting the target refill timing with unambiguous relative days and localized weekday or calendar date descriptions, ensuring morning recommendation windows terminate at or before 12:00 to avoid Austria's midday statutory price hike, supporting "today" as the target day when fuel is critically low, and triggering only when predicted tank level is within 3 days of reaching 15% reserve capacity.

#### Scenario: Recommending low-cost window before reserve
- **WHEN** predicted fuel tank level is within 3 days of reaching 15% reserve and historical prices consistently drop in the evening
- **THEN** system alerts the user with a recommended time window (e.g., 18:00–20:00), an unambiguous target day description (such as "in 2 days" or "tomorrow"), and expected price savings

#### Scenario: Avoiding midday statutory price hike in recommendation window
- **WHEN** pre-noon hours (10:00–11:59) are determined to be the lowest cost refill period
- **THEN** system sets the recommended window to end at or before 12:00 (e.g., 10:00–12:00 or 11:00–12:00) so the user is not guided to refuel during the 12:00 price surge

#### Scenario: Recommending refill today when reserve is imminent or tank near empty
- **WHEN** predicted fuel level will breach 15% reserve within 24 hours
- **THEN** system provides a recommendation targeting "today" with the earliest upcoming low-cost window

#### Scenario: Suppressing recommendation when fuel is above reserve threshold window
- **WHEN** vehicle fuel tank level is full or more than 3 days away from reaching 15% reserve
- **THEN** system does not present urgent refill alerts for distant weeks

### Requirement: Regional Intraday Prior Baseline
The system SHALL bundle a pre-computed Upper Austria regional hourly price distribution prior matching standard calendar day-of-week indexing and use it as a cold-start baseline for refill recommendations before sufficient local data has been collected, averaging multi-station observations and maintaining calibrated baseline prices when raw snapshots are purged.

#### Scenario: Cold start with no recorded price history
- **WHEN** a vehicle has zero or sparse refuel logs and no local API snapshots
- **THEN** system generates refill recommendations reflecting the Upper Austria statutory daily price cycle (midday 12:00 spike and morning pre-12:00 price dip) derived from the bundled prior using calendar day-of-week indexing (Sunday as day 1 through Saturday as day 7)

#### Scenario: Bayesian blending with local observations
- **WHEN** user logs refuels or API snapshots are recorded for a station
- **THEN** system dynamically shifts prediction weights from the regional prior toward observed local hourly averages as sample count increases

#### Scenario: Bayesian blending with multi-station observations
- **WHEN** price observations are recorded across multiple nearby fuel stations
- **THEN** system averages the station profiles for each hourly bin without dropping stations and shifts prediction weights from the regional prior toward observed hourly averages as sample count increases

#### Scenario: Fallback base price calibration when raw snapshots are absent
- **WHEN** raw price snapshots are empty or pruned older than 3 days
- **THEN** system calibrates the base price using existing station profile averages or recent refuel log prices rather than an uncalibrated fixed constant
