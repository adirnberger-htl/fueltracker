# Spec Delta: price-intelligence

## ADDED Requirements

### Requirement: Austrian E-Control Price Feed Querying
The system SHALL query the official Austrian E-Control Spritpreisrechner REST API for nearby fuel stations and real-time prices for the vehicle's primary fuel type without requiring a remote intermediary server.

#### Scenario: Querying prices for nearby stations
- **WHEN** price update is triggered (foreground or background worker) and location coordinates are available
- **THEN** system fetches real-time fuel prices for Diesel or Super 95 from the E-Control endpoint and records observed station price snapshots

#### Scenario: E-Control API error or network failure
- **WHEN** E-Control API request fails, times out, or returns invalid data
- **THEN** system logs the failure gracefully and falls back to existing local price profiles and regional baseline priors without interrupting user flow

### Requirement: Regional Intraday Prior Baseline
The system SHALL bundle a pre-computed Upper Austria regional hourly price distribution prior and use it as a cold-start baseline for refill recommendations before sufficient local data has been collected.

#### Scenario: Cold start with no recorded price history
- **WHEN** a vehicle has zero or sparse refuel logs and no local API snapshots
- **THEN** system generates refill recommendations reflecting the Upper Austria statutory daily price cycle (midday 12:00 spike and morning pre-12:00 price dip) derived from the bundled prior

#### Scenario: Bayesian blending with local observations
- **WHEN** user logs refuels or API snapshots are recorded for a station
- **THEN** system dynamically shifts prediction weights from the regional prior toward observed local hourly averages as sample count increases

### Requirement: Statistical Profile Aggregation and Snapshot Pruning
The system SHALL maintain a fixed-size 168-hour statistical profile table per station and fuel type, and SHALL automatically purge raw price snapshots older than 3 days to constrain mobile storage below 100 KB.

#### Scenario: Aggregating incoming price observation into hourly bins
- **WHEN** a new price snapshot is recorded for a station and fuel type
- **THEN** system updates the corresponding day-of-week and hour-of-day profile bin using online running average and sample count increments

#### Scenario: Automatic pruning of stale raw snapshots
- **WHEN** raw price snapshots reach an age older than 3 days (72 hours)
- **THEN** system purges old raw snapshot rows while preserving the aggregated statistical profile bins

### Requirement: Configurable API Key and Custom Endpoint
The system SHALL provide a default connection configuration for the public Austrian price feed while allowing the user to configure an optional custom API key or proxy URL in settings to prevent rate limiting.

#### Scenario: Default configuration
- **WHEN** user has not provided custom API credentials
- **THEN** system uses the default public E-Control REST endpoint and rate-limiting safeguards

#### Scenario: User provides custom API key or proxy URL
- **WHEN** user saves a custom API key or proxy URL in user preferences
- **THEN** system routes all subsequent price feed requests through the configured URL with the provided credentials
