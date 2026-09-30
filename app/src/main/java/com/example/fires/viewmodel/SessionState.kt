package com.example.fires.viewmodel

import com.example.fires.data.model.Role
import com.example.fires.data.model.User
import com.example.fires.data.model.hasCompleteProfile
import com.example.fires.data.model.roleEnum

/** Where should this person go when the app opens or after they log in? */
sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data object NeedsProfile : SessionState   // signed in (citizen) but profile not filled in
    data object Citizen : SessionState
    data object Responder : SessionState      // responders, and admins who open the app
    data class Error(val message: String) : SessionState
}

/**
 * The one rule for "where does this person go?", used by the splash screen (app start) and by
 * login. Role decides the area. Only citizens are sent to profile setup: responder and admin
 * accounts are created by staff, so their profile is never required. A missing users/{uid}
 * document (null) also goes to profile setup.
 */
fun User?.toSessionState(): SessionState = when {
    this == null -> SessionState.NeedsProfile
    roleEnum() == Role.CITIZEN ->
        if (hasCompleteProfile()) SessionState.Citizen else SessionState.NeedsProfile
    else -> SessionState.Responder
}
