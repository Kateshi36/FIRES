package com.example.fires.util

/**
 * What the permission screen should show right now. Checked in order: location permission
 * first, then the phone's location (GPS) switch. [Ready] means both are fine.
 */
sealed interface LocationGate {
    /** Permission not granted and we have not asked yet (or the person has not answered). */
    data object AskPermission : LocationGate

    /**
     * The person said no. [canAskAgain] is false once Android will no longer show the request
     * dialog ("Don't allow" twice); then the only way forward is the app's Settings page.
     */
    data class PermissionDenied(val canAskAgain: Boolean) : LocationGate

    /** Permission is granted but the phone's location switch is off. */
    data object GpsOff : LocationGate

    data object Ready : LocationGate
}

/**
 * The single rule behind the permission screen. It takes plain true/false facts (no Android
 * classes), so it can be unit-tested.
 *
 * @param hasPermission location permission is currently granted
 * @param gpsEnabled the phone's location switch is on
 * @param wasDenied the person answered "deny" to our request during this visit
 * @param canAskAgain Android would still show the request dialog (only meaningful after a denial)
 */
fun resolveLocationGate(
    hasPermission: Boolean,
    gpsEnabled: Boolean,
    wasDenied: Boolean,
    canAskAgain: Boolean
): LocationGate = when {
    !hasPermission && !wasDenied -> LocationGate.AskPermission
    !hasPermission -> LocationGate.PermissionDenied(canAskAgain)
    !gpsEnabled -> LocationGate.GpsOff
    else -> LocationGate.Ready
}
