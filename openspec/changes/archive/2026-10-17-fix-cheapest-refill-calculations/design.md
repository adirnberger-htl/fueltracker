# Design

## Context

See `proposal.md` for motivation. The domain layer computes depletion forecasts and refill recommendations locally via `DepletionForecastEngine` and `PriceIntelligenceEngine`. Current refill logic exhibits calculation inaccuracies in window bounding, target day offsets, base price resolution, multi-station handling, and prior asset day-of-week indexing.

## Goals / Non-Goals

**Goals:**
- Guarantee recommendation windows never span across Austria's statutory 12:00 price surge when the pre-noon dip is selected.
- Enable "today" (`targetDayOffset == 0`) for tanks depleting within 24 hours.
- Fix base price estimation in `PriceIntelligenceEngine` to prioritize observed station profile averages over hardcoded fallback constants.
- Average station profiles across stations per hourly bin to prevent arbitrary single-station profile overwriting.
- Fix day indexing in `at_ooe_prior.json` to match Java `Calendar.DAY_OF_WEEK` (1 = Sunday ... 7 = Saturday).
- Target refill window relative to reaching 15% reserve fuel rather than total dry stall (0%).

**Non-Goals:**
- Modifying Room database tables or schemas (in-memory aggregation and domain logic only).
- Altering E-Control API querying or network workers.

## Decisions

### Decision 1: Midday Spike-Aware Window Formatting
In Austria, stations are prohibited from raising fuel prices after 12:00 except at exactly 12:00 sharp. Prices are consistently lowest at 10:00–11:59 and jump sharply at 12:00.
- When `cheapestHour == 11`, cap the window at 12:00 (e.g. `10:00 - 12:00` or `11:00 - 12:00`).
- For other hours, use `cheapestHour` to `min(24, cheapestHour + 2)`. If `cheapestHour == 23`, format as `23:00 - 24:00` to prevent confusing `23:00 - 01:00` wraps.

*Alternative considered:* Always 1-hour window. *Rejected:* A 2-hour window gives practical flexibility for drivers, provided it doesn't cross the noon hike.

### Decision 2: Target Day Offset and Today Support
- Calculate days until reaching 15% reserve:
  ```kotlin
  val reserveUnits = vehicle.capacityUnits * 0.15
  val usableUnits = max(0.0, forecast.remainingUnits - reserveUnits)
  val daysUntilReserve = if (dailyKm > 0) ((usableUnits * 100.0) / consumptionRate) / dailyKm else 3.0
  ```
- If `daysUntilReserve <= 1.0`, allow `targetDayOffset = 0L` ("today").
- Update `formatTargetDay`:
  - `0L` -> `"today ($dateFormatted)"`
  - `1L` -> `"tomorrow ($dateFormatted)"`
  - `> 1L` -> `"in $dayOffset days ($dateFormatted)"`

### Decision 3: Base Price Calibration Fallback Hierarchy
When `snapshots` are empty (default state in ViewModel), calibrate `effectiveBasePrice` using:
1. Average of active `snapshots` (if present).
2. Average of `stationProfiles.map { it.runningAvgPrice }` (if present).
3. Recent vehicle refuel log unit price.
4. Fallback to `prior.defaultBaselinePrice`.

*Rationale:* Prevents unobserved hourly bins from dropping down to hardcoded €1.65 when real market prices are higher, which previously created synthetic "cheap" hours.

### Decision 4: Multi-Station Hourly Profile Aggregation
Instead of `.associateBy { it.hourOfDay }` (which discards all but the last station):
- Group by `hourOfDay`:
  ```kotlin
  val profileByHour = stationProfiles
      .filter { it.fuelType == fuelType && it.dayOfWeek == targetDayOfWeek }
      .groupBy { it.hourOfDay }
      .mapValues { (_, profiles) ->
          val totalSamples = profiles.sumOf { it.sampleCount }
          val weightedAvg = profiles.sumOf { it.runningAvgPrice * it.sampleCount } / max(1, totalSamples)
          StationPriceProfile(
              stationId = "composite",
              fuelType = fuelType,
              dayOfWeek = targetDayOfWeek,
              hourOfDay = profiles.first().hourOfDay,
              runningAvgPrice = weightedAvg,
              sampleCount = totalSamples
          )
      }
  ```

### Decision 5: Calendar Day-of-Week Indexing in JSON Asset
Update `at_ooe_prior.json` so:
- `"1"` = Sunday (weekend curve)
- `"2"` through `"6"` = Monday through Friday (weekday curve)
- `"7"` = Saturday (weekend curve)

## Risks / Trade-offs

- **Risk**: Existing unit test `testPriceIntelligenceEngine` asserted `"19:00 - 21:00"` and specific savings amounts.
  → *Mitigation*: Verify tests and adjust expectations where window capping or today formatting legitimately changes the result.
- **Risk**: Vehicle with zero logs or odometer movements could calculate 0 usable units.
  → *Mitigation*: Fall back gracefully to `1L` ("tomorrow") if forecast has no movement or tank capacity is unknown.
