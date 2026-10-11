package com.shilapi.xcertplay.vehicle

import android.os.Build
import android.content.Context
import com.shilapi.xcertplay.host.R

/** Default CarPlay return-to-car artwork. User-selected artwork takes precedence in the host. */
internal object VehicleBranding {
    fun carHomeIcon(context: Context? = null, fingerprint: String = Build.FINGERPRINT): Int =
        if (VehicleProfiles.isArcfox(context, fingerprint)) R.raw.ic_car_home_arcfox else R.raw.ic_car_home

    fun legacyIconPreview(context: Context? = null, fingerprint: String = Build.FINGERPRINT): Int =
        if (VehicleProfiles.isArcfox(context, fingerprint)) R.raw.ic_car_home_arcfox else R.raw.placeholder_icon
}
