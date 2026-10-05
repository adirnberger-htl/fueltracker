# Spec Delta

## MODIFIED Requirements

### Requirement: Station Price History Tracking
The system SHALL record historical price observations per fuel type and station from user logs and external price feed queries, ensuring station referential integrity is preserved prior to snapshot persistence.

#### Scenario: User logs fuel purchase
- **WHEN** user saves a refuel entry with station, fuel type, and price per liter
- **THEN** system saves a timestamped price snapshot to build local station historical trends

#### Scenario: User logs fuel purchase with default or unregistered station
- **WHEN** user saves a refuel entry with an unregistered station or fallback local station identifier
- **THEN** system ensures a matching station entity exists before recording the price snapshot, avoiding foreign key constraint failures
