package com.example.fires.ui.navigation

/** Every screen address in the app. Change a route here and the whole app follows. */
object Routes {
    const val ARG_INCIDENT_ID = "incidentId"

    // Before login
    const val SPLASH = "splash"
    const val PERMISSION = "permission"
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val PROFILE_SETUP = "profile_setup"

    // Shared by both roles
    const val CHAT = "chat/{$ARG_INCIDENT_ID}"
    fun chat(incidentId: String) = "chat/$incidentId"

    // Citizen area
    const val CITIZEN_GRAPH = "citizen_graph"
    const val CITIZEN_HOME = "citizen_home"
    const val REPORT = "report"
    const val REPORT_PIN = "report_pin"
    const val ALERT_SENT = "alert_sent/{$ARG_INCIDENT_ID}"
    fun alertSent(incidentId: String) = "alert_sent/$incidentId"
    const val STATUS = "status/{$ARG_INCIDENT_ID}"
    fun status(incidentId: String) = "status/$incidentId"
    const val MY_REPORTS = "my_reports"

    // Responder area
    const val RESPONDER_GRAPH = "responder_graph"
    const val RESPONDER_HOME = "responder_home"
    const val INCIDENT_DETAIL = "incident_detail/{$ARG_INCIDENT_ID}"
    fun incidentDetail(incidentId: String) = "incident_detail/$incidentId"
    const val ASSIGN = "assign/{$ARG_INCIDENT_ID}"
    fun assign(incidentId: String) = "assign/$incidentId"
    const val RESOLVE = "resolve/{$ARG_INCIDENT_ID}"
    fun resolve(incidentId: String) = "resolve/$incidentId"
    const val HISTORY = "history"
}
