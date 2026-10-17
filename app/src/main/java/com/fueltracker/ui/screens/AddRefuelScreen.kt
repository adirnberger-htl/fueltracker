package com.fueltracker.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fueltracker.domain.engine.FuelMath
import com.fueltracker.domain.model.PowertrainType
import com.fueltracker.domain.model.RefuelLog
import com.fueltracker.domain.model.Vehicle
import com.fueltracker.util.ImageStorageHelper
import com.fueltracker.util.OcrScanner
import kotlinx.coroutines.launch
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isEv = activeVehicle.powertrainType == PowertrainType.EV
    val unitLabel = activeVehicle.primaryFuelType.unitLabel

    var volumeText by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var totalCostText by remember { mutableStateOf("") }
    var odometerText by remember { mutableStateOf(activeVehicle.currentOdometerKm.toString()) }
    var stationNameText by remember { mutableStateOf(suggestedStationName ?: "") }
    var isFullFill by remember { mutableStateOf(true) }
    var notesText by remember { mutableStateOf("") }

    var stagedReceiptUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var tempCaptureUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val stagedReceiptUri = stagedReceiptUriString?.let { Uri.parse(it) }
    val tempCaptureUri = tempCaptureUriString?.let { Uri.parse(it) }
    var showCaptureSourceDialog by remember { mutableStateOf(false) }
    var isScanningOcr by remember { mutableStateOf(false) }
    var ocrScanStatusMessage by remember { mutableStateOf<String?>(null) }

    val receiptThumbnail = remember(stagedReceiptUri) {
        stagedReceiptUri?.let { uri ->
            try {
                val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)?.asImageBitmap()
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    LaunchedEffect(suggestedStationName) {
        if (suggestedStationName != null && stationNameText.isEmpty()) {
            stationNameText = suggestedStationName
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            ImageStorageHelper.clearTempCaptures(context)
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

    fun processCapturedReceipt(uri: Uri) {
        coroutineScope.launch {
            isScanningOcr = true
            ocrScanStatusMessage = "Analyzing image with ML Kit..."
            try {
                val rawText = OcrScanner.recognizeTextFromUri(context, uri)
                val receiptData = OcrScanner.parseReceiptText(rawText)

                if (receiptData.volume != null) {
                    volumeText = String.format(Locale.US, "%.2f", receiptData.volume)
                }
                if (receiptData.totalCost != null) {
                    totalCostText = String.format(Locale.US, "%.2f", receiptData.totalCost)
                }
                if (receiptData.unitPrice != null) {
                    priceText = String.format(Locale.US, "%.3f", receiptData.unitPrice)
                }
                if (!receiptData.stationName.isNullOrBlank() && stationNameText.isBlank()) {
                    stationNameText = receiptData.stationName
                }

                // Also check for odometer if scanning instrument cluster
                val odo = OcrScanner.parseOdometerText(rawText, activeVehicle.currentOdometerKm)
                if (odo.odometerKm != null) {
                    odometerText = odo.odometerKm.toString()
                }

                recalculateMissing()
                ocrScanStatusMessage = "Scan complete! Extracted refuel metrics."
            } catch (e: Exception) {
                ocrScanStatusMessage = "Could not parse text automatically. You can fill in the values manually."
            } finally {
                isScanningOcr = false
            }
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = tempCaptureUriString?.let { Uri.parse(it) }
        if (success && uri != null) {
            stagedReceiptUriString = uri.toString()
            processCapturedReceipt(uri)
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            stagedReceiptUriString = uri.toString()
            processCapturedReceipt(uri)
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

            // Quick Station Resolution & Camera Capture Trigger
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
                IconButton(onClick = { showCaptureSourceDialog = true }) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Scan Receipt or Pump", tint = MaterialTheme.colorScheme.secondary)
                }
            }

            // OCR Scanning Progress or Status Banner
            if (isScanningOcr) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = ocrScanStatusMessage ?: "Scanning...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            } else if (ocrScanStatusMessage != null && stagedReceiptUri != null) {
                Text(
                    text = ocrScanStatusMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Attached Receipt Preview Card
            if (stagedReceiptUri != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (receiptThumbnail != null) {
                            Image(
                                bitmap = receiptThumbnail,
                                contentDescription = "Receipt Thumbnail",
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                Icons.Default.Receipt,
                                contentDescription = "Receipt Photo Attached",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Receipt Photo Attached",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Photo will be saved with this refuel log",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showCaptureSourceDialog = true }) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Retake Photo")
                        }
                        IconButton(onClick = { stagedReceiptUriString = null }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove Photo", tint = MaterialTheme.colorScheme.error)
                        }
                    }
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

                    // Persist staged receipt image if present
                    val savedImagePath = stagedReceiptUri?.let { uri ->
                        ImageStorageHelper.saveStagedImageToInternalStorage(context, uri)
                    }

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
                        notes = notesText,
                        receiptImagePath = savedImagePath
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

    if (showCaptureSourceDialog) {
        AlertDialog(
            onDismissRequest = { showCaptureSourceDialog = false },
            title = { Text("Scan Receipt or Pump") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Capture a photo of your receipt or fuel pump display to extract volume, price, and total cost.")
                    Button(
                        onClick = {
                            showCaptureSourceDialog = false
                            try {
                                val uri = ImageStorageHelper.createTempPictureUri(context)
                                tempCaptureUriString = uri.toString()
                                takePictureLauncher.launch(uri)
                            } catch (_: Exception) {
                                ocrScanStatusMessage = "Camera could not be launched. Check device camera."
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Take Photo with Camera")
                    }
                    OutlinedButton(
                        onClick = {
                            showCaptureSourceDialog = false
                            pickImageLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose from Gallery")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCaptureSourceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
