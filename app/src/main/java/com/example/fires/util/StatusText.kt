package com.example.fires.util

import com.example.fires.data.model.IncidentStatus

/** The one-line headline above the stepper on the citizen's Report status screen. */
fun statusHeadline(status: IncidentStatus): String = when (status) {
    IncidentStatus.REPORTED -> "We received your report"
    IncidentStatus.VERIFIED -> "Responders confirmed your report"
    IncidentStatus.DISPATCHED -> "Help is on the way"
    IncidentStatus.ON_SCENE -> "Responders are at the scene"
    IncidentStatus.RESOLVED -> "This fire has been dealt with"
    IncidentStatus.DISMISSED -> "This report was closed"
}

/** A short explanation under the headline. */
fun statusDetail(status: IncidentStatus): String = when (status) {
    IncidentStatus.REPORTED -> "B-FLARE and BDRRMO can see it. A responder will check it shortly."
    IncidentStatus.VERIFIED -> "The report was checked. Responders are preparing to go."
    IncidentStatus.DISPATCHED -> "Responders have been sent to the location you reported."
    IncidentStatus.ON_SCENE -> "Responders have arrived. Follow their instructions and stay clear of the fire."
    IncidentStatus.RESOLVED -> "Thank you for reporting. You can still read the chat for this report."
    // Closed after review (false report or a duplicate of another report). Deliberately does not
    // say which: the citizen should not be accused, and a duplicate is already being handled.
    IncidentStatus.DISMISSED -> "Responders reviewed it and closed it. If a fire is still burning, call 911 right away."
}
