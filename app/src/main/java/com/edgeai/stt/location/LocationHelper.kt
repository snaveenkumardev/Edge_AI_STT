package com.edgeai.stt.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat

class LocationHelper(private val context: Context) {

    fun hasPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    @SuppressLint("MissingPermission")
    fun getCurrentLocationFormatted(): String {
        if (!hasPermission()) {
            return "Lat: 37.7749° N, Lng: -122.4194° W (Default/Simulated GPS)"
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        var bestLocation: Location? = null

        try {
            val providers = locationManager?.getProviders(true) ?: emptyList()
            for (provider in providers) {
                val loc = locationManager?.getLastKnownLocation(provider) ?: continue
                if (bestLocation == null || loc.accuracy < bestLocation.accuracy) {
                    bestLocation = loc
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return if (bestLocation != null) {
            val lat = String.format("%.4f", bestLocation.latitude)
            val lng = String.format("%.4f", bestLocation.longitude)
            "Lat: $lat°, Lng: $lng° (Accuracy: ${bestLocation.accuracy.toInt()}m)"
        } else {
            ""
        }
    }
}
