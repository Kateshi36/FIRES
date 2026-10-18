package com.example.fires.ui.navigation

import com.example.fires.viewmodel.SessionState

/**
 * The single place that turns a SessionState into a screen. Splash, login and sign-up all use
 * it, so a role can never be routed differently depending on how the person got in.
 * Returns null for states that mean "stay on this screen" (loading, logged out, error).
 */
fun SessionState.toRoute(): String? = when (this) {
    SessionState.NeedsProfile -> Routes.PROFILE_SETUP
    SessionState.Citizen -> Routes.CITIZEN_GRAPH
    SessionState.Responder -> Routes.RESPONDER_GRAPH
    SessionState.Loading, SessionState.LoggedOut, is SessionState.Error -> null
}
