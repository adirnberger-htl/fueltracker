package com.fueltracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.fueltracker.data.local.entity.FuelStationEntity
import com.fueltracker.data.local.entity.PriceSnapshotEntity
import com.fueltracker.data.local.entity.RefuelLogEntity
import com.fueltracker.data.local.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles ORDER BY createdAtEpochMs DESC")
    fun getAllVehicles(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE id = :id LIMIT 1")
    suspend fun getVehicleById(id: String): VehicleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: VehicleEntity): Long

    @Update
    suspend fun updateVehicle(vehicle: VehicleEntity): Int

    @Query("UPDATE vehicles SET currentOdometerKm = :newOdometer WHERE id = :vehicleId AND currentOdometerKm < :newOdometer")
    suspend fun updateOdometerIfHigher(vehicleId: String, newOdometer: Int): Int

    @Delete
    suspend fun deleteVehicle(vehicle: VehicleEntity): Int
}

@Dao
interface RefuelLogDao {
    @Query("SELECT * FROM refuel_logs WHERE vehicleId = :vehicleId ORDER BY odometerKm DESC, timestampEpochMs DESC")
    fun getLogsForVehicle(vehicleId: String): Flow<List<RefuelLogEntity>>

    @Query("SELECT * FROM refuel_logs WHERE vehicleId = :vehicleId ORDER BY odometerKm ASC, timestampEpochMs ASC")
    suspend fun getLogsForVehicleChronological(vehicleId: String): List<RefuelLogEntity>

    @Query("SELECT * FROM refuel_logs WHERE id = :id LIMIT 1")
    suspend fun getLogById(id: String): RefuelLogEntity?

    @Query("SELECT * FROM refuel_logs WHERE vehicleId = :vehicleId ORDER BY odometerKm DESC, timestampEpochMs DESC LIMIT 1")
    suspend fun getLatestLog(vehicleId: String): RefuelLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: RefuelLogEntity): Long

    @Delete
    suspend fun deleteLog(log: RefuelLogEntity): Int
}

@Dao
interface FuelStationDao {
    @Query("SELECT * FROM fuel_stations ORDER BY name ASC")
    fun getAllStations(): Flow<List<FuelStationEntity>>

    @Query("SELECT * FROM fuel_stations WHERE id = :id LIMIT 1")
    suspend fun getStationById(id: String): FuelStationEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStation(station: FuelStationEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStations(stations: List<FuelStationEntity>): List<Long>
}

@Dao
interface PriceSnapshotDao {
    @Query("SELECT * FROM price_snapshots WHERE stationId = :stationId ORDER BY timestampEpochMs DESC")
    fun getSnapshotsForStation(stationId: String): Flow<List<PriceSnapshotEntity>>

    @Query("SELECT * FROM price_snapshots ORDER BY timestampEpochMs DESC LIMIT :limit")
    suspend fun getRecentSnapshots(limit: Int): List<PriceSnapshotEntity>

    @Query("SELECT * FROM price_snapshots WHERE timestampEpochMs >= :sinceEpochMs ORDER BY timestampEpochMs ASC")
    suspend fun getSnapshotsSince(sinceEpochMs: Long): List<PriceSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: PriceSnapshotEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshots(snapshots: List<PriceSnapshotEntity>): List<Long>

    @Query("DELETE FROM price_snapshots WHERE timestampEpochMs < :cutoffEpochMs")
    suspend fun pruneSnapshotsOlderThan(cutoffEpochMs: Long): Int
}

@Dao
interface StationPriceProfileDao {
    @Query("SELECT * FROM station_price_profiles WHERE stationId = :stationId AND fuelType = :fuelType")
    fun getProfilesForStation(stationId: String, fuelType: com.fueltracker.domain.model.FuelType): Flow<List<com.fueltracker.data.local.entity.StationPriceProfileEntity>>

    @Query("SELECT * FROM station_price_profiles WHERE stationId = :stationId AND fuelType = :fuelType")
    suspend fun getProfilesForStationSync(stationId: String, fuelType: com.fueltracker.domain.model.FuelType): List<com.fueltracker.data.local.entity.StationPriceProfileEntity>

    @Query("SELECT * FROM station_price_profiles WHERE fuelType = :fuelType")
    fun getAllProfilesForFuelType(fuelType: com.fueltracker.domain.model.FuelType): Flow<List<com.fueltracker.data.local.entity.StationPriceProfileEntity>>

    @Query("SELECT * FROM station_price_profiles WHERE fuelType = :fuelType")
    suspend fun getAllProfilesForFuelTypeSync(fuelType: com.fueltracker.domain.model.FuelType): List<com.fueltracker.data.local.entity.StationPriceProfileEntity>

    @Query("SELECT * FROM station_price_profiles WHERE stationId = :stationId AND fuelType = :fuelType AND dayOfWeek = :dayOfWeek AND hourOfDay = :hourOfDay LIMIT 1")
    suspend fun getProfile(stationId: String, fuelType: com.fueltracker.domain.model.FuelType, dayOfWeek: Int, hourOfDay: Int): com.fueltracker.data.local.entity.StationPriceProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: com.fueltracker.data.local.entity.StationPriceProfileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(profiles: List<com.fueltracker.data.local.entity.StationPriceProfileEntity>): List<Long>
}



