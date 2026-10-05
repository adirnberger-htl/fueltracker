package com.fueltracker.domain.engine

import android.content.Context
import com.fueltracker.domain.model.RegionalPricePrior
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

object PricePriorLoader {

    fun parseFromJson(jsonString: String): RegionalPricePrior {
        val obj = JSONObject(jsonString)
        val region = obj.optString("region", "AT_OOE")
        val name = obj.optString("name", "Upper Austria Baseline Prior")
        val version = obj.optInt("version", 1)
        val defaultBaselinePrice = obj.optDouble("defaultBaselinePrice", 1.65)

        val factorsObj = obj.getJSONObject("factorsByDay")
        val factorsMap = mutableMapOf<Int, List<Double>>()
        for (key in factorsObj.keys()) {
            val dayInt = key.toIntOrNull() ?: continue
            val arr = factorsObj.getJSONArray(key)
            val list = mutableListOf<Double>()
            for (i in 0 until arr.length()) {
                list.add(arr.getDouble(i))
            }
            factorsMap[dayInt] = list
        }

        return RegionalPricePrior(
            region = region,
            name = name,
            version = version,
            defaultBaselinePrice = defaultBaselinePrice,
            factorsByDay = factorsMap
        )
    }

    fun loadFromAssets(
        context: Context,
        assetPath: String = "priors/at_ooe_prior.json"
    ): RegionalPricePrior {
        return try {
            val inputStream = context.assets.open(assetPath)
            val jsonString = BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
            parseFromJson(jsonString)
        } catch (e: Exception) {
            getDefaultPrior()
        }
    }

    fun getDefaultPrior(): RegionalPricePrior {
        // Upper Austria standard profile: spike at 12:00, dip at 10-11:00 and 19-20:00
        val weekdayFactors = listOf(
            1.00, 1.00, 1.00, 1.00, 1.00, 1.00, 0.99, 0.98, 0.97, 0.96, 0.95, 0.94,
            1.05, 1.04, 1.03, 1.02, 1.01, 1.00, 0.98, 0.97, 0.97, 0.98, 0.99, 1.00
        )
        val weekendFactors = listOf(
            1.00, 1.00, 1.00, 1.00, 1.00, 1.00, 1.00, 0.99, 0.98, 0.97, 0.96, 0.95,
            1.04, 1.03, 1.02, 1.02, 1.01, 1.00, 0.99, 0.98, 0.98, 0.99, 1.00, 1.00
        )
        val map = (1..7).associateWith { day ->
            if (day in 2..6) weekdayFactors else weekendFactors
        }
        return RegionalPricePrior(
            region = "AT_OOE",
            name = "Upper Austria Default Prior",
            version = 1,
            defaultBaselinePrice = 1.65,
            factorsByDay = map
        )
    }
}
