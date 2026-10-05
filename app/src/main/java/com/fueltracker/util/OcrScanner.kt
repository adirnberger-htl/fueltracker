package com.fueltracker.util

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ParsedPumpData(
    val volume: Double? = null,
    val totalCost: Double? = null,
    val unitPrice: Double? = null
)

data class ParsedOdometerData(
    val odometerKm: Int? = null
)

object OcrScanner {
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeText(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                continuation.resume(visionText.text)
            }
            .addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
    }

    fun parsePumpText(rawText: String): ParsedPumpData {
        // Regex for monetary and decimal numbers like 12.34 or 12,34
        val numberRegex = Regex("""\b(\d+[\.,]\d{1,3})\b""")
        val matches = numberRegex.findAll(rawText)
            .mapNotNull { it.value.replace(',', '.').toDoubleOrNull() }
            .filter { it > 0.0 }
            .toList()

        if (matches.size < 2) {
            return ParsedPumpData()
        }

        // Typically: Unit Price is around 0.20 to 2.80 (fuel/kWh)
        // Total Cost is the highest value
        // Volume is the intermediate value
        val sorted = matches.sorted()
        val unitPriceCandidate = sorted.find { it in 0.25..3.0 }
        val remaining = sorted.filter { it != unitPriceCandidate }

        val totalCost = remaining.maxOrNull()
        val volume = remaining.minOrNull()

        return ParsedPumpData(
            volume = volume,
            totalCost = totalCost,
            unitPrice = unitPriceCandidate
        )
    }

    fun parseOdometerText(rawText: String, previousOdometer: Int = 0): ParsedOdometerData {
        // Look for 5 to 7 digit integers (odometers typically 10,000 to 999,999)
        val intRegex = Regex("""\b(\d{4,7})\b""")
        val candidates = intRegex.findAll(rawText)
            .mapNotNull { it.value.toIntOrNull() }
            .filter { it >= previousOdometer }
            .toList()

        return ParsedOdometerData(
            odometerKm = candidates.firstOrNull() ?: intRegex.findAll(rawText).mapNotNull { it.value.toIntOrNull() }.firstOrNull()
        )
    }
}
