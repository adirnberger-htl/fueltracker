package com.fueltracker.domain.engine

import com.fueltracker.domain.model.FuelType
import com.fueltracker.domain.model.PriceSnapshot
import com.fueltracker.domain.model.RefuelLog
import com.fueltracker.domain.model.Vehicle
import java.util.Calendar
import kotlin.math.max
import kotlin.math.roundToLong

object FuelMath {
    enum class VariableType {
        VOLUME,
        TOTAL_COST,
        UNIT_PRICE
    }

    data class CalculationResult(
        val volume: Double,
        val totalCost: Double,
        val unitPrice: Double
    )

    fun inferMissingVariable(
        volume: Double?,
        totalCost: Double?,
        unitPrice: Double?
    ): CalculationResult? {
        val hasVol = volume != null && volume > 0.0
        val hasCost = totalCost != null && totalCost > 0.0
        val hasPrice = unitPrice != null && unitPrice > 0.0

        return when {
            hasVol && hasCost && !hasPrice -> {
                val price = roundToDecimals(totalCost!! / volume!!, 3)
                CalculationResult(volume, totalCost, price)
            }
            hasVol && hasPrice && !hasCost -> {
                val cost = roundToDecimals(volume!! * unitPrice!!, 2)
                CalculationResult(volume, cost, unitPrice)
            }
            hasCost && hasPrice && !hasVol -> {
                val vol = roundToDecimals(totalCost!! / unitPrice!!, 2)
                CalculationResult(vol, totalCost, unitPrice)
            }
            hasVol && hasCost && hasPrice -> {
                CalculationResult(volume!!, totalCost!!, unitPrice!!)
            }
            else -> null
        }
    }

    fun roundToDecimals(value: Double, decimals: Int): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10.0 }
        return kotlin.math.round(value * multiplier) / multiplier
    }
}

enum class EcoRating {
    ECO,
    MODERATE,
    INEFFICIENT
}

data class ConsumptionInterval(
    val startOdometerKm: Int,
    val endOdometerKm: Int,
    val distanceKm: Int,
    val totalUnitsFilled: Double,
    val consumptionRate: Double, // L/100km or kWh/100km
    val baselineDeviationPercent: Double,
    val ecoRating: EcoRating,
    val endLogId: String
)

data class EfficiencySummary(
    val averageConsumption: Double,
    val baselineConsumption: Double,
    val overallDeviationPercent: Double,
    val intervals: List<ConsumptionInterval>
)

object ConsumptionEngine {
    fun calculateEfficiency(
        vehicle: Vehicle,
        chronologicalLogs: List<RefuelLog>
    ): EfficiencySummary {
        val sortedLogs = chronologicalLogs.sortedBy { it.odometerKm }
        val intervals = mutableListOf<ConsumptionInterval>()

        var previousFullFillIndex = -1

        for (i in sortedLogs.indices) {
            val log = sortedLogs[i]
            if (log.isFullFill) {
                if (previousFullFillIndex != -1) {
                    val prevLog = sortedLogs[previousFullFillIndex]
                    val distance = log.odometerKm - prevLog.odometerKm

                    if (distance > 0) {
                        // Sum units filled between previous full fill (exclusive) and current full fill (inclusive)
                        var units = 0.0
                        for (j in (previousFullFillIndex + 1)..i) {
                            units += sortedLogs[j].unitsFilled
                        }

                        val consumption = FuelMath.roundToDecimals((units * 100.0) / distance, 2)
                        val baseline = vehicle.baselineConsumption
                        val deviation = if (baseline > 0.0) {
                            FuelMath.roundToDecimals(((consumption - baseline) / baseline) * 100.0, 1)
                        } else {
                            0.0
                        }

                        val rating = when {
                            deviation <= 5.0 -> EcoRating.ECO
                            deviation <= 15.0 -> EcoRating.MODERATE
                            else -> EcoRating.INEFFICIENT
                        }

                        intervals.add(
                            ConsumptionInterval(
                                startOdometerKm = prevLog.odometerKm,
                                endOdometerKm = log.odometerKm,
                                distanceKm = distance,
                                totalUnitsFilled = FuelMath.roundToDecimals(units, 2),
                                consumptionRate = consumption,
                                baselineDeviationPercent = deviation,
                                ecoRating = rating,
                                endLogId = log.id
                            )
                        )
                    }
                }
                previousFullFillIndex = i
            }
        }

        val totalDistance = intervals.sumOf { it.distanceKm }
        val totalUnits = intervals.sumOf { it.totalUnitsFilled }
        val overallAvg = if (totalDistance > 0) {
            FuelMath.roundToDecimals((totalUnits * 100.0) / totalDistance, 2)
        } else {
            vehicle.baselineConsumption
        }

        val overallDeviation = if (vehicle.baselineConsumption > 0.0) {
            FuelMath.roundToDecimals(((overallAvg - vehicle.baselineConsumption) / vehicle.baselineConsumption) * 100.0, 1)
        } else {
            0.0
        }

        return EfficiencySummary(
            averageConsumption = overallAvg,
            baselineConsumption = vehicle.baselineConsumption,
            overallDeviationPercent = overallDeviation,
            intervals = intervals
        )
    }
}

data class DepletionForecast(
    val remainingUnits: Double,
    val remainingPercent: Double,
    val remainingRangeKm: Int,
    val dailyKmRate: Double,
    val estimatedEmptyDateEpochMs: Long,
    val estimatedCostToFull: Double,
    val projectedMonthlyExpense: Double
)

object DepletionForecastEngine {
    private const val DEFAULT_DAILY_KM = 35.0
    private const val MS_PER_DAY = 24L * 60L * 60L * 1000L

    fun calculateForecast(
        vehicle: Vehicle,
        logs: List<RefuelLog>,
        currentFuelPrice: Double,
        currentTimeMs: Long = System.currentTimeMillis()
    ): DepletionForecast {
        val sortedLogs = logs.sortedBy { it.odometerKm }
        val latestLog = sortedLogs.lastOrNull()

        // 1. Calculate daily km rate
        val dailyKm = calculateDailyKm(sortedLogs) ?: DEFAULT_DAILY_KM

        // 2. Determine average consumption
        val efficiency = ConsumptionEngine.calculateEfficiency(vehicle, sortedLogs)
        val consumptionRate = if (efficiency.averageConsumption > 0.0) {
            efficiency.averageConsumption
        } else {
            max(1.0, vehicle.baselineConsumption)
        }

        // 3. Estimate remaining fuel/charge in tank
        val currentOdo = vehicle.currentOdometerKm
        val remainingUnits = if (latestLog != null) {
            val distSinceLastLog = max(0, currentOdo - latestLog.odometerKm)
            val unitsConsumedSince = (distSinceLastLog * consumptionRate) / 100.0
            val startingUnits = if (latestLog.isFullFill) {
                vehicle.capacityUnits
            } else {
                // Approximate: capacity - missing or last known level
                max(latestLog.unitsFilled, vehicle.capacityUnits * 0.5)
            }
            FuelMath.roundToDecimals(max(0.0, startingUnits - unitsConsumedSince), 1)
        } else {
            // Default 50% if no logs
            FuelMath.roundToDecimals(vehicle.capacityUnits * 0.5, 1)
        }

        val remainingPercent = if (vehicle.capacityUnits > 0) {
            FuelMath.roundToDecimals((remainingUnits / vehicle.capacityUnits) * 100.0, 1)
        } else 0.0

        val remainingRangeKm = ((remainingUnits * 100.0) / consumptionRate).toInt()

        val daysUntilEmpty = if (dailyKm > 0) {
            remainingRangeKm / dailyKm
        } else 30.0

        val estimatedEmptyDate = currentTimeMs + (daysUntilEmpty * MS_PER_DAY).roundToLong()

        // 4. Fill-up cost
        val unitsNeeded = max(0.0, vehicle.capacityUnits - remainingUnits)
        val effectivePrice = if (currentFuelPrice > 0.0) currentFuelPrice else (latestLog?.unitPrice ?: 1.70)
        val costToFull = FuelMath.roundToDecimals(unitsNeeded * effectivePrice, 2)

        // 5. Monthly expense projection (30 days)
        val monthlyKm = dailyKm * 30.0
        val monthlyUnits = (monthlyKm * consumptionRate) / 100.0
        val monthlyCost = FuelMath.roundToDecimals(monthlyUnits * effectivePrice, 2)

        return DepletionForecast(
            remainingUnits = remainingUnits,
            remainingPercent = remainingPercent,
            remainingRangeKm = remainingRangeKm,
            dailyKmRate = FuelMath.roundToDecimals(dailyKm, 1),
            estimatedEmptyDateEpochMs = estimatedEmptyDate,
            estimatedCostToFull = costToFull,
            projectedMonthlyExpense = monthlyCost
        )
    }

    private fun calculateDailyKm(sortedLogs: List<RefuelLog>): Double? {
        if (sortedLogs.size < 2) return null
        val firstLog = sortedLogs.first()
        val lastLog = sortedLogs.last()
        val totalDistance = lastLog.odometerKm - firstLog.odometerKm
        val totalDays = (lastLog.timestampEpochMs - firstLog.timestampEpochMs).toDouble() / MS_PER_DAY
        return if (totalDays >= 1.0 && totalDistance > 0) {
            totalDistance / totalDays
        } else null
    }
}

data class RefillRecommendation(
    val recommendedTimeWindow: String,
    val suggestedDateEpochMs: Long,
    val expectedPricePerUnit: Double,
    val estimatedSavingsPerUnit: Double,
    val message: String
)

object PriceIntelligenceEngine {
    private const val MS_PER_DAY = 24L * 60L * 60L * 1000L

    fun analyzePriceTrend(
        snapshots: List<PriceSnapshot>,
        fuelType: FuelType
    ): Map<Int, Double> {
        val filtered = snapshots.filter { it.fuelType == fuelType }
        if (filtered.isEmpty()) return emptyMap()

        val calendar = Calendar.getInstance()
        val hourMap = mutableMapOf<Int, MutableList<Double>>()

        for (snapshot in filtered) {
            calendar.timeInMillis = snapshot.timestampEpochMs
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            hourMap.getOrPut(hour) { mutableListOf() }.add(snapshot.pricePerUnit)
        }

        return hourMap.mapValues { (_, prices) ->
            FuelMath.roundToDecimals(prices.average(), 3)
        }
    }

    /**
     * Blends empirical station profile observations with regional prior using Bayesian/EMA weighting.
     * w = min(1.0, sampleCount / 10.0)
     * price = w * localAvg + (1 - w) * priorPrice
     */
    fun blendPriceWithPrior(
        dayOfWeek: Int,
        hourOfDay: Int,
        basePrice: Double,
        stationProfile: com.fueltracker.domain.model.StationPriceProfile?,
        prior: com.fueltracker.domain.model.RegionalPricePrior
    ): Double {
        val priorPrice = FuelMath.roundToDecimals(basePrice * prior.getMultiplier(dayOfWeek, hourOfDay), 3)
        if (stationProfile == null || stationProfile.sampleCount <= 0) {
            return priorPrice
        }
        val weight = (stationProfile.sampleCount / 10.0).coerceAtMost(1.0)
        val blended = (weight * stationProfile.runningAvgPrice) + ((1.0 - weight) * priorPrice)
        return FuelMath.roundToDecimals(blended, 3)
    }

    fun recommendNextRefill(
        forecast: DepletionForecast,
        snapshots: List<PriceSnapshot> = emptyList(),
        stationProfiles: List<com.fueltracker.domain.model.StationPriceProfile> = emptyList(),
        fuelType: FuelType,
        prior: com.fueltracker.domain.model.RegionalPricePrior? = null,
        currentTimeMs: Long = System.currentTimeMillis()
    ): RefillRecommendation {
        val priorToUse = prior ?: PricePriorLoader.getDefaultPrior()
        val hourlyAverages = analyzePriceTrend(snapshots, fuelType)

        // Suggest tomorrow or day before empty
        val daysUntilEmpty = max(1L, (forecast.estimatedEmptyDateEpochMs - currentTimeMs) / MS_PER_DAY)
        val targetDayOffset = if (daysUntilEmpty > 2) daysUntilEmpty - 1 else 1L
        val targetDateMs = currentTimeMs + (targetDayOffset * MS_PER_DAY)

        if (stationProfiles.isEmpty() && snapshots.isNotEmpty()) {
            val cheapestHour = hourlyAverages.minByOrNull { it.value }?.key ?: 19
            val lowestPrice = hourlyAverages[cheapestHour] ?: 1.65
            val highestPrice = hourlyAverages.maxOfOrNull { it.value } ?: (lowestPrice + 0.12)
            val savings = FuelMath.roundToDecimals(max(0.02, highestPrice - lowestPrice), 3)
            val windowStr = String.format("%02d:00 - %02d:00", cheapestHour, (cheapestHour + 2) % 24)

            return RefillRecommendation(
                recommendedTimeWindow = windowStr,
                suggestedDateEpochMs = targetDateMs,
                expectedPricePerUnit = lowestPrice,
                estimatedSavingsPerUnit = savings,
                message = "Prices are lowest around $windowStr. Refuel on day $targetDayOffset before reaching reserve to save ~€${savings}/L."
            )
        }

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = targetDateMs
        val targetDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        val effectiveBasePrice = when {
            hourlyAverages.isNotEmpty() -> hourlyAverages.values.average()
            snapshots.isNotEmpty() -> snapshots.map { it.pricePerUnit }.average()
            else -> priorToUse.defaultBaselinePrice
        }

        val profileByHour = stationProfiles
            .filter { it.fuelType == fuelType && it.dayOfWeek == targetDayOfWeek }
            .associateBy { it.hourOfDay }

        val estimated24Hours = (0..23).associateWith { hour ->
            if (hourlyAverages.containsKey(hour)) {
                hourlyAverages[hour]!!
            } else {
                val profile = profileByHour[hour]
                blendPriceWithPrior(
                    dayOfWeek = targetDayOfWeek,
                    hourOfDay = hour,
                    basePrice = effectiveBasePrice,
                    stationProfile = profile,
                    prior = priorToUse
                )
            }
        }

        val cheapestHour = estimated24Hours.minByOrNull { it.value }?.key ?: 11
        val lowestPrice = estimated24Hours[cheapestHour] ?: 1.65
        val highestPrice = estimated24Hours.maxOfOrNull { it.value } ?: (lowestPrice + 0.12)
        val savings = FuelMath.roundToDecimals(max(0.02, highestPrice - lowestPrice), 3)

        val windowStr = String.format("%02d:00 - %02d:00", cheapestHour, (cheapestHour + 2) % 24)

        return RefillRecommendation(
            recommendedTimeWindow = windowStr,
            suggestedDateEpochMs = targetDateMs,
            expectedPricePerUnit = lowestPrice,
            estimatedSavingsPerUnit = savings,
            message = "Prices are lowest around $windowStr. Refuel on day $targetDayOffset before reaching reserve to save ~€${savings}/L."
        )
    }
}
