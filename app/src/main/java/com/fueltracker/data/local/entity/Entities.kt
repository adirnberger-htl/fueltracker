package com.fueltracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.fueltracker.domain.model.FuelType
import com.fueltracker.domain.model.PowertrainType
import com.fueltracker.domain.model.Vehicle
import com.fueltracker.domain.model.RefuelLog
import com.fueltracker.domain.model.FuelStation
import com.fueltracker.domain.model.PriceSnapshot

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val powertrainType: PowertrainType,
    val primaryFuelType: FuelType,
    val capacityUnits: Double,
    val baselineConsumption: Double,
    val currentOdometerKm: Int,
    val createdAtEpochMs: Long
) {
    fun toDomain() = Vehicle(
        id = id,
        name = name,
        powertrainType = powertrainType,
        primaryFuelType = primaryFuelType,
        capacityUnits = capacityUnits,
        baselineConsumption = baselineConsumption,
        currentOdometerKm = currentOdometerKm,
        createdAtEpochMs = createdAtEpochMs
    )

    companion object {
        fun fromDomain(vehicle: Vehicle) = VehicleEntity(
            id = vehicle.id,
            name = vehicle.name,
            powertrainType = vehicle.powertrainType,
            primaryFuelType = vehicle.primaryFuelType,
            capacityUnits = vehicle.capacityUnits,
            baselineConsumption = vehicle.baselineConsumption,
            currentOdometerKm = vehicle.currentOdometerKm,
            createdAtEpochMs = vehicle.createdAtEpochMs
        )
    }
}

@Entity(
    tableName = "refuel_logs",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("vehicleId"),
        Index("timestampEpochMs")
    ]
)
data class RefuelLogEntity(
    @PrimaryKey
    val id: String,
    val vehicleId: String,
    val timestampEpochMs: Long,
    val odometerKm: Int,
    val unitsFilled: Double,
    val totalCost: Double,
    val unitPrice: Double,
    val isFullFill: Boolean,
    val stationId: String?,
    val stationName: String?,
    val notes: String,
    val receiptImagePath: String?
) {
    fun toDomain() = RefuelLog(
        id = id,
        vehicleId = vehicleId,
        timestampEpochMs = timestampEpochMs,
        odometerKm = odometerKm,
        unitsFilled = unitsFilled,
        totalCost = totalCost,
        unitPrice = unitPrice,
        isFullFill = isFullFill,
        stationId = stationId,
        stationName = stationName,
        notes = notes,
        receiptImagePath = receiptImagePath
    )

    companion object {
        fun fromDomain(log: RefuelLog) = RefuelLogEntity(
            id = log.id,
            vehicleId = log.vehicleId,
            timestampEpochMs = log.timestampEpochMs,
            odometerKm = log.odometerKm,
            unitsFilled = log.unitsFilled,
            totalCost = log.totalCost,
            unitPrice = log.unitPrice,
            isFullFill = log.isFullFill,
            stationId = log.stationId,
            stationName = log.stationName,
            notes = log.notes,
            receiptImagePath = log.receiptImagePath
        )
    }
}

@Entity(tableName = "fuel_stations")
data class FuelStationEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val brand: String,
    val latitude: Double,
    val longitude: Double,
    val address: String
) {
    fun toDomain() = FuelStation(
        id = id,
        name = name,
        brand = brand,
        latitude = latitude,
        longitude = longitude,
        address = address
    )

    companion object {
        fun fromDomain(station: FuelStation) = FuelStationEntity(
            id = station.id,
            name = station.name,
            brand = station.brand,
            latitude = station.latitude,
            longitude = station.longitude,
            address = station.address
        )
    }
}

@Entity(
    tableName = "price_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = FuelStationEntity::class,
            parentColumns = ["id"],
            childColumns = ["stationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("stationId"),
        Index("timestampEpochMs")
    ]
)
data class PriceSnapshotEntity(
    @PrimaryKey
    val id: String,
    val stationId: String,
    val timestampEpochMs: Long,
    val fuelType: FuelType,
    val pricePerUnit: Double
) {
    fun toDomain() = PriceSnapshot(
        id = id,
        stationId = stationId,
        timestampEpochMs = timestampEpochMs,
        fuelType = fuelType,
        pricePerUnit = pricePerUnit
    )

    companion object {
        fun fromDomain(snapshot: PriceSnapshot) = PriceSnapshotEntity(
            id = snapshot.id,
            stationId = snapshot.stationId,
            timestampEpochMs = snapshot.timestampEpochMs,
            fuelType = snapshot.fuelType,
            pricePerUnit = snapshot.pricePerUnit
        )
    }
}
