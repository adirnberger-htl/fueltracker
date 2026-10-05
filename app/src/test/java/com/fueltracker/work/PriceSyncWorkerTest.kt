package com.fueltracker.work

import com.fueltracker.data.local.dao.FuelStationDao
import com.fueltracker.data.local.dao.PriceSnapshotDao
import com.fueltracker.data.local.dao.StationPriceProfileDao
import com.fueltracker.data.remote.EControlApiClient
import com.fueltracker.data.repository.FakeFuelStationDao
import com.fueltracker.data.repository.FakePriceSnapshotDao
import com.fueltracker.data.repository.FakeStationPriceProfileDao
import com.fueltracker.data.repository.PriceRepositoryImpl
import com.fueltracker.domain.model.FuelType
import com.fueltracker.domain.model.PriceSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.UUID

class PriceSyncWorkerTest {

    @Test
    fun testWorkNameConstant() {
        assertEquals("PriceSyncWorker", PriceSyncWorker.WORK_NAME)
    }

    @Test
    fun testWorkerSyncPipelineWithMockClient() = runBlocking<Unit> {
        val stationDao = FakeFuelStationDao()
        val snapshotDao = FakePriceSnapshotDao()
        val profileDao = FakeStationPriceProfileDao()
        val priceRepo = PriceRepositoryImpl(stationDao, snapshotDao, profileDao)

        val mockJson = """
            [
              {
                "id": "2001",
                "name": "Turmöl Linz",
                "location": {
                  "address": "Industriestrasse 1",
                  "city": "Linz",
                  "latitude": 48.300,
                  "longitude": 14.300
                },
                "prices": [
                  {
                    "fuelType": "DIE",
                    "amount": 1.639
                  }
                ],
                "open": true
              }
            ]
        """.trimIndent()

        val client = EControlApiClient(httpFetcher = { _, _ -> mockJson })
        val fetched = client.fetchNearbyPrices(48.300, 14.300, FuelType.DIESEL)
        assertEquals(1, fetched.size)

        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = now
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)

        // Execute sync steps as done in worker:
        for (res in fetched) {
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

        // Verify station saved
        val station = priceRepo.getStationById("econtrol_2001")
        assertTrue(station != null)
        assertEquals("Turmöl Linz", station?.name)

        // Verify profile updated
        val profiles = priceRepo.getProfilesForStationSync("econtrol_2001", FuelType.DIESEL)
        assertEquals(1, profiles.size)
        assertEquals(1.639, profiles.first().runningAvgPrice, 0.001)
        assertEquals(dayOfWeek, profiles.first().dayOfWeek)
        assertEquals(hourOfDay, profiles.first().hourOfDay)
    }
}
