package com.fueltracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fueltracker.ui.screens.AddRefuelScreen
import com.fueltracker.ui.screens.AddVehicleDialog
import com.fueltracker.ui.screens.DashboardScreen
import com.fueltracker.ui.theme.FuelTrackerTheme
import com.fueltracker.ui.viewmodel.FuelTrackerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: FuelTrackerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FuelTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val uiState by viewModel.uiState.collectAsState()

                    var showAddVehicleDialog by remember { mutableStateOf(false) }
                    var showPriceSettingsDialog by remember { mutableStateOf(false) }

                    NavHost(navController = navController, startDestination = "dashboard") {
                        composable("dashboard") {
                            DashboardScreen(
                                uiState = uiState,
                                onSelectVehicle = { viewModel.selectActiveVehicle(it) },
                                onAddRefuelClick = { navController.navigate("add_refuel") },
                                onAddVehicleClick = { showAddVehicleDialog = true },
                                onDeleteLog = { viewModel.deleteRefuelLog(it) },
                                onSettingsClick = { showPriceSettingsDialog = true }
                            )
                        }

                        composable("add_refuel") {
                            val activeVehicle = uiState.activeVehicle
                            if (activeVehicle != null) {
                                AddRefuelScreen(
                                    activeVehicle = activeVehicle,
                                    suggestedStationName = uiState.nearestStation?.name,
                                    onResolveStation = { viewModel.resolveNearestStation() },
                                    onSaveRefuel = { viewModel.saveRefuelLog(it) },
                                    onBackClick = { navController.popBackStack() }
                                )
                            }
                        }
                    }

                    if (showAddVehicleDialog) {
                        AddVehicleDialog(
                            onDismiss = { showAddVehicleDialog = false },
                            onSaveVehicle = { vehicle ->
                                viewModel.saveVehicle(vehicle)
                                showAddVehicleDialog = false
                            }
                        )
                    }

                    if (showPriceSettingsDialog) {
                        com.fueltracker.ui.screens.PriceSettingsDialog(
                            initialApiKey = uiState.customApiKey,
                            initialApiUrl = uiState.customApiUrl,
                            onSave = { apiKey, apiUrl ->
                                viewModel.updateCustomApiKey(apiKey)
                                viewModel.updateCustomApiUrl(apiUrl)
                            },
                            onSyncNow = {
                                viewModel.syncPricesNow()
                            },
                            onDismiss = { showPriceSettingsDialog = false }
                        )
                    }
                }
            }
        }
    }
}
