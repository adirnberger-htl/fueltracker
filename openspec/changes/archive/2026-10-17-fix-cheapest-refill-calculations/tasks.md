# Tasks

## 1. Asset & Prior Alignment

- [x] 1.1 Update `at_ooe_prior.json` so day keys match `Calendar.DAY_OF_WEEK` (1 = Sunday, 2..6 = Monday..Friday, 7 = Saturday) and verify `PricePriorTest` parses Sunday and Friday correctly.

## 2. Recommendation Engine Refinements

- [x] 2.1 Implement midday spike-aware window capping (ending at 12:00 for pre-noon dips) and clean midnight bounding in `PriceIntelligenceEngine`, verifying in `EnginesTest`.
- [x] 2.2 Add "today" (`dayOffset == 0L`) support in `formatTargetDay` and target refill timing relative to 15% fuel reserve threshold in `PriceIntelligenceEngine`, verifying in `EnginesTest`.
- [x] 2.3 Add multi-station profile hourly weighted averaging and robust base price fallback hierarchy in `PriceIntelligenceEngine`, verifying in `EnginesTest`.
- [x] 2.4 Set `suggestedDateEpochMs` timestamp to the recommended start hour on the target day rather than raw current wall-clock time, verifying in `EnginesTest`.

## 3. Integration & Test Suite Verification

- [x] 3.1 Update and run existing test suite (`PricePriorTest`, `EnginesTest`, and `FuelTrackerE2ETest`) via `./gradlew test` to verify all calculations and assertions pass.
