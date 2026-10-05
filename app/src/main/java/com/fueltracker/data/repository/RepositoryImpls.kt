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
import com.fueltracker.data.local.dao.StationPriceProfileDao
import com.fueltracker.data.local.dao.VehicleDao
import com.fueltracker.data.local.entity.FuelStationEntity
import com.fueltracker.data.local.entity.PriceSnapshotEntity
import com.fueltracker.data.local.entity.RefuelLogEntity
import com.fueltracker.data.local.entity.StationPriceProfileEntity
import com.fueltracker.data.local.entity.VehicleEntity
import com.fueltracker.domain.model.FuelStation
import com.fueltracker.domain.model.FuelType
import com.fueltracker.domain.model.PriceSnapshot
import com.fueltracker.domain.model.RefuelLog
import com.fueltracker.domain.model.StationPriceProfile
import com.fueltracker.domain.model.Vehicle
import com.fueltracker.domain.repository.PriceRepository
import com.fueltracker.domain.repository.RefuelRepository
import com.fueltracker.domain.repository.UserPreferencesRepository
import com.fueltracker.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
    private val priceSnapshotDao: PriceSnapshotDao,
    private val stationPriceProfileDao: StationPriceProfileDao? = null
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

    override suspend fun pruneSnapshotsOlderThan(cutoffEpochMs: Long): Int {
        return priceSnapshotDao.pruneSnapshotsOlderThan(cutoffEpochMs)
    }

    override fun getProfilesForStation(stationId: String, fuelType: FuelType): Flow<List<StationPriceProfile>> {
        return stationPriceProfileDao?.getProfilesForStation(stationId, fuelType)?.map { entities ->
            entities.map { it.toDomain() }
        } ?: flowOf(emptyList())
    }

    override suspend fun getProfilesForStationSync(stationId: String, fuelType: FuelType): List<StationPriceProfile> {
        return stationPriceProfileDao?.getProfilesForStationSync(stationId, fuelType)?.map { it.toDomain() } ?: emptyList()
    }

    override fun getAllProfilesForFuelType(fuelType: FuelType): Flow<List<StationPriceProfile>> {
        return stationPriceProfileDao?.getAllProfilesForFuelType(fuelType)?.map { entities ->
            entities.map { it.toDomain() }
        } ?: flowOf(emptyList())
    }

    override suspend fun getAllProfilesForFuelTypeSync(fuelType: FuelType): List<StationPriceProfile> {
        return stationPriceProfileDao?.getAllProfilesForFuelTypeSync(fuelType)?.map { it.toDomain() } ?: emptyList()
    }

    override suspend fun recordPriceObservation(
        stationId: String,
        fuelType: FuelType,
        dayOfWeek: Int,
        hourOfDay: Int,
        price: Double,
        timestampMs: Long
    ) {
        val existing = stationPriceProfileDao?.getProfile(stationId, fuelType, dayOfWeek, hourOfDay)
        val updated = if (existing != null) {
            val newCount = existing.sampleCount + 1
            val newAvg = existing.runningAvgPrice + ((price - existing.runningAvgPrice) / newCount)
            existing.copy(
                runningAvgPrice = com.fueltracker.domain.engine.FuelMath.roundToDecimals(newAvg, 3),
                sampleCount = newCount,
                lastUpdatedEpochMs = timestampMs
            )
        } else {
            StationPriceProfileEntity(
                stationId = stationId,
                fuelType = fuelType,
                dayOfWeek = dayOfWeek,
                hourOfDay = hourOfDay,
                runningAvgPrice = price,
                sampleCount = 1,
                lastUpdatedEpochMs = timestampMs
            )
        }
        stationPriceProfileDao?.insertProfile(updated)
    }

    override suspend fun saveProfile(profile: StationPriceProfile) {
        stationPriceProfileDao?.insertProfile(StationPriceProfileEntity.fromDomain(profile))
    }
}

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferencesRepositoryImpl(
    private val context: Context
) : UserPreferencesRepository {
    companion object {
        private val ACTIVE_VEHICLE_KEY = stringPreferencesKey("active_vehicle_id")
        private val CUSTOM_API_KEY = stringPreferencesKey("price_api_custom_key")
        private val CUSTOM_API_URL = stringPreferencesKey("price_api_custom_url")
    }

    override val activeVehicleIdFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[ACTIVE_VEHICLE_KEY]
    }

    override suspend fun setActiveVehicleId(vehicleId: String) {
        context.dataStore.edit { preferences ->
            preferences[ACTIVE_VEHICLE_KEY] = vehicleId
        }
    }

    override val customApiKeyFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[CUSTOM_API_KEY]
    }

    override suspend fun setCustomApiKey(key: String?) {
        context.dataStore.edit { preferences ->
            if (key != null) {
                preferences[CUSTOM_API_KEY] = key
            } else {
                preferences.remove(CUSTOM_API_KEY)
            }
        }
    }

    override val customApiUrlFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[CUSTOM_API_URL]
    }

    override suspend fun setCustomApiUrl(url: String?) {
        context.dataStore.edit { preferences ->
            if (url != null) {
                preferences[CUSTOM_API_URL] = url
            } else {
                preferences.remove(CUSTOM_API_URL)
            }
        }
    }
}

