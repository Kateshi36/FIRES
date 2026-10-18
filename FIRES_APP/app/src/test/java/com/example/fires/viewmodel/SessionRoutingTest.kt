package com.example.fires.viewmodel

import com.example.fires.data.model.User
import com.example.fires.ui.navigation.Routes
import com.example.fires.ui.navigation.toRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** C6: who goes where after login or app start. Pure logic, no Android or Firebase needed. */
class SessionRoutingTest {

    private val completeCitizen = User(fullName = "Juan Dela Cruz", address = "Purok 3", role = "citizen")

    // ---------- Role -> session state ----------

    @Test fun citizen_withCompleteProfile_goesToCitizenArea() =
        assertEquals(SessionState.Citizen, completeCitizen.toSessionState())

    @Test fun citizen_withoutAddress_goesToProfileSetup() =
        assertEquals(SessionState.NeedsProfile, completeCitizen.copy(address = "").toSessionState())

    @Test fun citizen_withoutName_goesToProfileSetup() =
        assertEquals(SessionState.NeedsProfile, completeCitizen.copy(fullName = " ").toSessionState())

    @Test fun missingUserDocument_goesToProfileSetup() =
        assertEquals(SessionState.NeedsProfile, (null as User?).toSessionState())

    @Test fun responder_goesToResponderArea_evenWithEmptyProfile() =
        assertEquals(SessionState.Responder, User(role = "responder").toSessionState())

    @Test fun admin_goesToResponderArea() =
        assertEquals(SessionState.Responder, User(role = "admin").toSessionState())

    @Test fun unknownRole_isTreatedAsCitizen() =
        assertEquals(SessionState.Citizen, completeCitizen.copy(role = "superuser").toSessionState())

    // ---------- Session state -> route ----------

    @Test fun routes_matchTheAreas() {
        assertEquals(Routes.CITIZEN_GRAPH, SessionState.Citizen.toRoute())
        assertEquals(Routes.RESPONDER_GRAPH, SessionState.Responder.toRoute())
        assertEquals(Routes.PROFILE_SETUP, SessionState.NeedsProfile.toRoute())
    }

    @Test fun statesThatStayOnScreen_haveNoRoute() {
        assertNull(SessionState.Loading.toRoute())
        assertNull(SessionState.LoggedOut.toRoute())
        assertNull(SessionState.Error("x").toRoute())
    }
}
