package com.fueltracker.domain.engine

import com.fueltracker.domain.model.FuelType
import com.fueltracker.domain.model.PowertrainType
import com.fueltracker.domain.model.PriceSnapshot
import com.fueltracker.domain.model.RefuelLog
import com.fueltracker.domain.model.Vehicle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class EnginesTest {

    @Test
    fun testFuelMathAutoCalculation() {
        // Combination 1: Volume & Total -> Price/Unit
        val res1 = FuelMath.inferMissingVariable(volume = 40.0, totalCost = 68.0, unitPrice = null)
        assertNotNull(res1)
        assertEquals(40.0, res1!!.volume, 0.001)
        assertEquals(68.0, res1.totalCost, 0.001)
        assertEquals(1.70, res1.unitPrice, 0.001)

        // Combination 2: Volume & Price -> Total Cost
        val res2 = FuelMath.inferMissingVariable(volume = 50.0, totalCost = null, unitPrice = 1.65)
        assertNotNull(res2)
        assertEquals(82.50, res2!!.totalCost, 0.001)

        // Combination 3: Total Cost & Price -> Volume
        val res3 = FuelMath.inferMissingVariable(volume = null, totalCost = 80.0, unitPrice = 1.60)
        assertNotNull(res3)
        assertEquals(50.0, res3!!.volume, 0.001)

        // All 3 provided
        val res4 = FuelMath.inferMissingVariable(volume = 10.0, totalCost = 20.0, unitPrice = 2.0)
        assertNotNull(res4)

        // Edge cases: null or <= 0
        assertNull(FuelMath.inferMissingVariable(null, null, null))
        assertNull(FuelMath.inferMissingVariable(0.0, 50.0, null))
        assertNull(FuelMath.inferMissingVariable(-10.0, 50.0, null))
        assertNull(FuelMath.inferMissingVariable(10.0, null, null))
    }

    @Test
    fun testConsumptionEngineConsecutiveFullFills() {
        val vehicle = Vehicle(
            id = "v1",
            name = "Test ICE",
            powertrainType = PowertrainType.ICE,
            primaryFuelType = FuelType.DIESEL,
            capacityUnits = 60.0,
            baselineConsumption = 5.0,
            currentOdometerKm = 101000
        )

        val log1 = RefuelLog(
            id = "l1",
            vehicleId = "v1",
            timestampEpochMs = 1000000L,
            odometerKm = 100000,
            unitsFilled = 55.0,
            totalCost = 88.0,
            unitPrice = 1.60,
            isFullFill = true
        )
        val log2 = RefuelLog(
            id = "l2",
            vehicleId = "v1",
            timestampEpochMs = 2000000L,
            odometerKm = 101000,
            unitsFilled = 52.0, // 52 L for 1,000 km = 5.2 L/100km
            totalCost = 83.2,
            unitPrice = 1.60,
            isFullFill = true
        )

        val result = ConsumptionEngine.calculateEfficiency(vehicle, listOf(log1, log2))
        assertEquals(1, result.intervals.size)
        val interval = result.intervals.first()
        assertEquals(1000, interval.distanceKm)
        assertEquals(5.2, interval.consumptionRate, 0.01)
        assertEquals(4.0, interval.baselineDeviationPercent, 0.1) // (5.2 - 5.0)/5.0 = +4.0%
        assertEquals(EcoRating.ECO, interval.ecoRating)
    }

    @Test
    fun testConsumptionEngineChainedPartialFills() {
        val vehicle = Vehicle(
            id = "v1",
            name = "Test ICE",
            powertrainType = PowertrainType.ICE,
            primaryFuelType = FuelType.SUPER_95_E10,
            capacityUnits = 50.0,
            baselineConsumption = 6.0,
            currentOdometerKm = 51200
        )

        // Log 1: Full at 50,000 km
        val log1 = RefuelLog("l1", "v1", 10000L, 50000, 45.0, 75.0, 1.66, isFullFill = true)
        // Log 2: Partial at 50,400 km (+15 L)
        val log2 = RefuelLog("l2", "v1", 20000L, 50400, 15.0, 25.0, 1.66, isFullFill = false)
        // Log 3: Partial at 50,800 km (+20 L)
        val log3 = RefuelLog("l3", "v1", 30000L, 50800, 20.0, 33.0, 1.65, isFullFill = false)
        // Log 4: Full at 51,200 km (+37 L) -> Total units across 1,200 km = 15 + 20 + 37 = 72 L -> 6.0 L/100km
        val log4 = RefuelLog("l4", "v1", 40000L, 51200, 37.0, 61.0, 1.65, isFullFill = true)

        val result = ConsumptionEngine.calculateEfficiency(vehicle, listOf(log1, log2, log3, log4))
        assertEquals(1, result.intervals.size)
        val interval = result.intervals.first()
        assertEquals(1200, interval.distanceKm)
        assertEquals(72.0, interval.totalUnitsFilled, 0.01)
        assertEquals(6.0, interval.consumptionRate, 0.01)
        assertEquals(0.0, interval.baselineDeviationPercent, 0.1)
        assertEquals(EcoRating.ECO, interval.ecoRating)
    }

    @Test
    fun testDepletionForecastEngine() {
        val vehicle = Vehicle(
            id = "v1",
            name = "Test EV",
            powertrainType = PowertrainType.EV,
            primaryFuelType = FuelType.ELECTRIC_AC,
            capacityUnits = 60.0, // 60 kWh
            baselineConsumption = 15.0, // 15 kWh/100km
            currentOdometerKm = 10100
        )

        val now = 1000000000L
        val log1 = RefuelLog("l1", "v1", now - 864000000L, 9500, 50.0, 15.0, 0.30, isFullFill = true)
        val log2 = RefuelLog("l2", "v1", now - 86400000L, 10000, 55.0, 16.5, 0.30, isFullFill = true)

        val forecast = DepletionForecastEngine.calculateForecast(
            vehicle = vehicle,
            logs = listOf(log1, log2),
            currentFuelPrice = 0.35,
            currentTimeMs = now
        )

        // Driven 100 km since last log (10100 - 10000)
        // 100 km at 11 kWh/100km consumes 11 kWh from 60 kWh -> ~49 kWh remaining
        assertTrue(forecast.remainingUnits > 40.0)
        assertTrue(forecast.remainingRangeKm > 200)
        assertTrue(forecast.estimatedCostToFull > 0.0)
        assertTrue(forecast.projectedMonthlyExpense > 0.0)
    }

    @Test
    fun testPriceIntelligenceEngine() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 8)
        val morningTime = cal.timeInMillis

        cal.set(Calendar.HOUR_OF_DAY, 19)
        val eveningTime = cal.timeInMillis

        val snapshots = listOf(
            PriceSnapshot("p1", "s1", morningTime, FuelType.DIESEL, 1.80),
            PriceSnapshot("p2", "s1", morningTime + 1000, FuelType.DIESEL, 1.82),
            PriceSnapshot("p3", "s1", eveningTime, FuelType.DIESEL, 1.68),
            PriceSnapshot("p4", "s1", eveningTime + 1000, FuelType.DIESEL, 1.66)
        )

        val trend = PriceIntelligenceEngine.analyzePriceTrend(snapshots, FuelType.DIESEL)
        assertEquals(1.81, trend[8]!!, 0.01)
        assertEquals(1.67, trend[19]!!, 0.01)

        val forecast = DepletionForecast(
            remainingUnits = 20.0,
            remainingPercent = 33.0,
            remainingRangeKm = 300,
            dailyKmRate = 50.0,
            estimatedEmptyDateEpochMs = morningTime + (6 * 24 * 3600 * 1000L),
            estimatedCostToFull = 50.0,
            projectedMonthlyExpense = 150.0
        )

        val recommendation = PriceIntelligenceEngine.recommendNextRefill(
            forecast = forecast,
            snapshots = snapshots,
            fuelType = FuelType.DIESEL,
            currentTimeMs = morningTime
        )

        assertEquals("19:00 - 21:00", recommendation.recommendedTimeWindow)
        assertTrue(recommendation.estimatedSavingsPerUnit >= 0.10)
        assertTrue(recommendation.message.contains("Refuel in 5 days ("))
    }

    @Test
    fun testPriceIntelligenceEngine_formatTargetDay() {
        val testTime = 1760000000000L
        val tomorrow = PriceIntelligenceEngine.formatTargetDay(testTime, 1L, java.util.Locale.US)
        assertTrue(tomorrow.startsWith("tomorrow ("))
        assertTrue(tomorrow.endsWith(")"))

        val multiDay = PriceIntelligenceEngine.formatTargetDay(testTime, 4L, java.util.Locale.US)
        assertTrue(multiDay.startsWith("in 4 days ("))
        assertTrue(multiDay.endsWith(")"))
    }

    @Test
    fun testPriceIntelligenceEngine_tomorrowRefillMessage() {
        val now = 1760000000000L
        val forecast = DepletionForecast(
            remainingUnits = 5.0,
            remainingPercent = 10.0,
            remainingRangeKm = 70,
            dailyKmRate = 50.0,
            estimatedEmptyDateEpochMs = now + (1 * 24 * 3600 * 1000L),
            estimatedCostToFull = 60.0,
            projectedMonthlyExpense = 150.0
        )

        val recommendation = PriceIntelligenceEngine.recommendNextRefill(
            forecast = forecast,
            snapshots = emptyList(),
            fuelType = FuelType.DIESEL,
            currentTimeMs = now
        )

        assertTrue(recommendation.message.contains("Refuel tomorrow ("))
    }
}
