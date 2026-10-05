package com.fueltracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueltracker.domain.engine.FuelMath
import com.fueltracker.domain.model.PowertrainType
import com.fueltracker.domain.model.RefuelLog
import com.fueltracker.domain.model.Vehicle
import com.fueltracker.util.OcrScanner
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRefuelScreen(
    activeVehicle: Vehicle,
    suggestedStationName: String?,
    onResolveStation: () -> Unit,
    onSaveRefuel: (RefuelLog) -> Unit,
    onBackClick: () -> Unit
) {
    val isEv = activeVehicle.powertrainType == PowertrainType.EV
    val unitLabel = activeVehicle.primaryFuelType.unitLabel

    var volumeText by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var totalCostText by remember { mutableStateOf("") }
    var odometerText by remember { mutableStateOf(activeVehicle.currentOdometerKm.toString()) }
    var stationNameText by remember { mutableStateOf(suggestedStationName ?: "") }
    var isFullFill by remember { mutableStateOf(true) }
    var notesText by remember { mutableStateOf("") }

    var showOcrSimDialog by remember { mutableStateOf(false) }

    LaunchedEffect(suggestedStationName) {
        if (suggestedStationName != null && stationNameText.isEmpty()) {
            stationNameText = suggestedStationName
        }
    }

    // Auto-calculate whenever user enters values
    fun recalculateMissing() {
        val vol = volumeText.toDoubleOrNull()
        val cost = totalCostText.toDoubleOrNull()
        val price = priceText.toDoubleOrNull()

        val result = FuelMath.inferMissingVariable(vol, cost, price)
        if (result != null) {
            if (priceText.isEmpty() && result.unitPrice > 0) {
                priceText = String.format(Locale.US, "%.3f", result.unitPrice)
            } else if (totalCostText.isEmpty() && result.totalCost > 0) {
                totalCostText = String.format(Locale.US, "%.2f", result.totalCost)
            } else if (volumeText.isEmpty() && result.volume > 0) {
                volumeText = String.format(Locale.US, "%.2f", result.volume)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEv) "Log EV Charging" else "Log Refueling", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Vehicle Info Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activeVehicle.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "${activeVehicle.primaryFuelType.displayName} • ${activeVehicle.currentOdometerKm} km",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Quick Station Resolution
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = stationNameText,
                    onValueChange = { stationNameText = it },
                    label = { Text("Gas / Charging Station") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onResolveStation) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Detect Nearest Station", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { showOcrSimDialog = true }) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Camera OCR Scan", tint = MaterialTheme.colorScheme.secondary)
                }
            }

            // 2-Variable Auto-Calculation Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Fuel & Price (Enter any 2 to auto-fill 3rd)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = volumeText,
                        onValueChange = {
                            volumeText = it
                            recalculateMissing()
                        },
                        label = { Text("Volume ($unitLabel)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = priceText,
                        onValueChange = {
                            priceText = it
                            recalculateMissing()
                        },
                        label = { Text("Price per $unitLabel (€)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = totalCostText,
                        onValueChange = {
                            totalCostText = it
                            recalculateMissing()
                        },
                        label = { Text("Total Cost (€)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Odometer & Full Tank Toggle
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = odometerText,
                        onValueChange = { odometerText = it },
                        label = { Text("Current Odometer (km)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Full Tank Fill", fontWeight = FontWeight.Bold)
                            Text(
                                text = if (isFullFill) "Calculates exact consumption" else "Partial fill (deferred math)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isFullFill,
                            onCheckedChange = { isFullFill = it }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it },
                label = { Text("Notes (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Save Button
            val isFormValid = volumeText.toDoubleOrNull() != null &&
                    totalCostText.toDoubleOrNull() != null &&
                    odometerText.toIntOrNull() != null

            Button(
                onClick = {
                    val vol = volumeText.toDouble()
                    val total = totalCostText.toDouble()
                    val price = priceText.toDoubleOrNull() ?: (total / vol)
                    val odo = odometerText.toInt()

                    val log = RefuelLog(
                        id = UUID.randomUUID().toString(),
                        vehicleId = activeVehicle.id,
                        timestampEpochMs = System.currentTimeMillis(),
                        odometerKm = odo,
                        unitsFilled = vol,
                        totalCost = total,
                        unitPrice = price,
                        isFullFill = isFullFill,
                        stationName = stationNameText.ifBlank { "Station" },
                        notes = notesText
                    )
                    onSaveRefuel(log)
                    onBackClick()
                },
                enabled = isFormValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("Save Refuel Log", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showOcrSimDialog) {
        AlertDialog(
            onDismissRequest = { showOcrSimDialog = false },
            title = { Text("OCR Quick Scan") },
            text = {
                Text("Camera OCR captures pump displays and dashboard odometers instantly using Google ML Kit. Apply scanned sample?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Apply OCR parsed values
                        val sampleOcr = "TOTAL: €64.80 \nVOL: 38.80 L \nEUR/L: 1.670"
                        val parsed = OcrScanner.parsePumpText(sampleOcr)
                        if (parsed.volume != null) volumeText = parsed.volume.toString()
                        if (parsed.totalCost != null) totalCostText = parsed.totalCost.toString()
                        if (parsed.unitPrice != null) priceText = parsed.unitPrice.toString()

                        val sampleDash = "ODOMETER: 85600 KM"
                        val odoParsed = OcrScanner.parseOdometerText(sampleDash, activeVehicle.currentOdometerKm)
                        if (odoParsed.odometerKm != null) odometerText = odoParsed.odometerKm.toString()

                        showOcrSimDialog = false
                    }
                ) {
                    Text("Apply Scan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOcrSimDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
