package com.fueltracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrScannerTest {

    @Test
    fun parseReceiptText_germanReceipt() {
        val germanReceipt = """
            Shell Station Linz
            Wiener Strasse 123, 4020 Linz
            Datum: 12.10.2026 14:32
            Saeule 3
            Diesel
            Menge: 42,50 L
            Preis / L: 1,659 EUR
            Gesamtbetrag: EUR 70,51
            Vielen Dank fuer Ihren Besuch!
        """.trimIndent()

        val parsed = OcrScanner.parseReceiptText(germanReceipt)

        assertEquals(42.50, parsed.volume ?: 0.0, 0.01)
        assertEquals(70.51, parsed.totalCost ?: 0.0, 0.01)
        assertEquals(1.659, parsed.unitPrice ?: 0.0, 0.001)
        assertTrue(parsed.stationName?.contains("Shell", ignoreCase = true) == true)
    }

    @Test
    fun parseReceiptText_austrianOmvReceipt() {
        val omvReceipt = """
            OMV Tankstelle
            Pumpe 4
            Super 95 E10
            35.20 Liter x 1.629 EUR/L
            SUMME: 57.34 EUR
            BAR: 60.00 EUR
            Rueckgeld: 2.66 EUR
        """.trimIndent()

        val parsed = OcrScanner.parseReceiptText(omvReceipt)

        assertEquals(35.20, parsed.volume ?: 0.0, 0.01)
        assertEquals(57.34, parsed.totalCost ?: 0.0, 0.01)
        assertEquals(1.629, parsed.unitPrice ?: 0.0, 0.001)
        assertTrue(parsed.stationName?.contains("OMV", ignoreCase = true) == true)
    }

    @Test
    fun parseReceiptText_englishBpReceipt() {
        val englishReceipt = """
            BP Gas Station #402
            104 High Street
            Pump 02
            Regular Unleaded
            Volume: 12.45 Gal
            Price/Gal: 3.499
            Total USD: 43.56
            Card Payment Approved
        """.trimIndent()

        val parsed = OcrScanner.parseReceiptText(englishReceipt)

        assertEquals(12.45, parsed.volume ?: 0.0, 0.01)
        assertEquals(43.56, parsed.totalCost ?: 0.0, 0.01)
        assertEquals(3.499, parsed.unitPrice ?: 0.0, 0.001)
        assertTrue(parsed.stationName?.contains("BP", ignoreCase = true) == true)
    }

    @Test
    fun parseReceiptText_pumpFormatFallback() {
        val pumpText = """
            TOTAL: €64.80
            VOL: 38.80 L
            EUR/L: 1.670
        """.trimIndent()

        val parsed = OcrScanner.parseReceiptText(pumpText)

        assertEquals(38.80, parsed.volume ?: 0.0, 0.01)
        assertEquals(64.80, parsed.totalCost ?: 0.0, 0.01)
        assertEquals(1.670, parsed.unitPrice ?: 0.0, 0.001)
    }

    @Test
    fun parsePumpText_validValues() {
        val sample = "EUR/L 1.719 \n 45.00 L \n TOTAL 77.36 EUR"
        val parsed = OcrScanner.parsePumpText(sample)

        assertEquals(45.00, parsed.volume ?: 0.0, 0.01)
        assertEquals(77.36, parsed.totalCost ?: 0.0, 0.01)
        assertEquals(1.719, parsed.unitPrice ?: 0.0, 0.001)
    }

    @Test
    fun parseOdometerText_findsMatchingMileage() {
        val dash = "SPEED: 95 KM/H \n ODOMETER: 86450 KM"
        val parsed = OcrScanner.parseOdometerText(dash, previousOdometer = 85200)

        assertEquals(86450, parsed.odometerKm)
    }

    @Test
    fun parseReceiptText_ignoresMwStTaxAndExtractsRealVolume() {
        val receiptWithTax = """
            Diskont Tankstelle Linz
            Wiener Strasse 50
            Diesel B7
            48.20 x 1.589
            SUMME: 76.59 EUR
            MwSt 20% 12.77 EUR
            Netto: 63.82 EUR
            Vielen Dank fuer Ihren Besuch!
        """.trimIndent()

        val parsed = OcrScanner.parseReceiptText(receiptWithTax)

        assertEquals(48.20, parsed.volume ?: 0.0, 0.01)
        assertEquals(76.59, parsed.totalCost ?: 0.0, 0.01)
        assertEquals(1.589, parsed.unitPrice ?: 0.0, 0.001)
        assertEquals("Diskont Tankstelle Linz", parsed.stationName)
    }

    @Test
    fun parseReceiptText_footerSloganIgnoredEvenIfFirstOrOnlyGreeting() {
        val receipt = """
            Turmöl Quick Station
            Säule 2
            Super 95
            40.00 L
            Preis/L 1.620
            TOTAL: 64.80 EUR
            MwSt. inkl. 20%
            Kartenzahlung Beleg
            Auf Wiedersehen!
        """.trimIndent()

        val parsed = OcrScanner.parseReceiptText(receipt)

        assertEquals(40.00, parsed.volume ?: 0.0, 0.01)
        assertEquals(64.80, parsed.totalCost ?: 0.0, 0.01)
        assertEquals(1.620, parsed.unitPrice ?: 0.0, 0.001)
        assertTrue(parsed.stationName?.contains("Turmöl", ignoreCase = true) == true)
        assertFalse(parsed.stationName?.contains("Auf Wiedersehen", ignoreCase = true) == true)
        assertFalse(parsed.stationName?.contains("TOTAL", ignoreCase = true) == true)
    }

    @Test
    fun parseReceiptText_deducesMissingVolumeFromTotalAndPriceViaFuelMath() {
        val receiptNoVolumeLabel = """
            Eni Tankstelle
            Diesel
            Preis / L: 1.500 EUR
            Gesamtbetrag: 60.00 EUR
            MwSt 20%: 10.00 EUR
            Vielen Dank!
        """.trimIndent()

        val parsed = OcrScanner.parseReceiptText(receiptNoVolumeLabel)

        assertEquals(40.00, parsed.volume ?: 0.0, 0.01)
        assertEquals(60.00, parsed.totalCost ?: 0.0, 0.01)
        assertEquals(1.500, parsed.unitPrice ?: 0.0, 0.001)
        assertTrue(parsed.stationName?.contains("Eni", ignoreCase = true) == true)
    }

    @Test
    fun parsePumpText_ignoresTaxLines() {
        val pumpWithTax = """
            EUR/L 1.600
            SUMME 80.00 EUR
            MwSt 20% 13.33
            Menge 50.00
        """.trimIndent()

        val parsed = OcrScanner.parsePumpText(pumpWithTax)

        assertEquals(50.00, parsed.volume ?: 0.0, 0.01)
        assertEquals(80.00, parsed.totalCost ?: 0.0, 0.01)
        assertEquals(1.600, parsed.unitPrice ?: 0.0, 0.001)
    }
}

