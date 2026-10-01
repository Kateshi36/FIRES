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

    /** The menu item is only useful while the app can still be put to sleep. */
    fun showMenuItem(isIgnoringOptimizations: Boolean): Boolean = !isIgnoringOptimizations
}
