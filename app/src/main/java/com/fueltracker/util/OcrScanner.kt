package com.fueltracker.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.fueltracker.domain.engine.FuelMath
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
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

data class ParsedReceiptData(
    val volume: Double? = null,
    val totalCost: Double? = null,
    val unitPrice: Double? = null,
    val stationName: String? = null
)

object OcrScanner {
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    fun sortVisionTextLinesSpatially(visionText: Text): String {
        val allLines = visionText.textBlocks.flatMap { it.lines }
        if (allLines.isEmpty()) {
            return visionText.text
        }
        val sortedLines = allLines.sortedWith(
            Comparator { a, b ->
                val boxA = a.boundingBox
                val boxB = b.boundingBox
                val topA = boxA?.top ?: 0
                val topB = boxB?.top ?: 0
                val heightA = boxA?.height() ?: 20
                val heightB = boxB?.height() ?: 20
                val avgHeight = (heightA + heightB) / 2
                val rowThreshold = (avgHeight * 0.5).toInt().coerceAtLeast(10)

                if (kotlin.math.abs(topA - topB) <= rowThreshold) {
                    (boxA?.left ?: 0).compareTo(boxB?.left ?: 0)
                } else {
                    topA.compareTo(topB)
                }
            }
        )
        return sortedLines.joinToString("\n") { it.text }
    }

    suspend fun recognizeText(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                continuation.resume(sortVisionTextLinesSpatially(visionText))
            }
            .addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
    }

    suspend fun recognizeTextFromUri(context: Context, uri: Uri): String = suspendCancellableCoroutine { continuation ->
        try {
            val image = InputImage.fromFilePath(context, uri)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    continuation.resume(sortVisionTextLinesSpatially(visionText))
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
        } catch (e: Exception) {
            continuation.resumeWithException(e)
        }
    }

    fun parsePumpText(rawText: String): ParsedPumpData {
        // Discard lines containing tax or VAT keywords
        val taxRegex = Regex("""(?i)\b(mwst|ust|vat|tax|netto|steuer|steuersatz)\b|%""")
        val filteredLines = rawText.lines().filterNot { taxRegex.containsMatchIn(it) }
        val filteredText = filteredLines.joinToString("\n")

        // Regex for monetary and decimal numbers like 12.34 or 12,34
        val numberRegex = Regex("""\b(\d+[\.,]\d{1,3})\b""")
        val matches = numberRegex.findAll(filteredText)
            .mapNotNull { it.value.replace(',', '.').toDoubleOrNull() }
            .filter { it > 0.0 }
            .toList()

        if (matches.size < 2) {
            return ParsedPumpData()
        }

        // Typically: Unit Price is around 0.20 to 3.0 (fuel/kWh)
        // Total Cost is the highest value
        // Volume is the intermediate value
        val sorted = matches.sorted()
        val unitPriceCandidate = sorted.find { it in 0.25..3.0 }
        val remaining = sorted.filter { it != unitPriceCandidate }

        val totalCost = remaining.maxOrNull()
        var volume = remaining.minOrNull()

        // Verify with FuelMath: Volume * unitPrice ≈ totalCost
        if (totalCost != null && unitPriceCandidate != null && unitPriceCandidate > 0.0) {
            if (volume != null) {
                val expectedCost = volume * unitPriceCandidate
                val relativeDiff = kotlin.math.abs(expectedCost - totalCost) / totalCost
                if (relativeDiff > 0.08) {
                    volume = FuelMath.roundToDecimals(totalCost / unitPriceCandidate, 2)
                }
            } else {
                volume = FuelMath.roundToDecimals(totalCost / unitPriceCandidate, 2)
            }
        }

        return ParsedPumpData(
            volume = volume,
            totalCost = totalCost,
            unitPrice = unitPriceCandidate
        )
    }

    fun parseReceiptText(rawText: String): ParsedReceiptData {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        // 1. Station Brand / Name Extraction
        val knownBrands = listOf(
            "Shell", "Aral", "OMV", "BP", "Jet", "Total", "Eni", "Agip",
            "Avia", "Esso", "Repsol", "Circle K", "Chevron", "Mobil", "Texaco", "Turmöl",
            "Diskont", "Hem", "Orlen", "Q8", "Tamoil", "Socar"
        )
        val footerPhrases = listOf(
            "vielen dank", "danke", "besuch", "auf wiedersehen", "wiedersehen",
            "kassabon", "kundenbeleg", "beleg", "terminal", "trace", "auth",
            "kartenzahlung", "kreditkarte", "mastercard", "visa", "maestro",
            "bargeld", "gegeben", "rueckgeld", "rückgeld", "wechselgeld",
            "uid", "atu", "fn ", "firmenbuch", "steuernummer", "ust-id",
            "quittung", "rechnung", "receipt", "invoice", "mwst", "steuer",
            "thank you", "visit", "goodbye", "card payment", "approved"
        )

        fun isFooterOrPaymentLine(line: String): Boolean {
            val lower = line.lowercase()
            return footerPhrases.any { lower.contains(it) }
        }

        fun isBrandMatch(line: String, brand: String): Boolean {
            if (!line.contains(brand, ignoreCase = true)) return false
            if (brand.equals("Total", ignoreCase = true)) {
                val lower = line.lowercase()
                val isMonetaryTotal = lower.contains("eur") ||
                        line.contains("€") ||
                        lower.contains("usd") ||
                        line.contains("$") ||
                        lower.contains("summe") ||
                        lower.contains("gesamt") ||
                        lower.contains("subtotal") ||
                        lower.contains("betrag") ||
                        lower.contains("endsumme") ||
                        lower.contains("zahlbetrag") ||
                        lower.contains("due") ||
                        line.any { it.isDigit() }
                return !isMonetaryTotal
            }
            return true
        }

        var extractedStation: String? = null
        for (line in lines.take(8)) {
            if (isFooterOrPaymentLine(line)) continue
            val foundBrand = knownBrands.find { isBrandMatch(line, it) }
            if (foundBrand != null) {
                extractedStation = line
                break
            }
        }
        if (extractedStation == null && lines.isNotEmpty()) {
            val candidate = lines.take(6).firstOrNull { line ->
                line.length in 3..40 &&
                        !line.any { it.isDigit() } &&
                        !isFooterOrPaymentLine(line) &&
                        !line.contains("eur", ignoreCase = true) &&
                        !line.contains("€") &&
                        !line.contains("total", ignoreCase = true) &&
                        !line.contains("summe", ignoreCase = true)
            }
            if (candidate != null) extractedStation = candidate
        }

        fun parseNumber(str: String): Double? {
            val cleaned = str.replace(',', '.').replace(" ", "")
            return cleaned.toDoubleOrNull()
        }

        val taxKeywords = Regex("""(?i)\b(mwst|ust|vat|tax|netto|brutto\s*netto|steuer|steuersatz|st\.satz|taxable)\b|%""")

        // 2. Multiplier expressions like "45,20 x 1,659" or "45.20 * 1.659"
        var multiplierVolume: Double? = null
        var multiplierUnitPrice: Double? = null
        val multiplierRegex = Regex("""(?i)\b(\d+[\.,]\d{1,3})\s*(?:[xX\*]|@)\s*(\d+[\.,]\d{2,4})\b""")
        for (line in lines) {
            if (taxKeywords.containsMatchIn(line)) continue
            val match = multiplierRegex.find(line)
            if (match != null) {
                val v = parseNumber(match.groupValues[1])
                val p = parseNumber(match.groupValues[2])
                if (v != null && v > 0.0 && p != null && p in 0.20..5.0) {
                    multiplierVolume = v
                    multiplierUnitPrice = p
                    break
                }
            }
        }

        // 3. Total Cost
        var totalCost: Double? = null
        val totalKeywords = Regex("""(?i)\b(total|summe|gesamt|gesamtbetrag|betrag|endsumme|zahlbetrag|amount|due)\b""")
        val currencyNumberRegex = Regex("""(\d+[\.,]\d{2})""")
        for (line in lines) {
            if (taxKeywords.containsMatchIn(line)) continue
            if (totalKeywords.containsMatchIn(line)) {
                val matches = currencyNumberRegex.findAll(line).toList()
                if (matches.isNotEmpty()) {
                    val candidate = parseNumber(matches.last().value)
                    if (candidate != null && candidate > 0.0) {
                        totalCost = candidate
                        break
                    }
                }
            }
        }

        // 4. Volume
        var volume: Double? = multiplierVolume
        if (volume == null) {
            val volumeRegex = Regex("""(?i)(?:(\d+[\.,]\d{1,3})\s*(?:l|liter|ltr|liters|kwh|gal|gallons)\b|(?:menge|volume|vol|volumen)[\s\:\=\(l\)]*(\d+[\.,]\d{1,3}))""")
            for (line in lines) {
                if (taxKeywords.containsMatchIn(line)) continue
                val match = volumeRegex.find(line)
                if (match != null) {
                    val numStr = match.groupValues[1].ifEmpty { match.groupValues[2] }
                    val cand = parseNumber(numStr)
                    if (cand != null && cand > 0.0) {
                        volume = cand
                        break
                    }
                }
            }
        }

        // 5. Unit Price
        var unitPrice: Double? = multiplierUnitPrice
        if (unitPrice == null) {
            val unitPriceRegex = Regex("""(?i)(?:(?:preis[\s\/]*(?:l|liter|ltr|kwh|gal)?|price[\s\/]*(?:l|liter|gal|g)?|eur\/l|€\/l|eur\/kwh|€\/kwh)[\s\:\=]*(\d+[\.,]\d{2,4})|(\d+[\.,]\d{2,4})\s*(?:eur\/l|€\/l|eur\/kwh|€\/kwh|\/l|\/liter|\/kwh))""")
            for (line in lines) {
                if (taxKeywords.containsMatchIn(line)) continue
                val match = unitPriceRegex.find(line)
                if (match != null) {
                    val numStr = match.groupValues[1].ifEmpty { match.groupValues[2] }
                    val cand = parseNumber(numStr)
                    if (cand != null && cand in 0.20..5.0) {
                        unitPrice = cand
                        break
                    }
                }
            }
        }

        // 6. Fallback heuristics if missing
        if (totalCost == null || volume == null || unitPrice == null) {
            val pumpData = parsePumpText(rawText)
            if (totalCost == null) totalCost = pumpData.totalCost
            if (volume == null) volume = pumpData.volume
            if (unitPrice == null) unitPrice = pumpData.unitPrice
        }

        // 7. FuelMath cross-validation and missing variable inference
        val mathResult = FuelMath.inferMissingVariable(volume, totalCost, unitPrice)
        if (mathResult != null) {
            totalCost = mathResult.totalCost
            unitPrice = mathResult.unitPrice
            volume = mathResult.volume
        } else if (totalCost != null && unitPrice != null && unitPrice > 0.0) {
            volume = FuelMath.roundToDecimals(totalCost / unitPrice, 2)
        }

        // Sanity validation: check if volume * unitPrice matches totalCost
        if (totalCost != null && unitPrice != null && volume != null && unitPrice > 0.0) {
            val expectedCost = volume * unitPrice
            val relativeDiff = kotlin.math.abs(expectedCost - totalCost) / totalCost
            if (relativeDiff > 0.08) {
                volume = FuelMath.roundToDecimals(totalCost / unitPrice, 2)
            }
        }

        return ParsedReceiptData(
            volume = volume,
            totalCost = totalCost,
            unitPrice = unitPrice,
            stationName = extractedStation
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
