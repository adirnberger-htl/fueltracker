package com.fueltracker.domain.model

enum class PowertrainType {
    ICE,
    EV,
    PHEV
}

enum class FuelType(val displayName: String, val unitLabel: String) {
    DIESEL("Diesel", "L"),
    SUPER_95_E5("Super 95 (E5)", "L"),
    SUPER_95_E10("Super 95 (E10)", "L"),
    SUPER_PLUS_98("Super Plus 98", "L"),
    ELECTRIC_AC("Electric AC", "kWh"),
    ELECTRIC_DC("Electric DC (Fast)", "kWh");

    val isElectric: Boolean
        get() = this == ELECTRIC_AC || this == ELECTRIC_DC
}

data class Vehicle(
    val id: String,
    val name: String,
    val powertrainType: PowertrainType,
    val primaryFuelType: FuelType,
    val capacityUnits: Double, // Liters or kWh
    val baselineConsumption: Double, // L/100km or kWh/100km
    val currentOdometerKm: Int,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

data class RefuelLog(
    val id: String,
    val vehicleId: String,
    val timestampEpochMs: Long,
    val odometerKm: Int,
    val unitsFilled: Double, // L or kWh
    val totalCost: Double,
    val unitPrice: Double,
    val isFullFill: Boolean,
    val stationId: String? = null,
    val stationName: String? = null,
    val notes: String = "",
    val receiptImagePath: String? = null
)

data class FuelStation(
    val id: String,
    val name: String,
    val brand: String,
    val latitude: Double,
    val longitude: Double,
    val address: String = ""
)

data class PriceSnapshot(
    val id: String,
    val stationId: String,
    val timestampEpochMs: Long,
    val fuelType: FuelType,
    val pricePerUnit: Double
)
