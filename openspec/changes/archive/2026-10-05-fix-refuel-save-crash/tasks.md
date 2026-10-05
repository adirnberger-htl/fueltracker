# Tasks

## 1. Database & DAO Updates

- [x] 1.1 Update `FuelStationDao` in `Daos.kt` to use `OnConflictStrategy.IGNORE` for station insertions and verify compilation.
- [x] 1.2 Update `seedDefaultDataIfEmpty()` in `FuelTrackerViewModel.kt` to ensure default station `station_local` exists in the database.

## 2. Refuel Log and Price Snapshot Handling

- [x] 2.1 In `FuelTrackerViewModel.kt`, ensure the station entity is persisted before inserting `PriceSnapshot` in `saveRefuelLog`.
- [x] 2.2 In `FuelTrackerViewModel.kt`, persist detected `FuelStation` in `resolveNearestStation()` via `priceRepo.saveStation()`.

## 3. Verification

- [x] 3.1 Add or update unit/repository tests in `RepositoryTest.kt` or `FuelTrackerE2ETest.kt` verifying refuel log and price snapshot insertion with fallback station succeeds without SQLite foreign key violation.
- [x] 3.2 Run `./gradlew test` and verify all tests pass.
