# Tasks: Hybrid Fuel Price Intelligence

## 1. Prior Baseline Asset & Blending Engine

- [x] 1.1 Create `app/src/main/assets/priors/at_ooe_prior.json` containing 168 hourly price index factors for Upper Austria and verify valid asset loading and parsing via unit test.
- [x] 1.2 Implement prior loading and Bayesian/EMA blending in `PriceIntelligenceEngine` to calculate blended hourly estimates with unit tests covering cold-start (prior only) and accumulated observation weighting.

## 2. Room Profile Storage & Pruning

- [x] 2.1 Add `StationPriceProfileEntity` and `StationPriceProfileDao` to Room schema with database migration and verify entity insertion and 168-bin lookup in database tests.
- [x] 2.2 Implement 3-day raw snapshot pruning logic in `PriceSnapshotDao` and `PriceRepository` with unit tests verifying records older than 72 hours are deleted while profiles remain intact.
- [x] 2.3 Add custom API key and proxy URL fields to `UserPreferencesRepository` with DataStore test coverage.

## 3. E-Control Client & Background Sync

- [x] 3.1 Implement `EControlApiClient` to query `https://api.e-control.at/sprit/1.0/search/gas-stations/by-address` mapping `FuelType` (`DIE`, `SUP`) and coordinates, verified with mock response unit tests.
- [x] 3.2 Implement `PriceSyncWorker` (`WorkManager`) with unmetered network and battery constraints to query E-Control for active/nearest stations and update profile bins, verified with worker tests.

## 4. ViewModel Integration & End-to-End Verification

- [x] 4.1 Update `FuelTrackerViewModel` to load station price profiles and pass them into `PriceIntelligenceEngine.recommendNextRefill`, replacing the empty snapshot list.
- [x] 4.2 Add UI settings inputs in Compose for optional custom API key/proxy URL and verify the complete flow in `FuelTrackerE2ETest`.
