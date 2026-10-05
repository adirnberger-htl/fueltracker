package com.fueltracker

import com.fueltracker.data.repository.FakeRefuelLogDao
import com.fueltracker.data.repository.FakeVehicleDao
import com.fueltracker.data.repository.RefuelRepositoryImpl
import com.fueltracker.data.repository.VehicleRepositoryImpl
import com.fueltracker.domain.engine.ConsumptionEngine
import com.fueltracker.domain.engine.DepletionForecastEngine
import com.fueltracker.domain.engine.EcoRating
import com.fueltracker.domain.engine.FuelMath
import com.fueltracker.domain.engine.PriceIntelligenceEngine
import com.fueltracker.domain.model.FuelType
import com.fueltracker.domain.model.PowertrainType
import com.fueltracker.domain.model.PriceSnapshot
import com.fueltracker.domain.model.RefuelLog
import com.fueltracker.domain.model.Vehicle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class FuelTrackerE2ETest {

    @Test
    fun testCompleteUserJourney() = runBlocking {
        val vehicleDao = FakeVehicleDao()
        val refuelDao = FakeRefuelLogDao()
        val vehicleRepo = VehicleRepositoryImpl(vehicleDao)
        val refuelRepo = RefuelRepositoryImpl(refuelDao, vehicleDao)

        // 1. Create ICE vehicle
        val vehicleId = UUID.randomUUID().toString()
        val vehicle = Vehicle(
            id = vehicleId,
            name = "Golf 2.0 TDI",
            powertrainType = PowertrainType.ICE,
            primaryFuelType = FuelType.DIESEL,
            capacityUnits = 55.0,
            baselineConsumption = 5.2,
            currentOdometerKm = 100000
        )
        vehicleRepo.saveVehicle(vehicle)

        // 2. Perform 2-variable auto-calculation test
        val autoCalc = FuelMath.inferMissingVariable(volume = 50.0, totalCost = 85.0, unitPrice = null)
        assertNotNull(autoCalc)
        assertEquals(1.70, autoCalc!!.unitPrice, 0.001)

        // 3. User logs first full fill
        val t0 = 1000000000L
        val log1 = RefuelLog(
            id = "log-1",
            vehicleId = vehicleId,
            timestampEpochMs = t0,
            odometerKm = 100000,
            unitsFilled = 50.0,
            totalCost = 85.0,
            unitPrice = 1.70,
            isFullFill = true,
            stationName = "Shell Downtown"
        )
        refuelRepo.saveLog(log1)

        // 4. User logs intermediate partial fill 4 days later (+15 L at 100,300 km)
        val t1 = t0 + (4 * 24 * 3600 * 1000L)
        val log2 = RefuelLog(
            id = "log-2",
            vehicleId = vehicleId,
            timestampEpochMs = t1,
            odometerKm = 100300,
            unitsFilled = 15.0,
            totalCost = 25.50,
            unitPrice = 1.70,
            isFullFill = false,
            stationName = "Aral Express"
        )
        refuelRepo.saveLog(log2)

        // 5. User logs second full fill 6 days later (+35 L at 101,000 km)
        // Total units across 1000 km = 15 + 35 = 50 L -> exactly 5.0 L/100km!
        val t2 = t0 + (10 * 24 * 3600 * 1000L)
        val log3 = RefuelLog(
            id = "log-3",
            vehicleId = vehicleId,
            timestampEpochMs = t2,
            odometerKm = 101000,
            unitsFilled = 35.0,
            totalCost = 59.15,
            unitPrice = 1.69,
            isFullFill = true,
            stationName = "TotalEnergies"
        )
        refuelRepo.saveLog(log3)

        // 6. Verify vehicle odometer updated to latest log odometer
        val updatedVehicle = vehicleRepo.getVehicleById(vehicleId)
        assertNotNull(updatedVehicle)
        assertEquals(101000, updatedVehicle?.currentOdometerKm)

        // 7. Verify consumption & efficiency engine
        val allLogs = refuelRepo.getLogsChronological(vehicleId)
        assertEquals(3, allLogs.size)

        val efficiency = ConsumptionEngine.calculateEfficiency(updatedVehicle!!, allLogs)
        assertEquals(1, efficiency.intervals.size)
        val interval = efficiency.intervals.first()
        assertEquals(1000, interval.distanceKm)
        assertEquals(50.0, interval.totalUnitsFilled, 0.01)
        assertEquals(5.0, interval.consumptionRate, 0.01)
        // Baseline was 5.2, actual is 5.0 -> -3.8% (more efficient than baseline!)
        assertEquals(EcoRating.ECO, interval.ecoRating)

        // 8. Verify depletion forecast
        val forecast = DepletionForecastEngine.calculateForecast(
            vehicle = updatedVehicle,
            logs = allLogs,
            currentFuelPrice = 1.69,
            currentTimeMs = t2
        )
        assertEquals(55.0, forecast.remainingUnits, 0.1) // full tank just filled
        assertEquals(100.0, forecast.remainingPercent, 0.1)
        assertEquals(1100, forecast.remainingRangeKm) // 55 L / 5.0 L/100km = 1,100 km!
        assertTrue(forecast.dailyKmRate > 0)
        assertTrue(forecast.projectedMonthlyExpense > 0)

        // 9. Verify price recommendations
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 8)
        val morningTime = cal.timeInMillis
        cal.set(java.util.Calendar.HOUR_OF_DAY, 19)
        val eveningTime = cal.timeInMillis

        val snapshots = listOf(
            PriceSnapshot("s1", "st1", morningTime, FuelType.DIESEL, 1.82),
            PriceSnapshot("s2", "st1", eveningTime, FuelType.DIESEL, 1.68)
        )
        val recommendation = PriceIntelligenceEngine.recommendNextRefill(
            forecast = forecast,
            snapshots = snapshots,
            fuelType = FuelType.DIESEL,
            currentTimeMs = morningTime
        )

        assertEquals("19:00 - 21:00", recommendation.recommendedTimeWindow)
        assertTrue(recommendation.estimatedSavingsPerUnit >= 0.10)
    }
}
