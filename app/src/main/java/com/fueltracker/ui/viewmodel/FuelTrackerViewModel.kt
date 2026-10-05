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
import com.fueltracker.domain.model.PriceSnapshot
import com.fueltracker.domain.model.RefuelLog
import com.fueltracker.domain.model.Vehicle
import com.fueltracker.util.LocationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class UiState(
    val vehicles: List<Vehicle> = emptyList(),
    val activeVehicle: Vehicle? = null,
    val logs: List<RefuelLog> = emptyList(),
    val efficiency: EfficiencySummary? = null,
    val forecast: DepletionForecast? = null,
    val recommendation: RefillRecommendation? = null,
    val nearestStation: FuelStation? = null,
    val isLoading: Boolean = false
)

class FuelTrackerViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val vehicleRepo = VehicleRepositoryImpl(db.vehicleDao())
    private val refuelRepo = RefuelRepositoryImpl(db.refuelLogDao(), db.vehicleDao())
    private val priceRepo = PriceRepositoryImpl(db.fuelStationDao(), db.priceSnapshotDao())
    private val userPrefsRepo = UserPreferencesRepositoryImpl(application)

    private val _nearestStation = MutableStateFlow<FuelStation?>(null)
    private val _activeVehicleId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<UiState> = combine(
        vehicleRepo.getAllVehicles(),
        _activeVehicleId,
        _nearestStation
    ) { vehicles, activeId, station ->
        val active = vehicles.find { it.id == activeId } ?: vehicles.firstOrNull()
        Triple(vehicles, active, station)
    }.combine(
        refuelRepo.getLogsForVehicle(_activeVehicleId.value ?: "")
    ) { (vehicles, active, station), logs ->
        if (active != null) {
            val efficiency = ConsumptionEngine.calculateEfficiency(active, logs)
            val currentPrice = logs.firstOrNull()?.unitPrice ?: 1.70
            val forecast = DepletionForecastEngine.calculateForecast(active, logs, currentPrice)
            val recommendation = PriceIntelligenceEngine.recommendNextRefill(
                forecast = forecast,
                snapshots = emptyList(), // In-memory/database snapshots
                fuelType = active.primaryFuelType
            )
            UiState(
                vehicles = vehicles,
                activeVehicle = active,
                logs = logs,
                efficiency = efficiency,
                forecast = forecast,
                recommendation = recommendation,
                nearestStation = station
            )
        } else {
            UiState(
                vehicles = vehicles,
                activeVehicle = null,
                logs = emptyList(),
                nearestStation = station
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
            // Record price snapshot for future price curves
            priceRepo.recordPriceSnapshot(
                PriceSnapshot(
                    id = UUID.randomUUID().toString(),
                    stationId = log.stationId ?: "station_local",
                    timestampEpochMs = log.timestampEpochMs,
                    fuelType = uiState.value.activeVehicle?.primaryFuelType ?: FuelType.DIESEL,
                    pricePerUnit = log.unitPrice
                )
            )
        }
    }

    fun deleteRefuelLog(log: RefuelLog) {
        viewModelScope.launch {
            refuelRepo.deleteLog(log)
        }
    }

    fun resolveNearestStation() {
        viewModelScope.launch {
            val location = LocationHelper.getCurrentLocation(getApplication())
            if (location != null) {
                _nearestStation.value = FuelStation(
                    id = "gps_station_1",
                    name = "Aral Station (Nearest)",
                    brand = "Aral",
                    latitude = location.latitude,
                    longitude = location.longitude,
                    address = "Nearby GPS Location"
                )
            }
        }
    }
}
