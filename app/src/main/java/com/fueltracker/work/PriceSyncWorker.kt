package com.fueltracker.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fueltracker.data.local.AppDatabase
import com.fueltracker.data.remote.EControlApiClient
import com.fueltracker.data.repository.PriceRepositoryImpl
import com.fueltracker.data.repository.UserPreferencesRepositoryImpl
import com.fueltracker.data.repository.VehicleRepositoryImpl
import com.fueltracker.domain.model.FuelType
import com.fueltracker.domain.model.PriceSnapshot
import com.fueltracker.util.LocationHelper
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit

class PriceSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getInstance(applicationContext)
            val priceRepo = PriceRepositoryImpl(db.fuelStationDao(), db.priceSnapshotDao(), db.stationPriceProfileDao())
            val userPrefsRepo = UserPreferencesRepositoryImpl(applicationContext)
            val vehicleRepo = VehicleRepositoryImpl(db.vehicleDao())

            val activeVehicleId = userPrefsRepo.activeVehicleIdFlow.first()
            val vehicle = if (activeVehicleId != null) vehicleRepo.getVehicleById(activeVehicleId) else null
            val fuelType = vehicle?.primaryFuelType ?: FuelType.DIESEL

            // Use current location or default Linz centroid (48.3064, 14.2858)
            val location = LocationHelper.getCurrentLocation(applicationContext)
            val lat = location?.latitude ?: 48.3064
            val lon = location?.longitude ?: 14.2858

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
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = now
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)

            // Save top stations and update their hourly profile bins
            for (res in results.take(5)) {
                priceRepo.saveStation(res.station)

                // 1. Record snapshot for 3-day history
                priceRepo.recordPriceSnapshot(
                    PriceSnapshot(
                        id = UUID.randomUUID().toString(),
                        stationId = res.station.id,
                        timestampEpochMs = now,
                        fuelType = res.fuelType,
                        pricePerUnit = res.price
                    )
                )

                // 2. Update permanent 168-hour profile bin
                priceRepo.recordPriceObservation(
                    stationId = res.station.id,
                    fuelType = res.fuelType,
                    dayOfWeek = dayOfWeek,
                    hourOfDay = hourOfDay,
                    price = res.price,
                    timestampMs = now
                )
            }

            // 3. Auto-prune raw snapshots older than 3 days (72 hours)
            val threeDaysAgo = now - (3L * 24L * 3600L * 1000L)
            priceRepo.pruneSnapshotsOlderThan(threeDaysAgo)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "PriceSyncWorker"

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<PriceSyncWorker>(
                repeatInterval = 6,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        }
    }
}
