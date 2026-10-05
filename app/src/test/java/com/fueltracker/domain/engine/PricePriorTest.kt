package com.fueltracker.domain.engine

import com.fueltracker.domain.model.FuelType
import com.fueltracker.domain.model.StationPriceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Calendar

class PricePriorTest {

    @Test
    fun testParseAtOoePriorJsonAsset() {
        val assetFile = File("src/main/assets/priors/at_ooe_prior.json")
        assertTrue("Asset file should exist", assetFile.exists())

        val jsonString = assetFile.readText()
        val prior = PricePriorLoader.parseFromJson(jsonString)

        assertEquals("AT_OOE", prior.region)
        assertEquals(1, prior.version)
        assertEquals(1.65, prior.defaultBaselinePrice, 0.001)
        assertEquals(7, prior.factorsByDay.size)

        for (day in 1..7) {
            val factors = prior.factorsByDay[day]
            assertNotNull("Day $day must exist in prior", factors)
            assertEquals(24, factors!!.size)

            // Austrian 12:00 spike rule: hour 12 multiplier must be higher than hour 11
            val preNoon = prior.getMultiplier(day, 11)
            val noonSpike = prior.getMultiplier(day, 12)
            assertTrue("Hour 12 spike ($noonSpike) should exceed hour 11 dip ($preNoon)", noonSpike > preNoon)
        }
    }

    @Test
    fun testBayesianBlendingColdStartPriorOnly() {
        val prior = PricePriorLoader.getDefaultPrior()
        val basePrice = 1.70

        // With no local profile, prior determines the price (e.g. Wednesday day 4)
        val preNoonPrice = PriceIntelligenceEngine.blendPriceWithPrior(
            dayOfWeek = 4,
            hourOfDay = 11,
            basePrice = basePrice,
            stationProfile = null,
            prior = prior
        )
        // Hour 11 multiplier is 0.94 -> 1.70 * 0.94 = 1.598
        assertEquals(1.598, preNoonPrice, 0.005)

        val noonPrice = PriceIntelligenceEngine.blendPriceWithPrior(
            dayOfWeek = 4,
            hourOfDay = 12,
            basePrice = basePrice,
            stationProfile = null,
            prior = prior
        )
        // Hour 12 multiplier is 1.05 -> 1.70 * 1.05 = 1.785
        assertEquals(1.785, noonPrice, 0.005)
        assertTrue(noonPrice > preNoonPrice)
    }

    @Test
    fun testBayesianBlendingShiftsWeightToLocalObservations() {
        val prior = PricePriorLoader.getDefaultPrior()
        val basePrice = 1.70
        val dayOfWeek = 4
        val hourOfDay = 11
        // Prior at 11:00 is 1.598

        // Local profile with 2 samples at 1.75
        val profileSmall = StationPriceProfile(
            stationId = "s1",
            fuelType = FuelType.DIESEL,
            dayOfWeek = dayOfWeek,
            hourOfDay = hourOfDay,
            runningAvgPrice = 1.75,
            sampleCount = 2
        )
        // w = 2 / 10 = 0.2 -> 0.2 * 1.75 + 0.8 * 1.598 = 0.35 + 1.2784 = 1.628
        val blendedSmall = PriceIntelligenceEngine.blendPriceWithPrior(
            dayOfWeek = dayOfWeek,
            hourOfDay = hourOfDay,
            basePrice = basePrice,
            stationProfile = profileSmall,
            prior = prior
        )
        assertEquals(1.628, blendedSmall, 0.01)

        // Local profile with 10+ samples at 1.75 -> full weight (w = 1.0)
        val profileFull = StationPriceProfile(
            stationId = "s1",
            fuelType = FuelType.DIESEL,
            dayOfWeek = dayOfWeek,
            hourOfDay = hourOfDay,
            runningAvgPrice = 1.75,
            sampleCount = 12
        )
        val blendedFull = PriceIntelligenceEngine.blendPriceWithPrior(
            dayOfWeek = dayOfWeek,
            hourOfDay = hourOfDay,
            basePrice = basePrice,
            stationProfile = profileFull,
            prior = prior
        )
        assertEquals(1.75, blendedFull, 0.001)
    }

    @Test
    fun testRecommendRefillUsesPriorWhenNoSnapshots() {
        val forecast = DepletionForecast(
            remainingUnits = 10.0,
            remainingPercent = 15.0,
            remainingRangeKm = 100,
            dailyKmRate = 40.0,
            estimatedEmptyDateEpochMs = System.currentTimeMillis() + (3 * 24 * 3600 * 1000L),
            estimatedCostToFull = 70.0,
            projectedMonthlyExpense = 120.0
        )

        val recommendation = PriceIntelligenceEngine.recommendNextRefill(
            forecast = forecast,
            snapshots = emptyList(),
            stationProfiles = emptyList(),
            fuelType = FuelType.DIESEL
        )

        // Default prior has lowest prices around 11:00 before noon hike
        assertNotNull(recommendation)
        assertTrue(recommendation.estimatedSavingsPerUnit >= 0.05)
        assertEquals("11:00 - 13:00", recommendation.recommendedTimeWindow)
    }
}
