# Proposal: Fuel Tracker Core

## Why

Drivers experience unnecessary friction logging fuel and EV charging at the pump due to cumbersome manual data entry and lack of offline resilience. Furthermore, current trip logging apps fail to synthesize multi-powertrain fleets, answer whether driving habits are truly economical, predict upcoming fuel expenses, or recommend the cheapest intraday time windows to refuel. Fuel Tracker solves this by providing a friction-free, local-first logging experience combined with predictive efficiency and price intelligence.

## What Changes

- Introduce multi-vehicle fleet configuration supporting both internal combustion engines (Diesel, Super 95 E5/E10, Super Plus) and electric vehicles (battery capacity, charging mode, kWh).
- Build high-speed refueling and EV charging logging with auto-inferencing (calculate 3rd variable from any 2: volume, price/unit, total cost) and full vs. partial fill support.
- Provide on-demand GPS gas station detection (no continuous background tracking) and optional ML Kit OCR capture for pump displays and dashboard odometers.
- Implement efficiency tracking engine calculating actual vs. manufacturer baseline consumption (L/100km, kWh/100km) with driving economy scoring.
- Implement predictive fuel depletion models projecting empty dates, next refill costs, and monthly operational fuel budgets.
- Add fuel price intelligence analyzing local station price cycles to recommend optimal, low-cost refill windows before reserve depletion.

## Capabilities

### New Capabilities
- `vehicle-management`: Multi-vehicle profile management supporting ICE and EV powertrains, fuel types, tank/battery capacities, and baseline ratings.
- `refuel-logging`: Ultra-fast refueling and charging entry with smart auto-inferencing, full/partial tank handling, on-demand station resolution, and camera OCR for pump displays and odometers.
- `efficiency-analytics`: Consumption calculation algorithms (L/100km, kWh/100km, MPG), rolling averages, and economy benchmarking against vehicle baseline.
- `depletion-forecasting`: Rolling daily mileage tracking, remaining range estimation, next fill-up cost projection, and monthly fuel expense forecasting.
- `price-intelligence`: Station price tracking, intraday price trend detection, and smart refill time recommendation engine.

### Modified Capabilities
*(None - initial project specification)*

## Impact

- **Platform**: Android application built with Kotlin, Jetpack Compose, Material 3, and Room database.
- **Hardware/APIs**: Uses Android Location Services (foreground one-shot), Google ML Kit Text Recognition (on-device OCR), and external Fuel Price API (Tankerkönig or equivalent).
- **Architecture**: Local-first offline persistence with Jetpack Room and WorkManager for periodic price updates and predictive notifications.
