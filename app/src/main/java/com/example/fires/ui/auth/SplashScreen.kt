package com.example.fires.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fires.ui.navigation.Routes
import com.example.fires.ui.navigation.toRoute
import com.example.fires.ui.theme.FIRESTheme
import com.example.fires.util.AuthErrors
import com.example.fires.viewmodel.SessionState
import com.example.fires.viewmodel.SplashViewModel

/** First screen. Decides where to go based on login state and role (role routing). */
@Composable
fun SplashScreen(
    onNavigate: (String) -> Unit,
    viewModel: SplashViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state

    LaunchedEffect(current) {
        // Signed-in states use the shared rule; a logged-out visitor starts at the permission screen.
        val route = if (current == SessionState.LoggedOut) Routes.PERMISSION else current.toRoute()
        route?.let(onNavigate)
    }

    SplashContent(
        state = current,
        onRetry = { viewModel.resolve() },
        onSignOut = { viewModel.signOut() }
    )
}

/** The look of the splash screen. No ViewModel here, so it can be previewed. */
@Composable
fun SplashContent(
    state: SessionState,
    onRetry: () -> Unit,
    onSignOut: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.LocalFireDepartment,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(88.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "F.I.R.E.S.",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Text(
                "Report. Verify. Respond.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f)
            )
            Spacer(Modifier.height(32.dp))

            when (state) {
                is SessionState.Error -> {
                    Text(
                        state.message,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { onRetry() }) {
                        Text("Try again", color = Color.White)
                    }
                    TextButton(onClick = { onSignOut() }) {
                        Text("Log out", color = Color.White)
                    }
                }
                SessionState.Loading -> CircularProgressIndicator(color = Color.White)
                else -> Unit
            }
        }
    }
}

@Preview(name = "Splash - loading", showSystemUi = true)
@Composable
private fun SplashLoadingPreview() {
    FIRESTheme { SplashContent(state = SessionState.Loading, onRetry = {}) }
}

@Preview(name = "Splash - error", showSystemUi = true)
@Composable
private fun SplashErrorPreview() {
    FIRESTheme {
        SplashContent(state = SessionState.Error(AuthErrors.MSG_LOAD_PROFILE), onRetry = {})
    }
}
