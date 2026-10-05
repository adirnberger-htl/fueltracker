# Spec Delta: price-intelligence

## MODIFIED Requirements

### Requirement: Recommended Fill-Up Time Window
The system SHALL generate an optimal refill recommendation combining predicted depletion schedule and historical price valleys, presenting the target refill timing with unambiguous relative days and localized weekday or calendar date descriptions.

#### Scenario: Recommending low-cost window before reserve
- **WHEN** predicted fuel tank level is within 3 days of reaching 15% reserve and historical prices consistently drop in the evening
- **THEN** system alerts the user with a recommended time window (e.g., 18:00–20:00), an unambiguous target day description (such as "in 2 days" or "tomorrow"), and expected price savings
