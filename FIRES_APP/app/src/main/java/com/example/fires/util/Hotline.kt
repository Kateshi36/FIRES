package com.example.fires.util

/**
 * The emergency hotline shown when a report has not reached the server (no signal) or was refused.
 * It is the Barangay Bagumbayan Rescue number from the barangay's own hotline posters.
 * The app cannot call by itself; the button only opens the phone's dialer with the number filled in.
 */
object Hotline {
    /** Barangay Rescue, as shown to the person. */
    const val DISPLAY_NUMBER = "0919-894-7219"

    /** The same number without dashes, for the dialer. */
    const val DIAL_NUMBER = "09198947219"

    const val NO_SIGNAL_TEXT =
        "No signal, or still sending? Do not wait for the app. Call Barangay Rescue at $DISPLAY_NUMBER now."

    const val REFUSED_TEXT =
        "The report was not accepted. If the fire is still burning, call Barangay Rescue at $DISPLAY_NUMBER now."

    const val BUTTON = "Call Barangay Rescue"

    /** The dial link, e.g. tel:09198947219. */
    fun dialUri(): String = "tel:$DIAL_NUMBER"
}
