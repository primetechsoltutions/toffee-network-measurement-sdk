package com.ptsl.network_sdk.network_data_worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine

object LocationHelper {

     suspend fun getCurrentLocation(context: Context): Pair<Double, Double> =
        suspendCancellableCoroutine { cont ->
            val hasLocationPermission =
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED ||
                        ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED

            if (!hasLocationPermission) {
                cont.resume(Pair(0.00, 0.00)) {}
                return@suspendCancellableCoroutine
            }
            val locationClient = LocationServices.getFusedLocationProviderClient(context)


            locationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                CancellationTokenSource().token
            ).addOnSuccessListener { location ->
                if (location != null) {
                    cont.resume(Pair(location.latitude, location.longitude)) {}
                } else {
                    cont.resume(Pair(0.0, 0.0)) {}
                }
            }.addOnFailureListener {
                cont.resume(Pair(0.0001, 0.0001)) {}
            }
        }
}