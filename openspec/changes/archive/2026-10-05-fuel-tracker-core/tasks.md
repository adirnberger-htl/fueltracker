# Tasks

## 1. Project Setup & Core Dependencies

- [x] 1.1 Configure Android project build scripts with Kotlin 2.x, Jetpack Compose, Material 3, and navigation dependencies; verify build succeeds.
- [x] 1.2 Add Room database, Coroutines, Google ML Kit Text Recognition, and Play Services Location dependencies; verify gradle sync passes.

## 2. Persistence & Data Layer

- [x] 2.1 Define Room entities (`VehicleEntity`, `RefuelLogEntity`, `FuelStationEntity`, `PriceSnapshotEntity`) and relations; verify entity schema compiles.
- [x] 2.2 Implement Room DAOs with CRUD queries, reactive `Flow` emissions, and transactional operations; write and pass DAO unit tests.
- [x] 2.3 Create repository implementations (`VehicleRepository`, `RefuelRepository`, `PriceRepository`) backed by Room and user DataStore preferences; write repository unit tests.


## 3. Domain Logic & Calculation Engines

- [x] 3.1 Implement smart two-variable auto-calculation logic (`FuelMath.inferMissingVariable`); write unit tests covering all 3 variable combinations and edge cases (zero/negative).
- [x] 3.2 Implement consumption calculation engine handling consecutive full fills, chained partial fills, and EV kWh metrics; write comprehensive unit tests.
- [x] 3.3 Implement depletion forecasting engine calculating rolling daily mileage, remaining range, cost to fill, and projected monthly budget; write unit tests.
- [x] 3.4 Implement price intelligence analyzer detecting historical intraday price dips and generating recommended refill time windows; write unit tests.


## 4. Vehicle Management UI

- [x] 4.1 Build vehicle profile creation and edit screens supporting ICE and EV powertrain options with validation; verify in Compose preview.
- [x] 4.2 Build top-level active vehicle switcher and vehicle status card showing current estimated tank level and odometer; verify UI interaction.

## 5. Fast Refuel Logging & OCR UI

- [x] 5.1 Implement "Quick-Add" refuel logging screen with reactive auto-calculating inputs and full/partial fill toggle; verify user flow.
- [x] 5.2 Integrate one-shot foreground FusedLocationProvider to resolve nearest gas station; verify fallback behavior when location is denied.
- [x] 5.3 Implement on-device camera OCR scanning using Google ML Kit for pump displays and odometers with review-before-save; verify recognition and pre-population.

## 6. Insights & Price Recommendations UI

- [x] 6.1 Build efficiency analytics dashboard displaying consumption trends, baseline deviation indicators, and eco ratings.
- [x] 6.2 Build financial & depletion forecast view displaying remaining range, next fill cost, and monthly fuel expense projection.
- [x] 6.3 Build price recommendation card displaying optimal time windows and local price history.

## 7. Verification & Integration

- [x] 7.1 Run full unit and instrumentation test suite across all modules and verify all tests pass.
- [x] 7.2 Perform end-to-end user journey validation: create ICE vehicle, log full and partial fills, test auto-calculation, and verify dashboard projections update reactively.

