package com.example.fires.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fires.ui.common.LabeledTextField
import com.example.fires.ui.common.PrimaryButton
import com.example.fires.ui.navigation.toRoute
import com.example.fires.ui.theme.FIRESTheme
import com.example.fires.util.AuthErrors
import com.example.fires.util.Validators
import com.example.fires.viewmodel.LoginUiState
import com.example.fires.viewmodel.LoginViewModel
import com.example.fires.viewmodel.SessionState

/**
 * Login screen (C4). One login for everyone: after signing in, the role stored in users/{uid}
 * decides where the person goes (citizen area, responder area, or profile setup).
 *
 * @param onNavigate called once with the route to open after a successful login.
 * @param onSignUpClick called when the person taps the Sign up link.
 */
@Composable
fun LoginScreen(
    onNavigate: (String) -> Unit,
    onSignUpClick: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The effect below runs for the whole life of the screen, so it must always call the
    // newest lambda rather than the one from the first composition.
    val currentOnNavigate by rememberUpdatedState(onNavigate)

    // One-time signal from the ViewModel: "login worked, go here".
    LaunchedEffect(viewModel) {
        viewModel.destinations.collect { destination ->
            destination.toRoute()?.let { currentOnNavigate(it) }
        }
    }

    LoginContent(
        state = state,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onLoginClick = viewModel::onLoginClick,
        onSignUpClick = onSignUpClick
    )
}

/**
 * The look of the login screen. No ViewModel here, so it can be previewed.
 *
 * Layout follows the design sheet's login: flame icon, title, subtitle, fields, solid red button.
 * The content is centred when it fits and scrolls when it does not (small phones, landscape,
 * on-screen keyboard).
 */
@Composable
fun LoginContent(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    onSignUpClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
        ) {
            // A scrolling Column cannot centre its content on its own, so it is given a
            // minimum height equal to the space available. Short content then sits in the
            // middle; tall content simply scrolls.
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Keeps the form a sensible width on tablets and in landscape.
                Column(
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AuthHeader(subtitle = "Log in to your account")
                    Spacer(Modifier.height(28.dp))

                    LabeledTextField(
                        label = "Email",
                        value = state.email,
                        onValueChange = onEmailChange,
                        placeholder = "name@example.com",
                        error = state.errors.email,
                        keyboardType = KeyboardType.Email,
                        enabled = !state.isLoading
                    )
                    Spacer(Modifier.height(12.dp))
                    // isPassword adds the show/hide eye icon inside LabeledTextField.
                    LabeledTextField(
                        label = "Password",
                        value = state.password,
                        onValueChange = onPasswordChange,
                        error = state.errors.password,
                        isPassword = true,
                        enabled = !state.isLoading
                    )
                    Spacer(Modifier.height(16.dp))

                    // Wrong credentials, no connection, etc. Sits just above the button.
                    state.generalError?.let { message ->
                        ErrorBanner(message)
                        Spacer(Modifier.height(16.dp))
                    }

                    // PrimaryButton shows a spinner and ignores taps while loading is true.
                    PrimaryButton(
                        text = "Log in",
                        onClick = onLoginClick,
                        loading = state.isLoading
                    )
                    Spacer(Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Don't have an account?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = onSignUpClick,
                            enabled = !state.isLoading
                        ) {
                            Text("Sign up", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

/** Flame icon, app name and a one-line subtitle. Shared by the login and sign-up screens. */
@Composable
internal fun AuthHeader(subtitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Filled.LocalFireDepartment,
            contentDescription = null, // decorative, the title below says the same thing
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "F.I.R.E.S.",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Message box for errors that belong to the whole form, not one field.
 * The live region makes TalkBack read it out when it appears.
 */
@Composable
internal fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

// ---------- Previews ----------

@Preview(name = "Login - empty", showSystemUi = true)
@Composable
private fun LoginEmptyPreview() {
    FIRESTheme {
        LoginContent(LoginUiState(), {}, {}, {}, {})
    }
}

@Preview(name = "Login - blank field errors", showSystemUi = true)
@Composable
private fun LoginFieldErrorsPreview() {
    FIRESTheme {
        LoginContent(
            state = LoginUiState(
                errors = Validators.validateLogin(email = "", password = "")
            ),
            onEmailChange = {}, onPasswordChange = {}, onLoginClick = {}, onSignUpClick = {}
        )
    }
}

@Preview(name = "Login - wrong credentials", showSystemUi = true)
@Composable
private fun LoginWrongCredentialsPreview() {
    FIRESTheme {
        LoginContent(
            state = LoginUiState(
                email = "juan@example.com",
                password = "wrong-password",
                generalError = AuthErrors.MSG_BAD_CREDENTIALS
            ),
            onEmailChange = {}, onPasswordChange = {}, onLoginClick = {}, onSignUpClick = {}
        )
    }
}

@Preview(name = "Login - loading", showSystemUi = true)
@Composable
private fun LoginLoadingPreview() {
    FIRESTheme {
        LoginContent(
            state = LoginUiState(
                email = "juan@example.com",
                password = "secret-password",
                isLoading = true
            ),
            onEmailChange = {}, onPasswordChange = {}, onLoginClick = {}, onSignUpClick = {}
        )
    }
}
