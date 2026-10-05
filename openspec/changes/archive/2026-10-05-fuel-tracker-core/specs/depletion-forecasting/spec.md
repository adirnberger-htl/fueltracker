# Spec Delta

## Purpose

Projects future fuel tank depletion timelines, remaining operational range, upcoming fill-up expenses, and monthly fuel budget requirements.

## ADDED Requirements

### Requirement: Tank Depletion and Empty Date Forecasting
The system SHALL compute remaining range and projected empty date using rolling daily mileage and current estimated fuel level.

#### Scenario: Forecasting remaining range and empty date
- **WHEN** active vehicle has 25 L estimated remaining fuel, average consumption of 6.0 L/100km, and rolling average mileage of 40 km/day
- **THEN** system estimates remaining range as approximately 416 km and projects tank depletion within 10 days

### Requirement: Next Fill-Up Cost Projection
The system SHALL project the estimated monetary cost required to restore the fuel tank or battery to 100% capacity.

#### Scenario: Estimating cost to full
- **WHEN** user views vehicle overview with 30 L missing from a 50 L tank and local fuel price is €1.75/L
- **THEN** system displays projected refill cost of €52.50

### Requirement: Monthly Fuel Expense Forecasting
The system SHALL project monthly financial cost required to operate the vehicle based on projected distance, consumption rate, and fuel prices.

#### Scenario: Generating monthly budget projection
- **WHEN** user navigates to financial forecast tab
- **THEN** system projects total monthly distance, applies average fuel price and historical consumption rate, and displays estimated monthly fuel spend
