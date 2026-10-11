package com.shilapi.xcertplay.vehicle

import android.content.Context
import android.os.Build

/** One persisted vehicle choice, shared by setup, display configuration and OEM adapters. */
enum class VehicleProfile(val key: String) {
    DILINK_3("dilink3"), DILINK_4("dilink4"), DILINK_5("dilink5"), ARCFOX_T("arcfox_t"), UNKNOWN("unknown");
    companion object { fun fromKey(key: String?) = entries.firstOrNull { it.key == key } }
}

object VehicleProfiles {
    private const val PREFS = "diplay"
    private const val KEY = "vehicle_profile"
    private const val LEGACY_KEY = "setup_dilink_generation"

    fun confirmed(context: Context): VehicleProfile? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return VehicleProfile.fromKey(prefs.getString(KEY, null) ?: prefs.getString(LEGACY_KEY, null))
            ?.takeUnless { it == VehicleProfile.UNKNOWN }
    }

    fun current(context: Context?, fingerprint: String = Build.FINGERPRINT.orEmpty()): VehicleProfile =
        context?.let(::confirmed) ?: detect(fingerprint)

    fun detect(fingerprint: String): VehicleProfile {
        if (VehicleIdentity.isArcfox(fingerprint)) return VehicleProfile.ARCFOX_T
        val match = Regex("DiLink\\s*([345])", RegexOption.IGNORE_CASE).find(fingerprint)
        return match?.groupValues?.get(1)?.let { VehicleProfile.fromKey("dilink$it") } ?: VehicleProfile.UNKNOWN
    }

    fun isArcfox(context: Context?, fingerprint: String = Build.FINGERPRINT.orEmpty()): Boolean =
        current(context, fingerprint) == VehicleProfile.ARCFOX_T

    /** Unknown resumes automatic detection; existing BYD guide selections remain readable. */
    fun save(context: Context, profile: VehicleProfile) {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (profile == VehicleProfile.UNKNOWN) edit.remove(KEY).remove(LEGACY_KEY)
        else edit.putString(KEY, profile.key).putString(LEGACY_KEY, profile.key)
        edit.apply()
    }
}
