package com.example.fires.util

/** What the permission screen shows now: location first, then notifications, then it is done. */
sealed interface PermissionStep {
    /** [gate] is never [LocationGate.Ready] here; Ready means this step is finished. */
    data class Location(val gate: LocationGate) : PermissionStep

    /** Android 13+ only, and only if not already granted. */
    data object Notifications : PermissionStep

    /** Nothing left to ask. The screen moves on to login. */
    data object Done : PermissionStep
}

/**
 * The rule that decides the step. Plain true/false inputs, so it can be unit-tested.
 *
 * No denial can trap anyone. Every step can be left: location by [locationSkipped], notifications
 * by [notificationsAnswered] (set on ANY answer, yes or no, or "Not now"). So once both are
 * true/false as needed, the result is always [PermissionStep.Done].
 *
 * @param locationGate what [resolveLocationGate] says about permission and the GPS switch
 * @param locationSkipped the person chose to continue without location
 * @param notificationsNeeded this phone asks for it (Android 13+) and it is not granted yet
 * @param notificationsAnswered the person has responded to the notification prompt, either way
 */
fun resolvePermissionStep(
    locationGate: LocationGate,
    locationSkipped: Boolean,
    notificationsNeeded: Boolean,
    notificationsAnswered: Boolean
): PermissionStep = when {
    locationGate != LocationGate.Ready && !locationSkipped -> PermissionStep.Location(locationGate)
    notificationsNeeded && !notificationsAnswered -> PermissionStep.Notifications
    else -> PermissionStep.Done
}
