# Tasks

## 1. Domain Logic & Date Formatting

- [x] 1.1 Implement target date formatting helper in `PriceIntelligenceEngine` (`Engines.kt`) formatting `targetDayOffset == 1L` as `"tomorrow (<Weekday, dd.MM>)"` and `> 1L` as `"in <N> days (<Weekday, dd.MM>)"`.
- [x] 1.2 Update recommendation message construction in `PriceIntelligenceEngine.kt` to replace ambiguous `"day $targetDayOffset"` with the formatted target date text.

## 2. Testing & Verification

- [x] 2.1 Update and add unit tests in `EnginesTest.kt` verifying the new recommendation message string format for tomorrow and multi-day offsets, and verify passing with `./gradlew testDebugUnitTest`.
