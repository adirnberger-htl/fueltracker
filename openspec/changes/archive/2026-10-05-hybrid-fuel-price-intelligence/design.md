# Design: Hybrid Fuel Price Intelligence

## Context

See `proposal.md` for motivation. The application is a serverless Android app using Room (SQLite), Kotlin Coroutines/Flow, Jetpack Compose, and Preferences DataStore. Currently, `PriceIntelligenceEngine` computes hourly price averages across all recorded logs without distinguishing days of the week, and relies entirely on user-logged fill-ups (empty by default).

## Goals / Non-Goals

**Goals:**
- Provide reliable refill window recommendations immediately on install using an Upper Austria baseline prior.
- Fetch real-time Austrian fuel prices via E-Control's public REST endpoint directly from the mobile client.
- Maintain a bounded storage footprint (< 100 KB) using fixed 168-bin (7 days x 24 hours) station price profile tables.
- Blend regional prior and local empirical observations via an online Bayesian / EMA updating scheme.
- Allow optional custom API keys or proxy endpoints configured through settings.

**Non-Goals:**
- Operating or relying on any centralized backend proxy or cloud database.
- Building a generalized global multi-country price scraper (scoped specifically to Austria / Upper Austria).
- High-frequency live price streaming (fuel prices change only by statutory schedule and gradual competition).

## Decisions

### Decision 1: Direct Client Integration with E-Control REST API
- **Approach**: Android app communicates directly via HTTPS to `https://api.e-control.at/sprit/1.0/search/gas-stations/by-address`.
- **Rationale**: Keeps app 100% serverless with zero cloud infrastructure cost.
- **Alternatives Considered**:
  - *Cloudflare Worker / Lambda proxy*: Adds maintenance burden and server costs. Rejected to preserve serverless local-first philosophy.

### Decision 2: Bundled Upper Austria Intraday Prior Curve Asset
- **Approach**: Bundle `at_ooe_prior.json` in Android assets containing 168 hourly price index multipliers reflecting the Austrian 12:00 spike and morning valleys.
- **Rationale**: Solves cold-start instantly without requiring months of historical user logging.
- **Alternatives Considered**:
  - *Flat average default*: Already in use (defaults to 19:00 with no intraday curve); causes inaccurate recommendations.

### Decision 3: Fixed-Size Hourly Profile Matrix (Room DB)
- **Approach**: Store running statistics in a dedicated table `station_price_profiles` keyed by `(station_id, fuel_type, day_of_week, hour_of_day)`.
  - Columns: `running_avg_price`, `sample_count`, `last_updated_epoch_ms`.
  - Max storage: 5 stations * 168 bins = 840 rows (~50 KB total).
  - Raw `price_snapshots` are pruned after 72 hours (3 days) via background worker or database hook.
- **Rationale**: Prevents uncontrolled SQLite database growth on mobile devices while preserving years of hourly distribution intelligence.
- **Alternatives Considered**:
  - *Retain all raw snapshots indefinitely*: Would grow by tens of thousands of rows per year, degrading query performance on budget phones.

### Decision 4: Online Bayesian / EMA Blending Engine
- **Approach**: When predicting the price for hour $h$ and day $d$:
  $$\hat{P}(h, d) = w \cdot \mu_{\text{local}}(h, d) + (1 - w) \cdot P_{\text{prior}}(h, d)$$
  where weight $w = \min(1.0, \frac{N}{10})$ scales with the number of recorded local samples $N$.
- **Rationale**: Smooth transition from general Upper Austrian trend to specific neighborhood station behaviors as data accumulates.

### Decision 5: Periodic Sync via WorkManager
- **Approach**: A `PriceSyncWorker` running periodically (e.g. every 6–12 hours) with constraints: `NetworkType.UNMETERED` and `BatteryNotLow`.
- **Rationale**: Avoids draining battery or consuming cellular mobile data plans.

## Risks / Trade-offs

- **[E-Control API Rate Limiting or Temporary Downtime]** → App uses exponential backoff and gracefully falls back to cached profiles and baseline prior. Custom API key/proxy setting allows users to bypass shared IP throttles.
- **[No Location Permission Granted]** → App falls back to the user's saved/favorite station coordinates or default Linz/Upper Austria regional centroid.
- **[Network Quota / Battery Drain]** → WorkManager enforces unmetered network constraints and limits station queries to user's top 3–5 nearest or favorite stations.

## Migration Plan

1. Increment Room database version from current schema with automatic migration adding the `station_price_profiles` table.
2. Initialize default Upper Austria prior in the database or asset loader during application first start.
3. Add DataStore keys for `price_api_custom_key` and `price_api_custom_url`.
