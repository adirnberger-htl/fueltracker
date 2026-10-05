# vehicle-management Specification

## Purpose
Enables creation and management of multi-vehicle fleet profiles across internal combustion engines, electric vehicles, and plug-in hybrids.

## Requirements

### Requirement: Vehicle Profile Creation
The system SHALL allow users to create and store vehicle profiles supporting ICE (gasoline/diesel), EV (battery electric), and PHEV (plug-in hybrid) powertrains.

#### Scenario: Creating an ICE vehicle profile
- **WHEN** user inputs vehicle name, selects powertrain "ICE", fuel type "Diesel", tank capacity 55 L, and baseline consumption 5.2 L/100km
- **THEN** system saves the vehicle profile and makes it selectable for logging

#### Scenario: Creating an EV vehicle profile
- **WHEN** user inputs vehicle name, selects powertrain "EV", battery usable capacity 60 kWh, and baseline consumption 16.5 kWh/100km
- **THEN** system saves the EV profile with electrical units configured

### Requirement: Active Vehicle Selection
The system SHALL maintain one currently active vehicle in application state that defaults all logging and dashboard views.

#### Scenario: Switching active vehicle
- **WHEN** user selects a different vehicle from the vehicle switcher dropdown
- **THEN** system immediately updates the active vehicle context, displaying that vehicle's odometer, fuel level, and logging history
