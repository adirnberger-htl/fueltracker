# Proposal

## Why

Saving a refuel log (via manual entry or camera OCR quick scan) currently causes an unhandled `SQLiteConstraintException: FOREIGN KEY constraint failed` when persisting a price snapshot. This happens because `price_snapshots.stationId` references `fuel_stations.id`, but default fallback (`"station_local"`) or newly entered stations are not registered in the database before the snapshot is saved.

## What Changes

- Ensure default station (`"station_local"`) is seeded and any custom/resolved station is upserted into the `fuel_stations` table prior to recording price snapshots.
- Ensure refuel log saving gracefully associates with a valid station entity or safely handles price snapshot recording without violating SQLite foreign key constraints.
- Seed default local station entity in database initialization alongside existing default vehicle and log seeds.

## Capabilities

### New Capabilities
*(None)*

### Modified Capabilities
- `price-intelligence`: Update Station Price History Tracking requirement to mandate automatic station registration / fallback entity persistence so price snapshots never fail foreign key constraints on save.

## Impact

- `app/src/main/java/com/fueltracker/data/local/AppDatabase.kt` / `RepositoryImpls.kt`
- `app/src/main/java/com/fueltracker/ui/viewmodel/FuelTrackerViewModel.kt`
- `app/src/main/java/com/fueltracker/data/local/dao/Daos.kt`
