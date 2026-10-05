package com.fueltracker.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fueltracker.data.local.AppDatabase
import com.fueltracker.data.repository.PriceRepositoryImpl
import com.fueltracker.data.repository.RefuelRepositoryImpl
import com.fueltracker.data.repository.UserPreferencesRepositoryImpl
import com.fueltracker.data.repository.VehicleRepositoryImpl
import com.fueltracker.domain.engine.ConsumptionEngine
import com.fueltracker.domain.engine.DepletionForecast
import com.fueltracker.domain.engine.DepletionForecastEngine
import com.fueltracker.domain.engine.EfficiencySummary
import com.fueltracker.domain.engine.PriceIntelligenceEngine
import com.fueltracker.domain.engine.RefillRecommendation
import com.fueltracker.domain.model.FuelStation
import com.fueltracker.domain.model.FuelType
import com.fueltracker.domain.model.PowertrainType
import com.fueltracker.data.remote.EControlApiClient
import com.fueltracker.domain.engine.PricePriorLoader
import com.fueltracker.domain.model.PriceSnapshot
import com.fueltracker.domain.model.RefuelLog
import com.fueltracker.domain.model.Vehicle
import com.fueltracker.util.LocationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

data class UiState(
    val vehicles: List<Vehicle> = emptyList(),
    val activeVehicle: Vehicle? = null,
    val logs: List<RefuelLog> = emptyList(),
    val efficiency: EfficiencySummary? = null,
    val forecast: DepletionForecast? = null,
    val recommendation: RefillRecommendation? = null,
    val nearestStation: FuelStation? = null,
    val customApiKey: String? = null,
    val customApiUrl: String? = null,
    val isSyncing: Boolean = false,
    val isLoading: Boolean = false
)

class FuelTrackerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val vehicleRepo = VehicleRepositoryImpl(db.vehicleDao())
    private val refuelRepo = RefuelRepositoryImpl(db.refuelLogDao(), db.vehicleDao())
    private val priceRepo = PriceRepositoryImpl(db.fuelStationDao(), db.priceSnapshotDao(), db.stationPriceProfileDao())
    private val userPrefsRepo = UserPreferencesRepositoryImpl(application)

    private val _nearestStation = MutableStateFlow<FuelStation?>(null)
    private val _activeVehicleId = MutableStateFlow<String?>(null)

    private data class VehicleContext(
        val vehicles: List<Vehicle>,
        val active: Vehicle?,
        val station: FuelStation?,
        val customKey: String?,
        val customUrl: String?
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState> = combine(
        vehicleRepo.getAllVehicles(),
        _activeVehicleId,
        _nearestStation,
        userPrefsRepo.customApiKeyFlow,
        userPrefsRepo.customApiUrlFlow
    ) { vehicles, activeId, station, customKey, customUrl ->
        val active = vehicles.find { it.id == activeId } ?: vehicles.firstOrNull()
        VehicleContext(vehicles, active, station, customKey, customUrl)
    }.flatMapLatest { ctx ->
        val active = ctx.active
        if (active != null) {
            combine(
                refuelRepo.getLogsForVehicle(active.id),
                priceRepo.getAllProfilesForFuelType(active.primaryFuelType)
            ) { logs, profiles ->
                val efficiency = ConsumptionEngine.calculateEfficiency(active, logs)
                val currentPrice = logs.firstOrNull()?.unitPrice ?: 1.70
                val forecast = DepletionForecastEngine.calculateForecast(active, logs, currentPrice)
                val recommendation = PriceIntelligenceEngine.recommendNextRefill(
                    forecast = forecast,
                    snapshots = emptyList(),
                    stationProfiles = profiles,
                    fuelType = active.primaryFuelType,
                    prior = PricePriorLoader.getDefaultPrior()
                )
                UiState(
                    vehicles = ctx.vehicles,
                    activeVehicle = active,
                    logs = logs,
                    efficiency = efficiency,
                    forecast = forecast,
                    recommendation = recommendation,
                    nearestStation = ctx.station,
                    customApiKey = ctx.customKey,
                    customApiUrl = ctx.customUrl
                )
            }
        } else {
            flowOf(
                UiState(
                    vehicles = ctx.vehicles,
                    activeVehicle = null,
                    logs = emptyList(),
                    nearestStation = ctx.station,
                    customApiKey = ctx.customKey,
                    customApiUrl = ctx.customUrl
                )
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        UiState(isLoading = true)
    )

    init {
        viewModelScope.launch {
            userPrefsRepo.activeVehicleIdFlow.collect { id ->
                _activeVehicleId.value = id
            }
        }
        seedDefaultDataIfEmpty()
    }

    private fun seedDefaultDataIfEmpty() {
        viewModelScope.launch {
            // Ensure default station always exists for referential integrity
            val defaultStation = FuelStation(
                id = "station_local",
                name = "Local Station",
                brand = "Generic",
                latitude = 0.0,
                longitude = 0.0,
                address = "Local Station"
            )
            priceRepo.saveStation(defaultStation)

            val vehicles = db.vehicleDao().getVehicleById("default-golf")
            if (vehicles == null) {
                val defaultVehicle = Vehicle(
                    id = "default-golf",
                    name = "VW Golf TDI",
                    powertrainType = PowertrainType.ICE,
                    primaryFuelType = FuelType.DIESEL,
                    capacityUnits = 50.0,
                    baselineConsumption = 4.8,
                    currentOdometerKm = 85200
                )
                vehicleRepo.saveVehicle(defaultVehicle)
                userPrefsRepo.setActiveVehicleId(defaultVehicle.id)

                // Add sample historical logs
                val now = System.currentTimeMillis()
                val dayMs = 24L * 3600 * 1000
                refuelRepo.saveLog(
                    RefuelLog(
                        id = UUID.randomUUID().toString(),
                        vehicleId = defaultVehicle.id,
                        timestampEpochMs = now - (14 * dayMs),
                        odometerKm = 84400,
                        unitsFilled = 45.0,
                        totalCost = 74.25,
                        unitPrice = 1.65,
                        isFullFill = true,
                        stationName = "Aral Autohof"
                    )
                )
                refuelRepo.saveLog(
                    RefuelLog(
                        id = UUID.randomUUID().toString(),
                        vehicleId = defaultVehicle.id,
                        timestampEpochMs = now - (2 * dayMs),
                        odometerKm = 85200,
                        unitsFilled = 40.0,
                        totalCost = 67.20,
                        unitPrice = 1.68,
                        isFullFill = true,
                        stationName = "Shell Express"
                    )
                )
            }
        }
    }

    fun selectActiveVehicle(vehicleId: String) {
        viewModelScope.launch {
            _activeVehicleId.value = vehicleId
            userPrefsRepo.setActiveVehicleId(vehicleId)
        }
    }

    fun saveVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            vehicleRepo.saveVehicle(vehicle)
            selectActiveVehicle(vehicle.id)
        }
    }

    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            vehicleRepo.deleteVehicle(vehicle)
        }
    }

    fun saveRefuelLog(log: RefuelLog) {
        viewModelScope.launch {
            refuelRepo.saveLog(log)

            val targetStationId = log.stationId
                ?: if (!log.stationName.isNullOrBlank() && log.stationName == _nearestStation.value?.name) {
                    _nearestStation.value?.id ?: "station_local"
                } else {
                    "station_local"
                }

            // Ensure station exists in DB before inserting price snapshot to satisfy foreign key constraint
            if (priceRepo.getStationById(targetStationId) == null) {
                priceRepo.saveStation(
                    FuelStation(
                        id = targetStationId,
                        name = log.stationName?.ifBlank { "Local Station" } ?: "Local Station",
                        brand = "Generic",
                        latitude = 0.0,
                        longitude = 0.0,
                        address = log.stationName?.ifBlank { "Local Station" } ?: "Local Station"
                    )
                )
            }

            // Record price snapshot for future price curves
            priceRepo.recordPriceSnapshot(
                PriceSnapshot(
                    id = UUID.randomUUID().toString(),
                    stationId = targetStationId,
                    timestampEpochMs = log.timestampEpochMs,
                    fuelType = uiState.value.activeVehicle?.primaryFuelType ?: FuelType.DIESEL,
                    pricePerUnit = log.unitPrice
                )
            )

            // Update permanent 168-hour station profile
            val cal = Calendar.getInstance()
            cal.timeInMillis = log.timestampEpochMs
            priceRepo.recordPriceObservation(
                stationId = targetStationId,
                fuelType = uiState.value.activeVehicle?.primaryFuelType ?: FuelType.DIESEL,
                dayOfWeek = cal.get(Calendar.DAY_OF_WEEK),
                hourOfDay = cal.get(Calendar.HOUR_OF_DAY),
                price = log.unitPrice,
                timestampMs = log.timestampEpochMs
            )
        }
    }

    fun deleteRefuelLog(log: RefuelLog) {
        viewModelScope.launch {
            refuelRepo.deleteLog(log)
        }
    }

    fun updateCustomApiKey(key: String?) {
        viewModelScope.launch {
            userPrefsRepo.setCustomApiKey(key?.trim()?.takeIf { it.isNotEmpty() })
        }
    }

    fun updateCustomApiUrl(url: String?) {
        viewModelScope.launch {
            userPrefsRepo.setCustomApiUrl(url?.trim()?.takeIf { it.isNotEmpty() })
        }
    }

    fun syncPricesNow(
        defaultLat: Double = 48.3064,
        defaultLon: Double = 14.2858
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val active = uiState.value.activeVehicle
                val fuelType = active?.primaryFuelType ?: FuelType.DIESEL
                val location = LocationHelper.getCurrentLocation(getApplication())
                val lat = location?.latitude ?: defaultLat
                val lon = location?.longitude ?: defaultLon

                val customUrl = userPrefsRepo.customApiUrlFlow.first()
                val customKey = userPrefsRepo.customApiKeyFlow.first()

                val client = EControlApiClient()
                val results = client.fetchNearbyPrices(
                    latitude = lat,
                    longitude = lon,
                    fuelType = fuelType,
                    customBaseUrl = customUrl,
                    customApiKey = customKey
                )

                val now = System.currentTimeMillis()
                val cal = Calendar.getInstance()
                cal.timeInMillis = now
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val hourOfDay = cal.get(Calendar.HOUR_OF_DAY)

                for (res in results.take(5)) {
                    priceRepo.saveStation(res.station)
                    priceRepo.recordPriceSnapshot(
                        PriceSnapshot(
                            id = UUID.randomUUID().toString(),
                            stationId = res.station.id,
                            timestampEpochMs = now,
                            fuelType = res.fuelType,
                            pricePerUnit = res.price
                        )
                    )
                    priceRepo.recordPriceObservation(
                        stationId = res.station.id,
                        fuelType = res.fuelType,
                        dayOfWeek = dayOfWeek,
                        hourOfDay = hourOfDay,
                        price = res.price,
                        timestampMs = now
                    )
                }

                val threeDaysAgo = now - (3L * 24L * 3600L * 1000L)
                priceRepo.pruneSnapshotsOlderThan(threeDaysAgo)
            } catch (_: Exception) {}
        }
    }

    fun resolveNearestStation() {
        viewModelScope.launch {
            val location = LocationHelper.getCurrentLocation(getApplication())
            if (location != null) {
                val station = FuelStation(
                    id = "gps_station_1",
                    name = "Aral Station (Nearest)",
                    brand = "Aral",
                    latitude = location.latitude,
                    longitude = location.longitude,
                    address = "Nearby GPS Location"
                )
                priceRepo.saveStation(station)
                _nearestStation.value = station
            }
        }
    }
}
