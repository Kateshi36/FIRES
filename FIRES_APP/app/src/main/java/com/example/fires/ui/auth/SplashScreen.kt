package com.example.fires.ui.auth

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.delay

/**
 * The splash stays at least this long, even when the session is resolved sooner, so the logo
 * animation can finish and the screen does not flash by. Change this one number to adjust it.
 */
private const val MIN_SPLASH_MS = 1800L

/** First screen. Decides where to go based on login state and role (role routing). */
@Composable
fun SplashScreen(
    onNavigate: (String) -> Unit,
    viewModel: SplashViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state

    // Saved, so rotating the phone does not make the person wait again.
    var minTimeElapsed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(MIN_SPLASH_MS)
        minTimeElapsed = true
    }

    LaunchedEffect(current, minTimeElapsed) {
        if (!minTimeElapsed) return@LaunchedEffect // resolved early: wait for the animation
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

/**
 * The look of the splash screen. No ViewModel here, so it can be previewed.
 *
 * The logo fades and grows in, then the name and tagline fade in, then a small spinner.
 * @param animateIn set to false in previews so they show the finished screen, not the first frame.
 */
@Composable
fun SplashContent(
    state: SessionState,
    onRetry: () -> Unit,
    onSignOut: () -> Unit = {},
    animateIn: Boolean = true
) {
    // Flips to true on the first frame, which starts every animation below. Saved, so a rotation
    // does not replay the intro. With animateIn = false it starts true (no animation).
    var shown by rememberSaveable { mutableStateOf(!animateIn) }
    LaunchedEffect(Unit) { shown = true }

    val logoAlpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "logoAlpha"
    )
    val logoScale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.85f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "logoScale"
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 600, delayMillis = 350, easing = FastOutSlowInEasing),
        label = "textAlpha"
    )
    val spinnerAlpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = 800),
        label = "spinnerAlpha"
    )

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
                modifier = Modifier
                    .size(88.dp)
                    .graphicsLayer {
                        alpha = logoAlpha
                        scaleX = logoScale
                        scaleY = logoScale
                    }
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "F.I.R.E.S.",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                modifier = Modifier.graphicsLayer { alpha = textAlpha }
            )
            Text(
                "Report. Verify. Respond.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.graphicsLayer { alpha = textAlpha }
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
                // Loading, and also a resolved state that is waiting out the minimum splash time,
                // so the spinner never vanishes before the screen changes.
                else -> CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer { alpha = spinnerAlpha }
                )
            }
        }
    }
}

@Preview(name = "Splash - loading", showSystemUi = true)
@Composable
private fun SplashLoadingPreview() {
    FIRESTheme { SplashContent(state = SessionState.Loading, onRetry = {}, animateIn = false) }
}

@Preview(name = "Splash - error", showSystemUi = true)
@Composable
private fun SplashErrorPreview() {
    FIRESTheme {
        SplashContent(
            state = SessionState.Error(AuthErrors.MSG_LOAD_PROFILE),
            onRetry = {},
            animateIn = false
        )
    }
}
