package com.example.fires.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.fires.data.repository.AuthRepository
import com.example.fires.service.CitizenAlertWatcher
import com.example.fires.service.ResponderAlertService
import com.example.fires.ui.auth.LoginScreen
import com.example.fires.ui.auth.PermissionScreen
import com.example.fires.ui.auth.ProfileSetupScreen
import com.example.fires.ui.auth.SignUpScreen
import com.example.fires.ui.auth.SplashScreen
import com.example.fires.ui.citizen.AlertSentScreen
import com.example.fires.ui.citizen.HomeScreen
import com.example.fires.ui.citizen.MyReportsScreen
import com.example.fires.ui.citizen.PinAdjustScreen
import com.example.fires.ui.citizen.ReportScreen
import com.example.fires.ui.citizen.StatusScreen
import com.example.fires.viewmodel.ReportViewModel
import com.example.fires.ui.common.ChatScreen
import com.example.fires.ui.responder.AssignScreen
import com.example.fires.ui.responder.DashboardScreen
import com.example.fires.ui.responder.HistoryScreen
import com.example.fires.ui.responder.IncidentDetailScreen
import com.example.fires.ui.responder.ResolveScreen
import com.example.fires.util.AlertTargetRules
import com.example.fires.util.CitizenScreen
import com.example.fires.util.CitizenTargetRules
import com.example.fires.util.CitizenViewing
import com.example.fires.util.PendingCitizenTarget
import com.example.fires.util.ViewedScreen
import com.example.fires.util.Viewing
import com.example.fires.util.PendingAlertTarget
import com.example.fires.util.TargetAction
import com.example.fires.util.TargetPlace

private val incidentIdArg = listOf(navArgument(Routes.ARG_INCIDENT_ID) { type = NavType.StringType })

/**
 * The whole app map. Splash -> (permission -> login) OR straight into the citizen / responder area.
 * Screens marked "placeholder" are swapped for real ones in later phases; the routes stay the same.
 */
@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {

    fun go(route: String) = navController.navigate(route)
    fun back() = navController.popBackStack()
    // Same as go(), but a quick double tap cannot open the same screen twice.
    fun goOnce(route: String) = navController.navigate(route) { launchSingleTop = true }

    // Temporary sign-out (C7). Ends the Firebase session, then clears the whole back stack and
    // opens Login, so Back closes the app instead of returning to a signed-in screen.
    val authRepo = remember { AuthRepository() }
    val context = LocalContext.current
    fun signOut() {
        // F1.12: stop the alert service FIRST, so no alert for this account can arrive after
        // sign-out (and it does not sit there failing once the session is gone). Stopping a
        // service that is not running, for example for a citizen, does nothing.
        ResponderAlertService.stop(context)
        // F2: the same for a citizen's local notifications.
        CitizenAlertWatcher.stop()
        CitizenViewing.clear()
        authRepo.signOut()
        navController.navigate(Routes.LOGIN) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }

    // ---------- Notification tap (F1.9 + F1.10) ----------
    // MainActivity puts the tapped incident into PendingAlertTarget. It is opened here, and only
    // once the responder area is showing. While Splash is still deciding it just waits, so a tap
    // that launched a closed app is not lost on the way through splash and login routing.
    val currentEntry by navController.currentBackStackEntryAsState()
    val alertTarget by PendingAlertTarget.incidentId.collectAsStateWithLifecycle()

    fun placeOf(entry: NavBackStackEntry?): TargetPlace {
        val route = entry?.destination?.route
        if (entry == null || route == Routes.SPLASH) return TargetPlace.STARTING
        // Chat is shared by both roles, so judge it by the screen it was opened from.
        val owner = if (route == Routes.CHAT) navController.previousBackStackEntry else entry
        val inResponderArea =
            owner?.destination?.hierarchy?.any { it.route == Routes.RESPONDER_GRAPH } == true
        val ownerInCitizenArea =
            owner?.destination?.hierarchy?.any { it.route == Routes.CITIZEN_GRAPH } == true
        return when {
            inResponderArea -> TargetPlace.RESPONDER_AREA
            ownerInCitizenArea -> TargetPlace.CITIZEN_AREA
            else -> TargetPlace.ELSEWHERE
        }
    }

    LaunchedEffect(alertTarget, currentEntry) {
        val id = alertTarget ?: return@LaunchedEffect
        when (AlertTargetRules.decide(placeOf(currentEntry))) {
            TargetAction.WAIT -> Unit
            // A citizen or a signed-out person: ignore the tap, and forget it so it cannot fire
            // later, for example after someone else logs in on this phone.
            TargetAction.DROP -> PendingAlertTarget.clear()
            TargetAction.OPEN -> {
                PendingAlertTarget.clear()
                // Plain navigate on top of whatever the responder is doing: Back returns to it.
                // Skip only when that very incident is already on screen.
                val alreadyThere = currentEntry?.destination?.route == Routes.INCIDENT_DETAIL &&
                    currentEntry?.arguments?.getString(Routes.ARG_INCIDENT_ID) == id
                if (!alreadyThere) navController.navigate(Routes.incidentDetail(id))
            }
        }
    }

    // ---------- Citizen notification tap (F2) ----------
    // Same idea as the responder tap above, for the citizen's status and reply notifications:
    // MainActivity puts the target into PendingCitizenTarget, and it is opened here once the
    // citizen area is showing. Waits while Splash decides; dropped for anyone else.
    val citizenTarget by PendingCitizenTarget.target.collectAsStateWithLifecycle()

    LaunchedEffect(citizenTarget, currentEntry) {
        val target = citizenTarget ?: return@LaunchedEffect
        when (CitizenTargetRules.decide(placeOf(currentEntry))) {
            TargetAction.WAIT -> Unit
            TargetAction.DROP -> PendingCitizenTarget.clear()
            TargetAction.OPEN -> {
                PendingCitizenTarget.clear()
                val id = target.incidentId
                fun isOn(route: String) = currentEntry?.destination?.route == route &&
                    currentEntry?.arguments?.getString(Routes.ARG_INCIDENT_ID) == id
                when (target.screen) {
                    CitizenScreen.STATUS ->
                        if (!isOn(Routes.STATUS)) goOnce(Routes.status(id))
                    CitizenScreen.CHAT -> if (!isOn(Routes.CHAT)) {
                        // Status first, then the chat on top: Back from the chat lands on the report.
                        if (!isOn(Routes.STATUS)) goOnce(Routes.status(id))
                        goOnce(Routes.chat(id))
                    }
                }
            }
        }
    }

    // ---------- Start the citizen watcher (F2) ----------
    // Starts when the citizen area is showing. Called again on every screen change, which is
    // cheap (a running watcher ignores it) and restarts a watcher whose listener had failed.
    val inCitizenArea = placeOf(currentEntry) == TargetPlace.CITIZEN_AREA
    LaunchedEffect(inCitizenArea, currentEntry) {
        if (inCitizenArea) CitizenAlertWatcher.start(context)
    }

    // What the citizen is looking at, so a notification is not shown for the screen that already
    // shows it (the status screen for a status change, the chat for a reply). Only counts while
    // the app is on screen: in the background nothing is being looked at.
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    val appVisible = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    LaunchedEffect(currentEntry, appVisible) {
        val route = currentEntry?.destination?.route
        val id = currentEntry?.arguments?.getString(Routes.ARG_INCIDENT_ID)
        CitizenViewing.set(
            when {
                !appVisible || id == null -> Viewing()
                route == Routes.STATUS -> Viewing(ViewedScreen.STATUS, id)
                route == Routes.CHAT -> Viewing(ViewedScreen.CHAT, id)
                else -> Viewing()
            }
        )
    }
    DisposableEffect(Unit) { onDispose { CitizenViewing.clear() } }

    // ---------- Start the alert service (F1.11) ----------
    // The ONE place that starts it: the moment the responder area opens. That covers both a fresh
    // login (Login navigates into the area) and app start with a saved session (Splash does).
    // Android 12+ only allows starting a foreground service while the app is visible, and it is:
    // this runs because a responder screen was just shown. Leaving and re-entering the area
    // starts it again, which is harmless: a running service ignores a second start.
    val inResponderArea = placeOf(currentEntry) == TargetPlace.RESPONDER_AREA
    LaunchedEffect(inResponderArea) {
        if (inResponderArea) ResponderAlertService.start(context)
    }

    NavHost(navController = navController, startDestination = Routes.SPLASH) {

        // ---------- Before login ----------
        composable(Routes.SPLASH) {
            SplashScreen(onNavigate = { destination ->
                navController.navigate(destination) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            })
        }
        composable(Routes.PERMISSION) {
            PermissionScreen(
                onContinue = {
                    navController.navigate(Routes.LOGIN) {
                        // Remove the permission screen from the back stack. Otherwise Back from
                        // login would return here, and this screen moves on by itself when
                        // permission is already granted, trapping the person in a loop.
                        popUpTo(Routes.PERMISSION) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onNavigate = { destination ->
                    navController.navigate(destination) {
                        // Clear everything behind us (permission, login) so Back from the
                        // app closes it instead of returning to the login screen.
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                onSignUpClick = { go(Routes.SIGNUP) }
            )
        }
        composable(Routes.SIGNUP) {
            SignUpScreen(
                onNavigate = { destination ->
                    navController.navigate(destination) {
                        // Same as login: clear sign-up, login and splash behind us, so Back from
                        // profile setup closes the app instead of returning to a filled-in form.
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                // Sign-up was opened from login, so going back lands on login.
                onLoginClick = { back() }
            )
        }
        composable(Routes.PROFILE_SETUP) {
            ProfileSetupScreen(
                onNavigate = { destination ->
                    navController.navigate(destination) {
                        // Profile is saved: clear profile setup and everything behind it, so Back
                        // from the citizen area closes the app instead of reopening the form.
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
                // Back from here would close the app (the stack was cleared on the way in), so
                // Sign out is the way out for someone on the wrong account.
                onSignOut = { signOut() }
            )
        }

        // ---------- Shared chat ----------
        composable(Routes.CHAT, arguments = incidentIdArg) { entry ->
            val id = entry.arguments?.getString(Routes.ARG_INCIDENT_ID).orEmpty()
            ChatScreen(incidentId = id, onBack = { back() })
        }

        // ---------- Citizen area ----------
        navigation(startDestination = Routes.CITIZEN_HOME, route = Routes.CITIZEN_GRAPH) {
            composable(Routes.CITIZEN_HOME) {
                HomeScreen(
                    onSosClick = { goOnce(Routes.REPORT) },
                    onMyReportsClick = { goOnce(Routes.MY_REPORTS) },
                    onSignOut = { signOut() }
                )
            }
            composable(Routes.REPORT) {
                ReportScreen(
                    onBack = { back() },
                    onAdjustPin = { goOnce(Routes.REPORT_PIN) },
                    onSent = { incidentId ->
                        navController.navigate(Routes.alertSent(incidentId)) {
                            // Remove the report form (and the pin screen above it) from the back
                            // stack. Otherwise Back would reopen the filled-in form, and sending
                            // it again would overwrite the report that was just sent. This also
                            // clears the form's saved state, so the next report starts empty.
                            popUpTo(Routes.REPORT) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Routes.REPORT_PIN) { entry ->
                // The pin screen shares the report form's ViewModel, so Confirm hands the spot back.
                val reportEntry = remember(entry) { navController.getBackStackEntry(Routes.REPORT) }
                val reportViewModel: ReportViewModel = viewModel(reportEntry)
                PinAdjustScreen(onDone = { back() }, viewModel = reportViewModel)
            }
            composable(Routes.ALERT_SENT, arguments = incidentIdArg) { entry ->
                val id = entry.arguments?.getString(Routes.ARG_INCIDENT_ID).orEmpty()
                AlertSentScreen(
                    incidentId = id,
                    onBack = { back() },
                    onViewStatus = { goOnce(Routes.status(id)) }
                )
            }
            composable(Routes.STATUS, arguments = incidentIdArg) { entry ->
                val id = entry.arguments?.getString(Routes.ARG_INCIDENT_ID).orEmpty()
                StatusScreen(
                    incidentId = id,
                    onBack = { back() },
                    onOpenChat = { goOnce(Routes.chat(id)) }
                )
            }
            composable(Routes.MY_REPORTS) {
                MyReportsScreen(
                    onBack = { back() },
                    onOpen = { incidentId -> goOnce(Routes.status(incidentId)) }
                )
            }
        }

        // ---------- Responder area ----------
        navigation(startDestination = Routes.RESPONDER_HOME, route = Routes.RESPONDER_GRAPH) {
            composable(Routes.RESPONDER_HOME) {
                DashboardScreen(
                    onOpenIncident = { incidentId -> goOnce(Routes.incidentDetail(incidentId)) },
                    onHistory = { goOnce(Routes.HISTORY) },
                    onSignOut = { signOut() }
                )
            }
            composable(Routes.INCIDENT_DETAIL, arguments = incidentIdArg) { entry ->
                val id = entry.arguments?.getString(Routes.ARG_INCIDENT_ID).orEmpty()
                IncidentDetailScreen(
                    incidentId = id,
                    onBack = { back() },
                    onOpenChat = { goOnce(Routes.chat(id)) },
                    onAssign = { goOnce(Routes.assign(id)) },
                    onResolve = { goOnce(Routes.resolve(id)) },
                    // Plain go(), not goOnce(): from a detail screen to another detail screen,
                    // singleTop would REPLACE the current one, and Back would skip it.
                    onOpenIncident = { other -> go(Routes.incidentDetail(other)) }
                )
            }
            composable(Routes.ASSIGN, arguments = incidentIdArg) { entry ->
                val id = entry.arguments?.getString(Routes.ARG_INCIDENT_ID).orEmpty()
                AssignScreen(incidentId = id, onBack = { back() }, onDone = { back() })
            }
            composable(Routes.RESOLVE, arguments = incidentIdArg) { entry ->
                val id = entry.arguments?.getString(Routes.ARG_INCIDENT_ID).orEmpty()
                // Back lands on the detail screen, which now shows the report as resolved.
                ResolveScreen(incidentId = id, onBack = { back() }, onDone = { back() })
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    onBack = { back() },
                    // From history to a detail screen. Back returns to history.
                    onOpenIncident = { incidentId -> goOnce(Routes.incidentDetail(incidentId)) }
                )
            }
        }
    }
}
