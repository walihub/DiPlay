package com.shilapi.xcertplay.vehicle.arcfox

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import com.shilapi.xcertplay.vehicle.VehicleProfiles

/** Platform-signed builds grant only their own microphone and foreground location permissions. */
internal object ArcfoxRuntimePermissions {
    private const val TAG = "DiPlay-ArcfoxPermissions"
    internal val permissions = listOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

    /** A few local Binder calls; no ADB connection, network wait, or permission dialog. */
    fun grantAtStartup(context: Context): String {
        val app = context.applicationContext
        return grantMissing(
            arcfox = VehicleProfiles.isArcfox(app),
            canGrant = app.checkSelfPermission("android.permission.GRANT_RUNTIME_PERMISSIONS") == PackageManager.PERMISSION_GRANTED,
            isGranted = { app.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED },
            grant = { permission ->
                PackageManager::class.java.getMethod("grantRuntimePermission", String::class.java,
                    String::class.java, android.os.UserHandle::class.java)
                    .invoke(app.packageManager, app.packageName, permission, Process.myUserHandle())
            },
            failed = { permission, error -> Log.w(TAG, "System grant failed for $permission", error) },
        ).also { if (it != "not_arcfox") Log.i(TAG, it) }
    }

    fun repair(context: Context, complete: (String) -> Unit) { complete(grantAtStartup(context)) }

    internal fun grantMissing(
        arcfox: Boolean,
        canGrant: Boolean,
        isGranted: (String) -> Boolean,
        grant: (String) -> Unit,
        failed: (String, Throwable) -> Unit = { _, _ -> },
    ): String {
        if (!arcfox) return "not_arcfox"
        val missing = permissions.filterNot(isGranted)
        if (missing.isEmpty()) return "already_granted"
        if (!canGrant) return "system_grant_permission_missing"
        for (permission in missing) {
            runCatching { grant(permission) }.onFailure { failed(permission, it) }
        }
        val remaining = permissions.filterNot(isGranted)
        return if (remaining.isEmpty()) "system_granted" else "remaining=${remaining.joinToString()}"
    }
}
