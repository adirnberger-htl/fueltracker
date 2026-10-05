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
import org.junit.Assert.assertTrue
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


class FakeFuelStationDao : FuelStationDao {
    private val stations = mutableMapOf<String, FuelStationEntity>()

    override fun getAllStations(): Flow<List<FuelStationEntity>> = flowOf(stations.values.toList())

    override suspend fun getStationById(id: String): FuelStationEntity? = stations[id]

    override suspend fun insertStation(station: FuelStationEntity): Long {
        if (!stations.containsKey(station.id)) {
            stations[station.id] = station
        }
        return 1L
    }

    override suspend fun insertStations(stations: List<FuelStationEntity>): List<Long> {
        stations.forEach { insertStation(it) }
        return stations.map { 1L }
    }
}

class FakePriceSnapshotDao : PriceSnapshotDao {
    val snapshots = mutableListOf<PriceSnapshotEntity>()

    override fun getSnapshotsForStation(stationId: String): Flow<List<PriceSnapshotEntity>> =
        flowOf(snapshots.filter { it.stationId == stationId })

    override suspend fun getRecentSnapshots(limit: Int): List<PriceSnapshotEntity> =
        snapshots.takeLast(limit)

    override suspend fun getSnapshotsSince(sinceEpochMs: Long): List<PriceSnapshotEntity> =
        snapshots.filter { it.timestampEpochMs >= sinceEpochMs }

    override suspend fun insertSnapshot(snapshot: PriceSnapshotEntity): Long {
        snapshots.add(snapshot)
        return 1L
    }

    override suspend fun insertSnapshots(snapshots: List<PriceSnapshotEntity>): List<Long> {
        this.snapshots.addAll(snapshots)
        return snapshots.map { 1L }
    }

    override suspend fun pruneSnapshotsOlderThan(cutoffEpochMs: Long): Int {
        val initialSize = snapshots.size
        snapshots.removeAll { it.timestampEpochMs < cutoffEpochMs }
        return initialSize - snapshots.size
    }
}

class FakeStationPriceProfileDao : com.fueltracker.data.local.dao.StationPriceProfileDao {
    private val profiles = mutableMapOf<String, com.fueltracker.data.local.entity.StationPriceProfileEntity>()

    private fun key(stationId: String, fuelType: FuelType, dayOfWeek: Int, hourOfDay: Int) =
        "${stationId}_${fuelType.name}_${dayOfWeek}_${hourOfDay}"

    override fun getProfilesForStation(
        stationId: String,
        fuelType: FuelType
    ): Flow<List<com.fueltracker.data.local.entity.StationPriceProfileEntity>> =
        flowOf(profiles.values.filter { it.stationId == stationId && it.fuelType == fuelType })

    override suspend fun getProfilesForStationSync(
        stationId: String,
        fuelType: FuelType
    ): List<com.fueltracker.data.local.entity.StationPriceProfileEntity> =
        profiles.values.filter { it.stationId == stationId && it.fuelType == fuelType }

    override fun getAllProfilesForFuelType(
        fuelType: FuelType
    ): Flow<List<com.fueltracker.data.local.entity.StationPriceProfileEntity>> =
        flowOf(profiles.values.filter { it.fuelType == fuelType })

    override suspend fun getAllProfilesForFuelTypeSync(
        fuelType: FuelType
    ): List<com.fueltracker.data.local.entity.StationPriceProfileEntity> =
        profiles.values.filter { it.fuelType == fuelType }

    override suspend fun getProfile(
        stationId: String,
        fuelType: FuelType,
        dayOfWeek: Int,
        hourOfDay: Int
    ): com.fueltracker.data.local.entity.StationPriceProfileEntity? =
        profiles[key(stationId, fuelType, dayOfWeek, hourOfDay)]

    override suspend fun insertProfile(
        profile: com.fueltracker.data.local.entity.StationPriceProfileEntity
    ): Long {
        profiles[key(profile.stationId, profile.fuelType, profile.dayOfWeek, profile.hourOfDay)] = profile
        return 1L
    }

    override suspend fun insertProfiles(
        profiles: List<com.fueltracker.data.local.entity.StationPriceProfileEntity>
    ): List<Long> {
        profiles.forEach { insertProfile(it) }
        return profiles.map { 1L }
    }
}

class FakeUserPreferencesRepository : com.fueltracker.domain.repository.UserPreferencesRepository {
    private val _activeVehicleId = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private val _customApiKey = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private val _customApiUrl = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    override val activeVehicleIdFlow: Flow<String?> = _activeVehicleId
    override val customApiKeyFlow: Flow<String?> = _customApiKey
    override val customApiUrlFlow: Flow<String?> = _customApiUrl

    override suspend fun setActiveVehicleId(vehicleId: String) {
        _activeVehicleId.value = vehicleId
    }

    override suspend fun setCustomApiKey(key: String?) {
        _customApiKey.value = key
    }

    override suspend fun setCustomApiUrl(url: String?) {
        _customApiUrl.value = url
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

    @Test
    fun testPriceSnapshotSavingWithStation() = runBlocking {
        val stationDao = FakeFuelStationDao()
        val snapshotDao = FakePriceSnapshotDao()
        val profileDao = FakeStationPriceProfileDao()
        val priceRepo = PriceRepositoryImpl(stationDao, snapshotDao, profileDao)

        // Ensure station exists before snapshot
        val defaultStation = com.fueltracker.domain.model.FuelStation(
            id = "station_local",
            name = "Local Station",
            brand = "Generic",
            latitude = 0.0,
            longitude = 0.0,
            address = "Local Station"
        )
        priceRepo.saveStation(defaultStation)

        val retrievedStation = priceRepo.getStationById("station_local")
        assertNotNull(retrievedStation)
        assertEquals("Local Station", retrievedStation?.name)

        // Record snapshot
        val snapshot = com.fueltracker.domain.model.PriceSnapshot(
            id = "snap-1",
            stationId = "station_local",
            timestampEpochMs = System.currentTimeMillis(),
            fuelType = FuelType.DIESEL,
            pricePerUnit = 1.67
        )
        priceRepo.recordPriceSnapshot(snapshot)

        val recent = priceRepo.getRecentSnapshots(10)
        assertEquals(1, recent.size)
        assertEquals("station_local", recent.first().stationId)
        assertEquals(1.67, recent.first().pricePerUnit, 0.001)
    }

    @Test
    fun testStationPriceProfileObservationAndAggregation() = runBlocking {
        val stationDao = FakeFuelStationDao()
        val snapshotDao = FakePriceSnapshotDao()
        val profileDao = FakeStationPriceProfileDao()
        val priceRepo = PriceRepositoryImpl(stationDao, snapshotDao, profileDao)

        val now = System.currentTimeMillis()
        // Record 1st observation: Monday (2), 11:00 AM, 1.60
        priceRepo.recordPriceObservation(
            stationId = "station_1",
            fuelType = FuelType.DIESEL,
            dayOfWeek = 2,
            hourOfDay = 11,
            price = 1.60,
            timestampMs = now
        )

        var profiles = priceRepo.getProfilesForStationSync("station_1", FuelType.DIESEL)
        assertEquals(1, profiles.size)
        assertEquals(1.60, profiles.first().runningAvgPrice, 0.001)
        assertEquals(1, profiles.first().sampleCount)

        // Record 2nd observation: Monday (2), 11:00 AM, 1.70 -> Avg = 1.65, count = 2
        priceRepo.recordPriceObservation(
            stationId = "station_1",
            fuelType = FuelType.DIESEL,
            dayOfWeek = 2,
            hourOfDay = 11,
            price = 1.70,
            timestampMs = now + 1000
        )

        profiles = priceRepo.getProfilesForStationSync("station_1", FuelType.DIESEL)
        assertEquals(1, profiles.size)
        assertEquals(1.65, profiles.first().runningAvgPrice, 0.001)
        assertEquals(2, profiles.first().sampleCount)
    }

    @Test
    fun testPruneSnapshotsOlderThanThreeDays() = runBlocking<Unit> {
        val stationDao = FakeFuelStationDao()
        val snapshotDao = FakePriceSnapshotDao()
        val profileDao = FakeStationPriceProfileDao()
        val priceRepo = PriceRepositoryImpl(stationDao, snapshotDao, profileDao)

        val now = System.currentTimeMillis()
        val dayMs = 24L * 3600 * 1000L

        // Old snapshots: 4 days old and 5 days old
        snapshotDao.insertSnapshot(PriceSnapshotEntity("s-old-1", "st1", now - (5 * dayMs), FuelType.DIESEL, 1.65))
        snapshotDao.insertSnapshot(PriceSnapshotEntity("s-old-2", "st1", now - (4 * dayMs), FuelType.DIESEL, 1.66))

        // Recent snapshots: 1 day old and current
        snapshotDao.insertSnapshot(PriceSnapshotEntity("s-new-1", "st1", now - (1 * dayMs), FuelType.DIESEL, 1.68))
        snapshotDao.insertSnapshot(PriceSnapshotEntity("s-new-2", "st1", now, FuelType.DIESEL, 1.70))

        assertEquals(4, snapshotDao.snapshots.size)

        // Prune older than 3 days
        val cutoff = now - (3 * dayMs)
        val prunedCount = priceRepo.pruneSnapshotsOlderThan(cutoff)

        assertEquals(2, prunedCount)
        assertEquals(2, snapshotDao.snapshots.size)
        assertTrue(snapshotDao.snapshots.all { it.timestampEpochMs >= cutoff })
    }

    @Test
    fun testUserPreferencesCustomApiKeyAndUrl() = runBlocking<Unit> {
        val userPrefsRepo = FakeUserPreferencesRepository()

        assertEquals(null, userPrefsRepo.customApiKeyFlow.first())
        assertEquals(null, userPrefsRepo.customApiUrlFlow.first())

        userPrefsRepo.setCustomApiKey("test-secret-key-123")
        userPrefsRepo.setCustomApiUrl("https://my-custom-proxy.internal/sprit")

        assertEquals("test-secret-key-123", userPrefsRepo.customApiKeyFlow.first())
        assertEquals("https://my-custom-proxy.internal/sprit", userPrefsRepo.customApiUrlFlow.first())

        userPrefsRepo.setCustomApiKey(null)
        assertEquals(null, userPrefsRepo.customApiKeyFlow.first())
    }
}

