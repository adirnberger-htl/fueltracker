# Design

## Context

Greenfield Android application designed for rapid offline-first vehicle fuel and charging logging with analytical and predictive capabilities. See `proposal.md` for motivation and `specs/` for behavioral requirements.

## Goals / Non-Goals

**Goals:**
- Provide sub-second offline persistence for logging refueling and EV charging.
- Deliver on-device OCR for pump displays and odometers without server dependencies.
- Build pure Kotlin mathematical engines for consumption calculation, range depletion, and budget projections.
- Architect modular repositories to support local Room database caching and remote fuel price integration.

**Non-Goals:**
- Continuous background GPS tracking or automatic driving trip detection.
- Direct OBD-II dongle hardware integration in initial release.
- Cloud account synchronization or multi-device login (focus is on private, local-first storage).

## Decisions

### 1. Architecture: MVVM with Unidirectional Data Flow (StateFlow)
- **Decision**: Clean Architecture layers (UI -> Domain/UseCases -> Data/Repositories) with MVI-style UI state exposed via Kotlin `StateFlow`.
- **Rationale**: Isolates calculation logic (consumption, forecasting) from UI frameworks, enabling thorough unit testing without Android emulator dependencies.
- **Alternatives Considered**: Direct ViewModel-to-DAO calls (rejected due to high coupling and poor testability of complex forecast algorithms).

### 2. UI Toolkit: Jetpack Compose with Material 3
- **Decision**: Single-Activity Jetpack Compose navigation.
- **Rationale**: Eliminates XML boilerplate, facilitates dynamic reactive state changes (such as instantaneous auto-calculation of the 3rd variable as user types), and natively supports dark theme and dynamic theming.
- **Alternatives Considered**: Traditional Android Views with XML (rejected; slower development iteration).

### 3. Data Persistence: Room SQLite with Coroutines Flow
- **Decision**: Android Room ORM with foreign key cascades between `vehicles`, `refuel_logs`, `fuel_stations`, and `price_snapshots`.
- **Rationale**: Robust transactional guarantees, fast indexed query performance, and reactive UI updates via Flow emissions.
- **Alternatives Considered**: DataStore/Proto (rejected; inadequate for complex relational data and historical queries).

### 4. Vision Processing: Google ML Kit On-Device Text Recognition
- **Decision**: On-device Google ML Kit Vision API running on CameraX preview frames or captured images.
- **Rationale**: Zero network latency, zero cloud API costs, operates fully offline at underground or rural gas stations, and protects user privacy.
- **Alternatives Considered**: Cloud OCR (Google Cloud Vision / AWS Textract) (rejected; requires internet connection and leaks private receipts).

### 5. Geolocation: On-Demand Foreground FusedLocationProvider
- **Decision**: Single location request triggered only when opening the logging screen, cancelled immediately upon station selection.
- **Rationale**: Guarantees zero background battery drain and eliminates privacy concerns.
- **Alternatives Considered**: Background Geofencing (rejected; unnecessary permission hurdles and potential battery impact).

## Risks / Trade-offs

- **[Risk] Regional variation in fuel price APIs**: Different countries use different price reporting APIs (e.g. Tankerkönig in Germany, Open Fuel APIs in UK/US).
  - *Mitigation*: Abstract fuel price retrieval behind a clean `FuelPriceProvider` interface with a fallback mock/crowdsourced provider.
- **[Risk] OCR extraction errors on tilted/distorted digits**: Mechanical or reflective odometer and pump displays might yield misread digits.
  - *Mitigation*: Never auto-save scanned OCR data directly; present recognized numbers in pre-filled editable input fields with delta sanity validation against prior odometer reading.
- **[Risk] Mixed partial fills skewing consumption curves**: Driver topping up 10 L without filling to full tank.
  - *Mitigation*: Consumption calculation engine flags partial fills and postpones consumption math across the interval until the next full tank event.
