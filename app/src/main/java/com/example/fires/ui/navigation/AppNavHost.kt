package com.example.fires.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.fires.data.repository.AuthRepository
import com.example.fires.ui.auth.LoginScreen
import com.example.fires.ui.auth.PermissionScreen
import com.example.fires.ui.auth.ProfileSetupScreen
import com.example.fires.ui.auth.SignUpScreen
import com.example.fires.ui.auth.SplashScreen
import com.example.fires.ui.common.MapDemoScreen
import com.example.fires.ui.common.PlaceholderAction
import com.example.fires.ui.common.PlaceholderScreen

private val incidentIdArg = listOf(navArgument(Routes.ARG_INCIDENT_ID) { type = NavType.StringType })

/**
 * The whole app map. Splash -> (permission -> login) OR straight into the citizen / responder area.
 * Screens marked "placeholder" are swapped for real ones in later phases; the routes stay the same.
 */
@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {

    fun go(route: String) = navController.navigate(route)
    fun back() = navController.popBackStack()

    // Temporary sign-out (C7). Ends the Firebase session, then clears the whole back stack and
    // opens Login, so Back closes the app instead of returning to a signed-in screen.
    val authRepo = remember { AuthRepository() }
    fun signOut() {
        authRepo.signOut()
        navController.navigate(Routes.LOGIN) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
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
            PlaceholderScreen(
                title = "Chat",
                note = "Placeholder for incident $id. Built in Phase D and E.",
                actions = listOf(PlaceholderAction("Back") { back() })
            )
        }

        // ---------- Citizen area ----------
        navigation(startDestination = Routes.CITIZEN_HOME, route = Routes.CITIZEN_GRAPH) {
            composable(Routes.CITIZEN_HOME) {
                MapDemoScreen(
                    title = "Citizen home",
                    note = "Placeholder + map test: drag the red pin or tap the map to move it.",
                    showPin = true,
                    actions = listOf(
                        PlaceholderAction("Report a fire") { go(Routes.REPORT) },
                        PlaceholderAction("My reports") { go(Routes.MY_REPORTS) },
                        PlaceholderAction("Status tracker (demo)") { go(Routes.status("demo")) },
                        PlaceholderAction("Sign out (temporary)") { signOut() }
                    )
                )
            }
            composable(Routes.REPORT) {
                PlaceholderScreen(
                    title = "Report a fire",
                    note = "Placeholder. Phase D: the report form.",
                    actions = listOf(
                        PlaceholderAction("Send (demo)") { go(Routes.alertSent("demo")) },
                        PlaceholderAction("Back") { back() }
                    )
                )
            }
            composable(Routes.ALERT_SENT, arguments = incidentIdArg) { entry ->
                val id = entry.arguments?.getString(Routes.ARG_INCIDENT_ID).orEmpty()
                PlaceholderScreen(
                    title = "Alert sent",
                    note = "Placeholder for incident $id.",
                    actions = listOf(
                        PlaceholderAction("View status") { go(Routes.status(id)) },
                        PlaceholderAction("Back") { back() }
                    )
                )
            }
            composable(Routes.STATUS, arguments = incidentIdArg) { entry ->
                val id = entry.arguments?.getString(Routes.ARG_INCIDENT_ID).orEmpty()
                PlaceholderScreen(
                    title = "Report status",
                    note = "Placeholder for incident $id. Phase D: live status stepper.",
                    actions = listOf(
                        PlaceholderAction("Open chat") { go(Routes.chat(id)) },
                        PlaceholderAction("Back") { back() }
                    )
                )
            }
            composable(Routes.MY_REPORTS) {
                PlaceholderScreen(
                    title = "My reports",
                    note = "Placeholder. Phase D: list of my reports.",
                    actions = listOf(PlaceholderAction("Back") { back() })
                )
            }
        }

        // ---------- Responder area ----------
        navigation(startDestination = Routes.RESPONDER_HOME, route = Routes.RESPONDER_GRAPH) {
            composable(Routes.RESPONDER_HOME) {
                MapDemoScreen(
                    title = "Responder dashboard",
                    note = "Placeholder + map test: demo markers colored by severity. Tap one.",
                    showPin = false,
                    actions = listOf(
                        PlaceholderAction("Incident detail (demo)") { go(Routes.incidentDetail("demo")) },
                        PlaceholderAction("History") { go(Routes.HISTORY) },
                        PlaceholderAction("Sign out (temporary)") { signOut() }
                    )
                )
            }
            composable(Routes.INCIDENT_DETAIL, arguments = incidentIdArg) { entry ->
                val id = entry.arguments?.getString(Routes.ARG_INCIDENT_ID).orEmpty()
                PlaceholderScreen(
                    title = "Incident detail",
                    note = "Placeholder for incident $id. Phase E: verify, status, assign, resolve.",
                    actions = listOf(
                        PlaceholderAction("Open chat") { go(Routes.chat(id)) },
                        PlaceholderAction("Back") { back() }
                    )
                )
            }
            composable(Routes.HISTORY) {
                PlaceholderScreen(
                    title = "Incident history",
                    note = "Placeholder. Phase E: resolved incidents.",
                    actions = listOf(PlaceholderAction("Back") { back() })
                )
            }
        }
    }
}
