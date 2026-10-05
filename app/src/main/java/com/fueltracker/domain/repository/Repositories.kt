package com.fueltracker.domain.repository

import com.fueltracker.domain.model.FuelStation
import com.fueltracker.domain.model.FuelType
import com.fueltracker.domain.model.PriceSnapshot
import com.fueltracker.domain.model.RefuelLog
import com.fueltracker.domain.model.Vehicle
import kotlinx.coroutines.flow.Flow

interface VehicleRepository {
    fun getAllVehicles(): Flow<List<Vehicle>>
    suspend fun getVehicleById(id: String): Vehicle?
    suspend fun saveVehicle(vehicle: Vehicle)
    suspend fun deleteVehicle(vehicle: Vehicle)
    suspend fun updateOdometerIfHigher(vehicleId: String, newOdometer: Int)
}

interface RefuelRepository {
    fun getLogsForVehicle(vehicleId: String): Flow<List<RefuelLog>>
    suspend fun getLogsChronological(vehicleId: String): List<RefuelLog>
    suspend fun getLogById(id: String): RefuelLog?
    suspend fun getLatestLog(vehicleId: String): RefuelLog?
    suspend fun saveLog(log: RefuelLog)
    suspend fun deleteLog(log: RefuelLog)
}

interface PriceRepository {
    fun getAllStations(): Flow<List<FuelStation>>
    suspend fun getStationById(id: String): FuelStation?
    suspend fun saveStation(station: FuelStation)
    suspend fun recordPriceSnapshot(snapshot: PriceSnapshot)
    suspend fun getRecentSnapshots(limit: Int = 100): List<PriceSnapshot>
    suspend fun getSnapshotsSince(sinceEpochMs: Long): List<PriceSnapshot>
    suspend fun pruneSnapshotsOlderThan(cutoffEpochMs: Long): Int
    fun getProfilesForStation(stationId: String, fuelType: FuelType): Flow<List<com.fueltracker.domain.model.StationPriceProfile>>
    suspend fun getProfilesForStationSync(stationId: String, fuelType: FuelType): List<com.fueltracker.domain.model.StationPriceProfile>
    fun getAllProfilesForFuelType(fuelType: FuelType): Flow<List<com.fueltracker.domain.model.StationPriceProfile>>
    suspend fun getAllProfilesForFuelTypeSync(fuelType: FuelType): List<com.fueltracker.domain.model.StationPriceProfile>
    suspend fun recordPriceObservation(
        stationId: String,
        fuelType: FuelType,
        dayOfWeek: Int,
        hourOfDay: Int,
        price: Double,
        timestampMs: Long = System.currentTimeMillis()
    )
    suspend fun saveProfile(profile: com.fueltracker.domain.model.StationPriceProfile)
}

interface UserPreferencesRepository {
    val activeVehicleIdFlow: Flow<String?>
    suspend fun setActiveVehicleId(vehicleId: String)
    val customApiKeyFlow: Flow<String?>
    suspend fun setCustomApiKey(key: String?)
    val customApiUrlFlow: Flow<String?>
    suspend fun setCustomApiUrl(url: String?)
}

