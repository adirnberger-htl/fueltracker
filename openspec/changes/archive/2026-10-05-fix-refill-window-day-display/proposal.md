# Proposal: Fix Refill Window Day Display

## Why

The cheapest refill window recommendation currently generates messages like `"Refuel on day 4 before reaching reserve..."`. This phrasing is ambiguous: users cannot tell whether it refers to day 4 of the week, the 4th of the month, or 4 days from now. Clarifying this recommendation with human-readable relative timing (e.g. "in 4 days" or "on Thursday, 09.10") eliminates confusion and improves actionability.

## What Changes

- Clarify target refill recommendation date phrasing in `PriceIntelligenceEngine` messages from `"Refuel on day X"` to clear relative/calendar descriptions (e.g. `"Refuel in X days (Thu, 09.10)"` or `"Refuel tomorrow (Tue, 06.10)"`).
- Update recommendation UI in `DashboardScreen` / `PriceRecommendationCard` if needed to cleanly show target date badge or formatted date text alongside the time window.
- Update unit tests in `EnginesTest.kt` verifying recommendation messages and date formatting.

## Capabilities

### New Capabilities
<!-- None -->

### Modified Capabilities
- `price-intelligence`: Clarify requirement `Recommended Fill-Up Time Window` to mandate unambiguous target date phrasing (relative days or localized weekday/date) in refill recommendations.

## Impact

- Domain model & engine: `PriceIntelligenceEngine.kt` message generator and `RefillRecommendation` fields.
- UI: `DashboardScreen.kt` recommendation card text formatting.
- Tests: `EnginesTest.kt` assertion updates.
