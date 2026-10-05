package com.fueltracker.data.remote

import com.fueltracker.domain.model.FuelStation
import com.fueltracker.domain.model.FuelType
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

data class EControlPriceResult(
    val station: FuelStation,
    val fuelType: FuelType,
    val price: Double,
    val timestampEpochMs: Long = System.currentTimeMillis()
)

class EControlApiClient(
    private val httpFetcher: (url: String, headers: Map<String, String>) -> String = ::defaultHttpFetcher
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://api.e-control.at/sprit/1.0/search/gas-stations/by-address"

        fun defaultHttpFetcher(urlStr: String, headers: Map<String, String>): String {
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw RuntimeException("E-Control API returned HTTP $responseCode")
            }

            return connection.inputStream.bufferedReader().use { it.readText() }
        }

        fun mapFuelTypeToApiCode(fuelType: FuelType): String {
            return when (fuelType) {
                FuelType.DIESEL -> "DIE"
                FuelType.SUPER_95_E5, FuelType.SUPER_95_E10, FuelType.SUPER_PLUS_98 -> "SUP"
                FuelType.ELECTRIC_AC, FuelType.ELECTRIC_DC -> "DIE"
            }
        }
    }

    fun parseStationsJson(jsonString: String, fuelType: FuelType): List<EControlPriceResult> {
        val results = mutableListOf<EControlPriceResult>()
        val array = JSONArray(jsonString)
        val now = System.currentTimeMillis()

        for (i in 0 until array.length()) {
            val stationObj = array.getJSONObject(i)
            val id = stationObj.optString("id", UUID.randomUUID().toString())
            val name = stationObj.optString("name", "Unknown Station")
            val locationObj = stationObj.optJSONObject("location")
            val address = locationObj?.optString("address", "") ?: ""
            val city = locationObj?.optString("city", "") ?: ""
            val fullAddress = if (city.isNotBlank()) "$address, $city" else address
            val lat = locationObj?.optDouble("latitude", 0.0) ?: 0.0
            val lon = locationObj?.optDouble("longitude", 0.0) ?: 0.0

            val station = FuelStation(
                id = "econtrol_$id",
                name = name,
                brand = name.split(" ").firstOrNull() ?: "Generic",
                latitude = lat,
                longitude = lon,
                address = fullAddress
            )

            val pricesArray = stationObj.optJSONArray("prices")
            if (pricesArray != null && pricesArray.length() > 0) {
                val firstPrice = pricesArray.getJSONObject(0).optDouble("amount", 0.0)
                if (firstPrice > 0.0) {
                    results.add(
                        EControlPriceResult(
                            station = station,
                            fuelType = fuelType,
                            price = firstPrice,
                            timestampEpochMs = now
                        )
                    )
                }
            }
        }
        return results
    }

    fun fetchNearbyPrices(
        latitude: Double,
        longitude: Double,
        fuelType: FuelType,
        customBaseUrl: String? = null,
        customApiKey: String? = null
    ): List<EControlPriceResult> {
        val baseUrl = customBaseUrl?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE_URL
        val apiFuelCode = mapFuelTypeToApiCode(fuelType)
        val url = "$baseUrl?latitude=$latitude&longitude=$longitude&fuelType=$apiFuelCode&includeClosed=false"

        val headers = mutableMapOf<String, String>()
        if (!customApiKey.isNullOrBlank()) {
            headers["Authorization"] = "Bearer $customApiKey"
            headers["X-API-KEY"] = customApiKey
        }

        val jsonResponse = httpFetcher(url, headers)
        return parseStationsJson(jsonResponse, fuelType)
    }
}
