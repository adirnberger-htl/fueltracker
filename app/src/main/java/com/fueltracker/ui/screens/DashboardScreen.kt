package com.fueltracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueltracker.domain.engine.EcoRating
import com.fueltracker.domain.model.PowertrainType
import com.fueltracker.domain.model.RefuelLog
import com.fueltracker.domain.model.Vehicle
import com.fueltracker.ui.theme.AggressiveRed
import com.fueltracker.ui.theme.EcoGreen
import com.fueltracker.ui.theme.ModerateOrange
import com.fueltracker.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: UiState,
    onSelectVehicle: (String) -> Unit,
    onAddRefuelClick: () -> Unit,
    onAddVehicleClick: () -> Unit,
    onDeleteLog: (RefuelLog) -> Unit,
    onSettingsClick: () -> Unit = {}
) {
    var vehicleMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { vehicleMenuExpanded = true }
                    ) {
                        Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.activeVehicle?.name ?: "Select Vehicle",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        DropdownMenu(
                            expanded = vehicleMenuExpanded,
                            onDismissRequest = { vehicleMenuExpanded = false }
                        ) {
                            uiState.vehicles.forEach { vehicle ->
                                DropdownMenuItem(
                                    text = { Text(vehicle.name) },
                                    onClick = {
                                        onSelectVehicle(vehicle.id)
                                        vehicleMenuExpanded = false
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("+ Add New Vehicle", fontWeight = FontWeight.Bold) },
                                onClick = {
                                    vehicleMenuExpanded = false
                                    onAddVehicleClick()
                                }
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Price Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddRefuelClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Refuel")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            val vehicle = uiState.activeVehicle
            if (vehicle != null) {
                // 1. Vehicle Tank / Status Card
                item {
                    VehicleStatusCard(vehicle = vehicle, uiState = uiState)
                }

                // 2. Depletion & Monthly Budget Card
                item {
                    ForecastBudgetCard(uiState = uiState)
                }

                // 3. Efficiency & Eco Rating Card
                item {
                    EfficiencyCard(uiState = uiState)
                }

                // 4. Price Intelligence & Recommendation Card
                item {
                    PriceRecommendationCard(uiState = uiState)
                }

                // 5. Recent Logs Header
                item {
                    Text(
                        text = "Recent Activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (uiState.logs.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Text(
                                text = "No logs recorded yet. Tap + to add first refuel.",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else {
                    items(uiState.logs, key = { it.id }) { log ->
                        RefuelLogItem(log = log, onDelete = { onDeleteLog(log) })
                    }
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = "No vehicles found", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = onAddVehicleClick) {
                                Text("Create Vehicle")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VehicleStatusCard(vehicle: Vehicle, uiState: UiState) {
    val forecast = uiState.forecast
    val unitLabel = vehicle.primaryFuelType.unitLabel
    val isEv = vehicle.powertrainType == PowertrainType.EV

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEv) "Battery Status" else "Fuel Tank",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${vehicle.currentOdometerKm} km",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val remainingPercent = (forecast?.remainingPercent ?: 50.0).toFloat() / 100f
            LinearProgressIndicator(
                progress = { remainingPercent.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp)),
                color = when {
                    remainingPercent < 0.15f -> AggressiveRed
                    remainingPercent < 0.35f -> ModerateOrange
                    else -> MaterialTheme.colorScheme.primary
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${forecast?.remainingUnits ?: (vehicle.capacityUnits * 0.5)} / ${vehicle.capacityUnits} $unitLabel (${(remainingPercent * 100).toInt()}%)",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "~${forecast?.remainingRangeKm ?: 0} km range",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ForecastBudgetCard(uiState: UiState) {
    val forecast = uiState.forecast ?: return
    val sdf = remember { SimpleDateFormat("EEE, dd MMM", Locale.getDefault()) }
    val emptyDateStr = sdf.format(Date(forecast.estimatedEmptyDateEpochMs))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Forecast & Budget Projection",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Empty Tank", style = MaterialTheme.typography.labelSmall)
                    Text(text = emptyDateStr, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text(text = "Cost to Fill", style = MaterialTheme.typography.labelSmall)
                    Text(text = "€${String.format(Locale.US, "%.2f", forecast.estimatedCostToFull)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text(text = "Monthly Cost", style = MaterialTheme.typography.labelSmall)
                    Text(text = "€${String.format(Locale.US, "%.2f", forecast.projectedMonthlyExpense)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun EfficiencyCard(uiState: UiState) {
    val efficiency = uiState.efficiency ?: return
    val vehicle = uiState.activeVehicle ?: return
    val unitLabel = if (vehicle.powertrainType == PowertrainType.EV) "kWh/100km" else "L/100km"

    val (badgeColor, badgeText) = when {
        efficiency.overallDeviationPercent <= 5.0 -> EcoGreen to "ECO (Efficient)"
        efficiency.overallDeviationPercent <= 15.0 -> ModerateOrange to "MODERATE"
        else -> AggressiveRed to "HIGH CONSUMPTION"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Efficiency Analysis", style = MaterialTheme.typography.titleSmall)
                Surface(
                    color = badgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Actual Average", style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = "${efficiency.averageConsumption} $unitLabel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column {
                    Text(text = "Baseline (WLTP)", style = MaterialTheme.typography.labelSmall)
                    Text(
                        text = "${vehicle.baselineConsumption} $unitLabel",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Column {
                    Text(text = "Variance", style = MaterialTheme.typography.labelSmall)
                    val sign = if (efficiency.overallDeviationPercent > 0) "+" else ""
                    Text(
                        text = "$sign${efficiency.overallDeviationPercent}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = badgeColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun PriceRecommendationCard(uiState: UiState) {
    val rec = uiState.recommendation ?: return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.LocalGasStation,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Cheapest Refill Window: ${rec.recommendedTimeWindow}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = rec.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
fun RefuelLogItem(log: RefuelLog, onDelete: () -> Unit) {
    val sdf = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    val dateStr = sdf.format(Date(log.timestampEpochMs))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = log.stationName ?: "Refuel", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(text = "$dateStr • ${log.odometerKm} km", style = MaterialTheme.typography.bodySmall)
                if (!log.isFullFill) {
                    Text(text = "Partial fill", color = ModerateOrange, style = MaterialTheme.typography.labelSmall)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(text = "€${String.format(Locale.US, "%.2f", log.totalCost)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Text(text = "${log.unitsFilled} L @ €${String.format(Locale.US, "%.3f", log.unitPrice)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
