# Proposal: Hybrid Fuel Price Intelligence

## Why

Fuel price recommendations currently rely solely on user-recorded refuel logs, creating severe data sparsity (one snapshot every 1–2 weeks) and rendering intraday price cycle predictions inaccurate on fresh installs. In regional markets like Upper Austria, fuel prices follow strict statutory intraday patterns (12:00 PM price hike under the Austrian Spritpreisverordnung, preceded by morning competitive lows). Integrating external public data via Austria's E-Control Spritpreisrechner alongside a pre-computed regional baseline prior provides immediate, high-accuracy refill predictions in a strictly serverless, low-storage mobile environment.

## What Changes

- Add direct client-side integration with Austria's public E-Control Spritpreisrechner REST API for real-time station fuel prices without requiring a dedicated backend.
- Support optional user-configured custom API key or proxy endpoint in user preferences to prevent rate-limiting while providing a working default.
- Bundle an Upper Austria baseline intraday prior curve asset (7 days x 24 hours, ~1.5 KB) to eliminate cold-start inaccuracies.
- Introduce an online Bayesian / EMA updating engine that blends the regional prior with incoming E-Control API samples and ground-truth refuel entries.
- Introduce a compact 168-bin SQLite statistical profile table to maintain long-term price intelligence while keeping mobile storage overhead strictly below 100 KB.
- Add background opportunistic price sampling via Android `WorkManager` with automatic pruning of raw snapshot records older than 3 days.

## Capabilities

### New Capabilities
None.

### Modified Capabilities
- `price-intelligence`: Adds requirements for external Austrian E-Control price feed querying, regional prior distribution seeding, and compact station price profile aggregation.

## Impact

- **Code**: `PriceIntelligenceEngine`, `PriceRepository`, `FuelTrackerViewModel`, Room entities, and DAOs.
- **Dependencies**: Android `WorkManager` (if not already present), HTTP client (Ktor or Retrofit/OkHttp or `HttpURLConnection`), Kotlinx Serialization / Moshi for parsing E-Control JSON.
- **Data & Migration**: New Room entity `StationPriceProfileEntity` with automated 3-day raw snapshot pruning.
- **Storage & Network**: Storage footprint under 100 KB; network queries restricted to unmetered connections or low-frequency foreground/background sync for user's favorite/nearest stations.
