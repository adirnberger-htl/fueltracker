package com.fueltracker.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fueltracker.data.local.dao.FuelStationDao
import com.fueltracker.data.local.dao.PriceSnapshotDao
import com.fueltracker.data.local.dao.RefuelLogDao
import com.fueltracker.data.local.dao.VehicleDao
import com.fueltracker.data.local.entity.FuelStationEntity
import com.fueltracker.data.local.entity.PriceSnapshotEntity
import com.fueltracker.data.local.entity.RefuelLogEntity
import com.fueltracker.data.local.entity.VehicleEntity
import com.fueltracker.domain.model.FuelStation
import com.fueltracker.domain.model.PriceSnapshot
import com.fueltracker.domain.model.RefuelLog
import com.fueltracker.domain.model.Vehicle
import com.fueltracker.domain.repository.PriceRepository
import com.fueltracker.domain.repository.RefuelRepository
import com.fueltracker.domain.repository.UserPreferencesRepository
import com.fueltracker.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class VehicleRepositoryImpl(
    private val vehicleDao: VehicleDao
) : VehicleRepository {
    override fun getAllVehicles(): Flow<List<Vehicle>> {
        return vehicleDao.getAllVehicles().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getVehicleById(id: String): Vehicle? {
        return vehicleDao.getVehicleById(id)?.toDomain()
    }

    override suspend fun saveVehicle(vehicle: Vehicle) {
        vehicleDao.insertVehicle(VehicleEntity.fromDomain(vehicle))
    }

    override suspend fun deleteVehicle(vehicle: Vehicle) {
        vehicleDao.deleteVehicle(VehicleEntity.fromDomain(vehicle))
    }

    override suspend fun updateOdometerIfHigher(vehicleId: String, newOdometer: Int) {
        vehicleDao.updateOdometerIfHigher(vehicleId, newOdometer)
    }
}

class RefuelRepositoryImpl(
    private val refuelLogDao: RefuelLogDao,
    private val vehicleDao: VehicleDao
) : RefuelRepository {
    override fun getLogsForVehicle(vehicleId: String): Flow<List<RefuelLog>> {
        return refuelLogDao.getLogsForVehicle(vehicleId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getLogsChronological(vehicleId: String): List<RefuelLog> {
        return refuelLogDao.getLogsForVehicleChronological(vehicleId).map { it.toDomain() }
    }

    override suspend fun getLogById(id: String): RefuelLog? {
        return refuelLogDao.getLogById(id)?.toDomain()
    }

    override suspend fun getLatestLog(vehicleId: String): RefuelLog? {
        return refuelLogDao.getLatestLog(vehicleId)?.toDomain()
    }

    override suspend fun saveLog(log: RefuelLog) {
        refuelLogDao.insertLog(RefuelLogEntity.fromDomain(log))
        vehicleDao.updateOdometerIfHigher(log.vehicleId, log.odometerKm)
    }

    override suspend fun deleteLog(log: RefuelLog) {
        refuelLogDao.deleteLog(RefuelLogEntity.fromDomain(log))
    }
}

class PriceRepositoryImpl(
    private val stationDao: FuelStationDao,
    private val priceSnapshotDao: PriceSnapshotDao
) : PriceRepository {
    override fun getAllStations(): Flow<List<FuelStation>> {
        return stationDao.getAllStations().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getStationById(id: String): FuelStation? {
        return stationDao.getStationById(id)?.toDomain()
    }

    override suspend fun saveStation(station: FuelStation) {
        stationDao.insertStation(FuelStationEntity.fromDomain(station))
    }

    override suspend fun recordPriceSnapshot(snapshot: PriceSnapshot) {
        priceSnapshotDao.insertSnapshot(PriceSnapshotEntity.fromDomain(snapshot))
    }

    override suspend fun getRecentSnapshots(limit: Int): List<PriceSnapshot> {
        return priceSnapshotDao.getRecentSnapshots(limit).map { it.toDomain() }
    }

    override suspend fun getSnapshotsSince(sinceEpochMs: Long): List<PriceSnapshot> {
        return priceSnapshotDao.getSnapshotsSince(sinceEpochMs).map { it.toDomain() }
    }
}

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepositoryImpl(
    private val context: Context
) : UserPreferencesRepository {
    companion object {
        private val ACTIVE_VEHICLE_KEY = stringPreferencesKey("active_vehicle_id")
    }

    override val activeVehicleIdFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[ACTIVE_VEHICLE_KEY]
    }

    override suspend fun setActiveVehicleId(vehicleId: String) {
        context.dataStore.edit { preferences ->
            preferences[ACTIVE_VEHICLE_KEY] = vehicleId
        }
    }
}
