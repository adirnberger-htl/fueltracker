package com.fueltracker.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object LocationHelper {

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Location? = suspendCancellableCoroutine { continuation ->
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()

            fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { location ->
                    continuation.resume(location)
                }
                .addOnFailureListener {
                    continuation.resume(null)
                }
                .addOnCanceledListener {
                    continuation.resume(null)
                }
        } catch (e: Exception) {
            continuation.resume(null)
        }
    }
}
