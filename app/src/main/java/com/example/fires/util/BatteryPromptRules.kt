package com.example.fires.util

/**
 * When to talk about battery optimization (F1.13). Pure rules, so they are unit tested on the JVM.
 */
object BatteryPromptRules {

    /**
     * Ask on the FIRST dashboard visit only. Never when the app is already allowed, and never
     * again once we have asked (the menu item is the way to ask again).
     */
    fun shouldAskOnFirstVisit(isIgnoringOptimizations: Boolean, alreadyAsked: Boolean): Boolean =
        !isIgnoringOptimizations && !alreadyAsked

    /**
     * Ask ONCE, at the first "Start response", while battery optimization is still on (H5e). Uses
     * its own "already asked" flag, so having answered the dashboard question earlier does not
     * stop this one: location sharing is a different job from alerts, and Android can put it to
     * sleep too.
     */
    fun shouldAskAtFirstStartResponse(isIgnoringOptimizations: Boolean, alreadyAskedForResponse: Boolean): Boolean =
        !isIgnoringOptimizations && !alreadyAskedForResponse

    /** The menu item is only useful while the app can still be put to sleep. */
    fun showMenuItem(isIgnoringOptimizations: Boolean): Boolean = !isIgnoringOptimizations

    // ---------- Dialog wording ----------

    /** The dashboard question (F1.13): alerts. This is the dialog's default wording. */
    const val ALERTS_TITLE = "Keep alerts on time"
    const val ALERTS_MESSAGE =
        "To save battery, Android can put this app to sleep, and then fire alerts may " +
            "arrive late. Allow F.I.R.E.S. to keep running in the background so " +
            "alerts reach you right away."

    /** The first "Start response" question (H5e): location sharing. */
    const val LOCATION_TITLE = "Keep location sharing running"
    const val LOCATION_MESSAGE =
        "To save battery, Android can put this app to sleep, and then your location may stop " +
            "reaching the person who reported the fire. Allow F.I.R.E.S. to keep running in " +
            "the background so sharing keeps going while you respond."
}
