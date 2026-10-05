package com.fueltracker.data.remote

import com.fueltracker.domain.model.FuelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EControlApiClientTest {

    private val sampleJson = """
        [
          {
            "id": "1001",
            "name": "Eni Service Station Linz",
            "location": {
              "address": "Wiener Strasse 10",
              "postalCode": "4020",
              "city": "Linz",
              "latitude": 48.298,
              "longitude": 14.288
            },
            "prices": [
              {
                "fuelType": "DIE",
                "amount": 1.649
              }
            ],
            "open": true
          },
          {
            "id": "1002",
            "name": "Aral Autohof Wels",
            "location": {
              "address": "Salzburger Strasse 50",
              "postalCode": "4600",
              "city": "Wels",
              "latitude": 48.156,
              "longitude": 14.024
            },
            "prices": [
              {
                "fuelType": "DIE",
                "amount": 1.629
              }
            ],
            "open": true
          }
        ]
    """.trimIndent()

    @Test
    fun testParseStationsJson() {
        val client = EControlApiClient()
        val results = client.parseStationsJson(sampleJson, FuelType.DIESEL)

        assertEquals(2, results.size)

        val first = results[0]
        assertEquals("econtrol_1001", first.station.id)
        assertEquals("Eni Service Station Linz", first.station.name)
        assertEquals("Eni", first.station.brand)
        assertEquals("Wiener Strasse 10, Linz", first.station.address)
        assertEquals(48.298, first.station.latitude, 0.001)
        assertEquals(14.288, first.station.longitude, 0.001)
        assertEquals(1.649, first.price, 0.001)
        assertEquals(FuelType.DIESEL, first.fuelType)

        val second = results[1]
        assertEquals("econtrol_1002", second.station.id)
        assertEquals(1.629, second.price, 0.001)
    }

    @Test
    fun testFuelTypeMapping() {
        assertEquals("DIE", EControlApiClient.mapFuelTypeToApiCode(FuelType.DIESEL))
        assertEquals("SUP", EControlApiClient.mapFuelTypeToApiCode(FuelType.SUPER_95_E5))
        assertEquals("SUP", EControlApiClient.mapFuelTypeToApiCode(FuelType.SUPER_95_E10))
        assertEquals("SUP", EControlApiClient.mapFuelTypeToApiCode(FuelType.SUPER_PLUS_98))
    }

    @Test
    fun testFetchNearbyPricesWithMockFetcherAndCustomHeaders() {
        var requestedUrl = ""
        var capturedHeaders = mapOf<String, String>()

        val mockFetcher: (String, Map<String, String>) -> String = { url, headers ->
            requestedUrl = url
            capturedHeaders = headers
            sampleJson
        }

        val client = EControlApiClient(httpFetcher = mockFetcher)
        val results = client.fetchNearbyPrices(
            latitude = 48.306,
            longitude = 14.286,
            fuelType = FuelType.SUPER_95_E10,
            customBaseUrl = "https://custom-proxy.internal/sprit/stations",
            customApiKey = "secret-token-999"
        )

        assertEquals(2, results.size)
        assertTrue("URL should use custom base", requestedUrl.startsWith("https://custom-proxy.internal/sprit/stations"))
        assertTrue("URL should include latitude", requestedUrl.contains("latitude=48.306"))
        assertTrue("URL should include fuelType=SUP", requestedUrl.contains("fuelType=SUP"))

        assertEquals("Bearer secret-token-999", capturedHeaders["Authorization"])
        assertEquals("secret-token-999", capturedHeaders["X-API-KEY"])
    }
}
