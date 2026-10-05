# Design: Fix Refill Window Day Display

## Context

In `PriceIntelligenceEngine.kt`, `targetDayOffset` is calculated as the day before estimated empty date (or 1 day if within 2 days). The generated recommendation message currently uses:
`"Refuel on day $targetDayOffset before reaching reserve to save ~€${savings}/L."`
This confuses users because "day X" sounds like a calendar day of the month or a weekday index rather than a relative offset from today.

See `proposal.md` for motivation and `specs/price-intelligence/spec.md` for the behavioral requirement.

## Goals / Non-Goals

**Goals:**
- Format target refill day in `PriceIntelligenceEngine` using clear relative timing and localized calendar date (`EEE, dd.MM`).
- For 1 day offset: format as `"tomorrow (<Weekday, dd.MM>)"`.
- For >1 day offset: format as `"in <N> days (<Weekday, dd.MM>)"`.
- Update all recommendation message construction sites in `PriceIntelligenceEngine.kt`.
- Ensure tests verify the formatted message and relative day clarity.

**Non-Goals:**
- Modifying the underlying depletion calculation or Bayesian prior price weighting logic.
- Redesigning the entire dashboard recommendation UI card layout.

## Decisions

### Decision 1: Human-readable relative day formatting helper in `PriceIntelligenceEngine`
- **Choice**: Introduce a private helper function `formatTargetDay(targetDateMs: Long, dayOffset: Long): String` using `SimpleDateFormat("EEE, dd.MM", Locale.getDefault())` (or parameterized/locale-safe formatter).
- **Format**:
  - `dayOffset == 1L`: `"tomorrow ($dateFormatted)"`
  - `dayOffset > 1L`: `"in $dayOffset days ($dateFormatted)"`
- **Rationale**: Immediate clarity for user without needing complex UI changes, leveraging the already computed `targetDateMs`.
- **Alternatives considered**:
  - Just `"in $dayOffset days"`: misses calendar day context (e.g. knowing if it falls on a weekend).
  - Just calendar date: misses immediate cognitive understanding of how many days remain before fuel runs out.

### Decision 2: Message string composition
- **Choice**:
  `"Prices are lowest around $windowStr. Refuel $targetDayText before reaching reserve to save ~€${savings}/L."`
- **Rationale**: Smooth, grammatical English sentence that drops the ambiguous "on day X" phrasing.

## Risks / Trade-offs

- **Risk**: Locale differences in date/weekday formatting during unit tests.
  - **Mitigation**: Use `Locale.US` or standard formatted pattern assertions in unit tests where fixed outputs are tested, or verify pattern structure.
