package com.fueltracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fueltracker.domain.model.FuelType
import com.fueltracker.domain.model.PowertrainType
import com.fueltracker.domain.model.Vehicle
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVehicleDialog(
    onDismiss: () -> Unit,
    onSaveVehicle: (Vehicle) -> Unit
) {
    var nameText by remember { mutableStateOf("") }
    var selectedPowertrain by remember { mutableStateOf(PowertrainType.ICE) }
    var selectedFuelType by remember { mutableStateOf(FuelType.DIESEL) }
    var capacityText by remember { mutableStateOf("50.0") }
    var baselineText by remember { mutableStateOf("5.0") }
    var odometerText by remember { mutableStateOf("50000") }

    var fuelDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Vehicle", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("Vehicle Name (e.g. Golf TDI)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Powertrain Type", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PowertrainType.values().forEach { pt ->
                        FilterChip(
                            selected = selectedPowertrain == pt,
                            onClick = {
                                selectedPowertrain = pt
                                selectedFuelType = when (pt) {
                                    PowertrainType.EV -> FuelType.ELECTRIC_AC
                                    PowertrainType.ICE -> FuelType.DIESEL
                                    PowertrainType.PHEV -> FuelType.SUPER_95_E10
                                }
                                if (pt == PowertrainType.EV) {
                                    capacityText = "60.0"
                                    baselineText = "16.0"
                                }
                            },
                            label = { Text(pt.name) }
                        )
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = fuelDropdownExpanded,
                    onExpandedChange = { fuelDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedFuelType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Fuel / Energy Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = fuelDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = fuelDropdownExpanded,
                        onDismissRequest = { fuelDropdownExpanded = false }
                    ) {
                        FuelType.values().forEach { ft ->
                            DropdownMenuItem(
                                text = { Text(ft.displayName) },
                                onClick = {
                                    selectedFuelType = ft
                                    fuelDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = capacityText,
                    onValueChange = { capacityText = it },
                    label = { Text("Tank / Battery Capacity (${selectedFuelType.unitLabel})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = baselineText,
                    onValueChange = { baselineText = it },
                    label = { Text("Baseline WLTP Rating (${if (selectedPowertrain == PowertrainType.EV) "kWh/100km" else "L/100km"})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = odometerText,
                    onValueChange = { odometerText = it },
                    label = { Text("Initial Odometer (km)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val isValid = nameText.isNotBlank() &&
                    capacityText.toDoubleOrNull() != null &&
                    baselineText.toDoubleOrNull() != null &&
                    odometerText.toIntOrNull() != null

            Button(
                onClick = {
                    val vehicle = Vehicle(
                        id = UUID.randomUUID().toString(),
                        name = nameText.trim(),
                        powertrainType = selectedPowertrain,
                        primaryFuelType = selectedFuelType,
                        capacityUnits = capacityText.toDouble(),
                        baselineConsumption = baselineText.toDouble(),
                        currentOdometerKm = odometerText.toInt()
                    )
                    onSaveVehicle(vehicle)
                    onDismiss()
                },
                enabled = isValid
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
