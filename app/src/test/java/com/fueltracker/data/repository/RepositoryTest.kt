package com.fueltracker.data.repository

import com.fueltracker.data.local.dao.FuelStationDao
import com.fueltracker.data.local.dao.PriceSnapshotDao
import com.fueltracker.data.local.dao.RefuelLogDao
import com.fueltracker.data.local.dao.VehicleDao
import com.fueltracker.data.local.entity.FuelStationEntity
import com.fueltracker.data.local.entity.PriceSnapshotEntity
import com.fueltracker.data.local.entity.RefuelLogEntity
import com.fueltracker.data.local.entity.VehicleEntity
import com.fueltracker.domain.model.FuelType
import com.fueltracker.domain.model.PowertrainType
import com.fueltracker.domain.model.RefuelLog
import com.fueltracker.domain.model.Vehicle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FakeVehicleDao : VehicleDao {
    private val vehicles = mutableMapOf<String, VehicleEntity>()

    override fun getAllVehicles(): Flow<List<VehicleEntity>> = flowOf(vehicles.values.toList())

    override suspend fun getVehicleById(id: String): VehicleEntity? = vehicles[id]

    override suspend fun insertVehicle(vehicle: VehicleEntity): Long {
        vehicles[vehicle.id] = vehicle
        return 1L
    }

    override suspend fun updateVehicle(vehicle: VehicleEntity): Int {
        vehicles[vehicle.id] = vehicle
        return 1
    }

    override suspend fun updateOdometerIfHigher(vehicleId: String, newOdometer: Int): Int {
        val existing = vehicles[vehicleId]
        return if (existing != null && newOdometer > existing.currentOdometerKm) {
            vehicles[vehicleId] = existing.copy(currentOdometerKm = newOdometer)
            1
        } else {
            0
        }
    }

    override suspend fun deleteVehicle(vehicle: VehicleEntity): Int {
        vehicles.remove(vehicle.id)
        return 1
    }
}

class FakeRefuelLogDao : RefuelLogDao {
    private val logs = mutableListOf<RefuelLogEntity>()

    override fun getLogsForVehicle(vehicleId: String): Flow<List<RefuelLogEntity>> =
        flowOf(logs.filter { it.vehicleId == vehicleId })

    override suspend fun getLogsForVehicleChronological(vehicleId: String): List<RefuelLogEntity> =
        logs.filter { it.vehicleId == vehicleId }.sortedBy { it.odometerKm }

    override suspend fun getLogById(id: String): RefuelLogEntity? = logs.find { it.id == id }

    override suspend fun getLatestLog(vehicleId: String): RefuelLogEntity? =
        logs.filter { it.vehicleId == vehicleId }.maxByOrNull { it.odometerKm }

    override suspend fun insertLog(log: RefuelLogEntity): Long {
        logs.removeAll { it.id == log.id }
        logs.add(log)
        return 1L
    }

    override suspend fun deleteLog(log: RefuelLogEntity): Int {
        logs.removeAll { it.id == log.id }
        return 1
    }
}


class RepositoryTest {
    @Test
    fun testVehicleAndRefuelRepositoryIntegration() = runBlocking {
        val vehicleDao = FakeVehicleDao()
        val refuelDao = FakeRefuelLogDao()
        val vehicleRepo = VehicleRepositoryImpl(vehicleDao)
        val refuelRepo = RefuelRepositoryImpl(refuelDao, vehicleDao)

        val vehicle = Vehicle(
            id = "v1",
            name = "Golf 7",
            powertrainType = PowertrainType.ICE,
            primaryFuelType = FuelType.DIESEL,
            capacityUnits = 50.0,
            baselineConsumption = 5.0,
            currentOdometerKm = 100000
        )
        vehicleRepo.saveVehicle(vehicle)

        val fetched = vehicleRepo.getVehicleById("v1")
        assertNotNull(fetched)
        assertEquals("Golf 7", fetched?.name)

        val log = RefuelLog(
            id = "log1",
            vehicleId = "v1",
            timestampEpochMs = System.currentTimeMillis(),
            odometerKm = 100800,
            unitsFilled = 45.0,
            totalCost = 72.0,
            unitPrice = 1.60,
            isFullFill = true
        )
        refuelRepo.saveLog(log)

        // Verify odometer updated on vehicle
        val updatedVehicle = vehicleRepo.getVehicleById("v1")
        assertEquals(100800, updatedVehicle?.currentOdometerKm)

        val logs = refuelRepo.getLogsChronological("v1")
        assertEquals(1, logs.size)
        assertEquals(45.0, logs.first().unitsFilled, 0.001)
    }
}
