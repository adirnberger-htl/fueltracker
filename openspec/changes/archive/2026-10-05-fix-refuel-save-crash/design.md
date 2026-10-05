# Design

## Context

See proposal.md for motivation. `PriceSnapshotEntity` enforces a foreign key constraint pointing to `FuelStationEntity.id`. When refuel logs are saved without a prior station lookup or with default fallback (`"station_local"`), no station row exists in `fuel_stations`, causing SQLite foreign key violation on snapshot insertion.

## Goals / Non-Goals

**Goals:**
- Guarantee referential integrity for all price snapshots without SQLite foreign key exceptions.
- Ensure default fallback station `"station_local"` exists in `fuel_stations`.
- Ensure dynamically detected or entered stations (like GPS resolution `gps_station_1`) are persisted before referencing in snapshots.

**Non-Goals:**
- Redesigning the entire FuelStation database schema or foreign key hierarchy.
- Adding complex external station sync APIs.

## Decisions

### Decision 1: Seed default fallback station in database initialization
- **Rationale**: Any refuel log created without an explicit station will fall back to `"station_local"`. Ensuring this record exists by default in `AppDatabase` / `seedDefaultDataIfEmpty()` guarantees foreign key validity.
- **Alternatives Considered**: Making `stationId` nullable or removing foreign key; rejected to preserve relational consistency for price analytics.

### Decision 2: Add `OnConflictStrategy.IGNORE` to `FuelStationDao.insertStation`
- **Rationale**: Allows idempotent upserting/ensuring of station records (e.g. before snapshot recording or when resolving GPS station) without throwing primary key duplicate errors.
- **Alternatives Considered**: Doing manual `SELECT` before `INSERT`; rejected because `OnConflictStrategy.IGNORE` is atomic and faster in Room.

### Decision 3: Ensure station existence in repository / ViewModel before snapshot insert
- **Rationale**: Before inserting `PriceSnapshotEntity`, verify or insert the station entity (`priceRepo.saveStation`) so foreign key validation is always satisfied even if the user typed an ad-hoc station name.

## Risks / Trade-offs

- *[Risk]* Existing app installs have empty `fuel_stations` table if already past initial launch → *Mitigation*: Ensure `seedDefaultDataIfEmpty()` checks and inserts `"station_local"` regardless of whether vehicles were already seeded, and `saveRefuelLog` ensures fallback station existence before recording snapshots.
