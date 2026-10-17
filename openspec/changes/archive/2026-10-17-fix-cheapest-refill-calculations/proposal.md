# Proposal

## Why

The current refill recommendation calculations in `PriceIntelligenceEngine` contain several algorithmic inaccuracies and edge-case errors:
1. Recommending a 2-hour window starting at 11:00 (`11:00 - 13:00`), which steers users directly into Austria's statutory midday price hike at 12:00 sharp (*Spritpreisverordnung*).
2. Forcing target day offsets to at least 1 day ("tomorrow"), preventing timely recommendations for critically low tanks running empty today.
3. Collapsing base price estimation to a hardcoded default (€1.65) whenever raw snapshots are empty, causing unobserved hours to appear artificially cheaper than observed stations.
4. Day-of-week alignment mismatch in bundled `at_ooe_prior.json` relative to `Calendar.DAY_OF_WEEK` (Sunday/Friday inverted).
5. Unconditional recommendation generation even when the tank is full, targeting 0% total exhaustion rather than the 15% reserve threshold.

Fixing these ensures accurate, safe, and cost-effective refill recommendations for Austrian drivers.

## What Changes

- **Spike-Aware Window Sizing**: Truncate or cap refill windows around the midday dip so they end at 12:00 (e.g., `10:00 - 12:00`), preventing recommendations from overlapping the statutory 12:00 price surge.
- **Today Window Support**: Allow `dayOffset == 0` ("today") when estimated empty time is within 24 hours, updating `formatTargetDay` to format today's window cleanly.
- **Robust Base Price Calibration**: Fall back to station profile running averages or recent log unit prices rather than hardcoded `1.65` when raw snapshots are empty.
- **Multi-Station Profile Aggregation**: Average profile prices across stations for each hour bin instead of picking an arbitrary single station via map overwrite.
- **JSON Prior Calendar Alignment**: Align `at_ooe_prior.json` day keys with Java `Calendar.DAY_OF_WEEK` (1 = Sunday ... 7 = Saturday).
- **Reserve-Threshold Gating**: Calculate target day and alert timing relative to reaching 15% reserve tank capacity rather than complete tank exhaustion (0%).
- **Window Formatting Boundary**: Handle midnight wrap-arounds cleanly without confusing labels like `23:00 - 01:00`.

## Capabilities

### Modified Capabilities
- `price-intelligence`: Update requirements for refill time window generation (midday spike avoidance, today target support, reserve threshold gating, and multi-station profile averaging).

## Impact

- **Domain**: `com.fueltracker.domain.engine.PriceIntelligenceEngine`, `com.fueltracker.domain.engine.PricePriorLoader`, `com.fueltracker.domain.engine.DepletionForecastEngine`.
- **Assets**: `app/src/main/assets/priors/at_ooe_prior.json`.
- **UI / ViewModel**: `com.fueltracker.ui.viewmodel.FuelTrackerViewModel` and `DashboardScreen`.
- **Tests**: `EnginesTest`, `PricePriorTest`, and `FuelTrackerE2ETest`.
