package com.ana.theflow.utilities

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

/**
 * Owns the real runtime permission request for device location (the manifest declares
 * ACCESS_FINE_LOCATION/ACCESS_COARSE_LOCATION, but nothing in the app ever actually asked for
 * them before this - checkSelfPermission alone always returns DENIED on a real install) plus a
 * single active location fetch, since getLastKnownLocation alone can be null forever on a device
 * that has never had a location fix cached by any app.
 *
 * Must be constructed as an eager field (not lazily on first use) on a Fragment, so
 * registerForActivityResult runs before the fragment reaches CREATED state.
 */
class DeviceLocationProvider(private val fragment: Fragment) {

    private var pendingPermissionCallback: ((Boolean) -> Unit)? = null

    private val permissionLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val callback = pendingPermissionCallback
        pendingPermissionCallback = null
        callback?.invoke(granted)
    }

    fun hasPermission(): Boolean {
        val context = fragment.context ?: return false
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    // Requests the permission if not already granted/denied-and-asked-again; calls back once
    // either way. Safe to call repeatedly - a prior denial just resolves false immediately
    // instead of nagging (RequestMultiplePermissions surfaces the OS's own rules for that).
    fun ensurePermission(onResult: (Boolean) -> Unit) {
        if (hasPermission()) {
            onResult(true)
            return
        }
        if (!fragment.isAdded) {
            onResult(false)
            return
        }
        pendingPermissionCallback = onResult
        permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    // Resolves permission, then returns a real location: last-known if it's fresh enough,
    // otherwise actively requests a single fresh fix (bounded by a timeout so a provider with no
    // signal - a fresh emulator, indoors, airplane mode - can't hang this indefinitely), falling
    // back to a stale last-known reading, then null.
    fun currentLocation(onResult: (Location?) -> Unit) {
        ensurePermission { granted ->
            if (!granted) {
                onResult(null)
                return@ensurePermission
            }
            fetchLocation(onResult)
        }
    }

    private fun fetchLocation(onResult: (Location?) -> Unit) {
        val context = fragment.context
        if (context == null) {
            onResult(null)
            return
        }
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null) {
            onResult(null)
            return
        }

        val cached = lastKnownLocation(locationManager)
        if (cached != null && System.currentTimeMillis() - cached.time < FRESH_CACHE_MS) {
            onResult(cached)
            return
        }

        val provider = when {
            runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) -> LocationManager.GPS_PROVIDER
            runCatching { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider == null) {
            onResult(cached)
            return
        }

        var delivered = false
        val handler = Handler(Looper.getMainLooper())
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (delivered) return
                delivered = true
                runCatching { locationManager.removeUpdates(this) }
                onResult(location)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }

        try {
            locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        } catch (_: SecurityException) {
            onResult(cached)
            return
        }

        handler.postDelayed({
            if (!delivered) {
                delivered = true
                runCatching { locationManager.removeUpdates(listener) }
                onResult(cached)
            }
        }, SINGLE_FIX_TIMEOUT_MS)
    }

    private fun lastKnownLocation(locationManager: LocationManager): Location? {
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { provider ->
                runCatching {
                    if (locationManager.isProviderEnabled(provider)) locationManager.getLastKnownLocation(provider) else null
                }.getOrNull()
            }
            .maxByOrNull { it.time }
    }

    private companion object {
        const val FRESH_CACHE_MS = 5 * 60 * 1000L
        const val SINGLE_FIX_TIMEOUT_MS = 4000L
    }
}
