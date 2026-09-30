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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.example.fires.viewmodel.SessionState
import com.example.fires.viewmodel.SignUpUiState
import com.example.fires.viewmodel.SignUpViewModel

/**
 * Sign-up screen (C5). Creates a citizen account, then continues to profile setup.
 *
 * @param onNavigate called once with the route to open after a successful sign-up.
 * @param onLoginClick called when the person taps the Log in link.
 */
@Composable
fun SignUpScreen(
    onNavigate: (String) -> Unit,
    onLoginClick: () -> Unit,
    viewModel: SignUpViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The effect runs for the whole life of the screen, so it must always call the newest lambda.
    val currentOnNavigate by rememberUpdatedState(onNavigate)

    // One-time signal from the ViewModel: "account and profile document saved, go here".
    LaunchedEffect(viewModel) {
        viewModel.destinations.collect { destination ->
            destination.toRoute()?.let { currentOnNavigate(it) }
        }
    }

    SignUpContent(
        state = state,
        onNameChange = viewModel::onNameChange,
        onEmailChange = viewModel::onEmailChange,
        onContactChange = viewModel::onContactChange,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
        onSignUpClick = viewModel::onSignUpClick,
        onLoginClick = onLoginClick
    )
}

/**
 * The look of the sign-up screen. No ViewModel here, so it can be previewed.
 *
 * Same structure as the login screen: header, fields with the error text under each one, a banner
 * for problems that belong to the whole form, then the solid red button. The content is centred
 * when it fits and scrolls when it does not (small phones, landscape, on-screen keyboard).
 */
@Composable
fun SignUpContent(
    state: SignUpUiState,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onContactChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSignUpClick: () -> Unit,
    onLoginClick: () -> Unit
) {
    // Fields are locked while sending, and after the account exists (the retry must save exactly
    // what was submitted; the ViewModel ignores edits in that state as well).
    val fieldsEnabled = !state.isLoading && !state.accountCreated

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
            // Minimum height = available space, so short content sits in the middle and tall
            // content (five fields plus a banner) scrolls.
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 400.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AuthHeader(subtitle = "Create your account")
                    Spacer(Modifier.height(28.dp))

                    LabeledTextField(
                        label = "Full name",
                        value = state.name,
                        onValueChange = onNameChange,
                        placeholder = "Juan Dela Cruz",
                        error = state.errors.name,
                        enabled = fieldsEnabled
                    )
                    Spacer(Modifier.height(12.dp))
                    // The duplicate-email message from Firebase lands in errors.email, so it shows
                    // under this field exactly like a blank or badly formatted email does.
                    LabeledTextField(
                        label = "Email",
                        value = state.email,
                        onValueChange = onEmailChange,
                        placeholder = "name@example.com",
                        error = state.errors.email,
                        keyboardType = KeyboardType.Email,
                        enabled = fieldsEnabled
                    )
                    Spacer(Modifier.height(12.dp))
                    LabeledTextField(
                        label = "Contact number",
                        value = state.contactNo,
                        onValueChange = onContactChange,
                        placeholder = "09171234567",
                        error = state.errors.contactNo,
                        keyboardType = KeyboardType.Phone,
                        enabled = fieldsEnabled
                    )
                    Spacer(Modifier.height(12.dp))
                    LabeledTextField(
                        label = "Password",
                        value = state.password,
                        onValueChange = onPasswordChange,
                        placeholder = "At least ${Validators.MIN_PASSWORD_LENGTH} characters",
                        error = state.errors.password,
                        isPassword = true,
                        enabled = fieldsEnabled
                    )
                    Spacer(Modifier.height(12.dp))
                    LabeledTextField(
                        label = "Confirm password",
                        value = state.confirmPassword,
                        onValueChange = onConfirmPasswordChange,
                        error = state.errors.confirmPassword,
                        isPassword = true,
                        enabled = fieldsEnabled
                    )
                    Spacer(Modifier.height(16.dp))

                    // No connection, account saved but details not, etc. Sits just above the button.
                    state.generalError?.let { message ->
                        ErrorBanner(message)
                        Spacer(Modifier.height(16.dp))
                    }

                    // After the account exists the button only retries saving the details.
                    PrimaryButton(
                        text = if (state.accountCreated) "Try again" else "Create account",
                        onClick = onSignUpClick,
                        loading = state.isLoading
                    )
                    Spacer(Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Already have an account?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = onLoginClick,
                            enabled = !state.isLoading
                        ) {
                            Text("Log in", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

// ---------- Previews ----------

private val noOp: () -> Unit = {}
private val noOpText: (String) -> Unit = {}

@Composable
private fun PreviewContent(state: SignUpUiState) {
    FIRESTheme {
        SignUpContent(
            state = state,
            onNameChange = noOpText,
            onEmailChange = noOpText,
            onContactChange = noOpText,
            onPasswordChange = noOpText,
            onConfirmPasswordChange = noOpText,
            onSignUpClick = noOp,
            onLoginClick = noOp
        )
    }
}

private val filledState = SignUpUiState(
    name = "Juan Dela Cruz",
    email = "juan@example.com",
    contactNo = "09171234567",
    password = "secret-password",
    confirmPassword = "secret-password"
)

@Preview(name = "Sign up - empty", showSystemUi = true)
@Composable
private fun SignUpEmptyPreview() = PreviewContent(SignUpUiState())

@Preview(name = "Sign up - blank field errors", showSystemUi = true)
@Composable
private fun SignUpFieldErrorsPreview() = PreviewContent(
    SignUpUiState(
        errors = Validators.validateSignUp(
            name = "", email = "", contactNo = "", password = "", confirmPassword = ""
        )
    )
)

@Preview(name = "Sign up - email already in use", showSystemUi = true)
@Composable
private fun SignUpDuplicateEmailPreview() = PreviewContent(
    filledState.copy(
        errors = Validators.SignUpErrors(email = AuthErrors.MSG_EMAIL_TAKEN)
    )
)

@Preview(name = "Sign up - loading", showSystemUi = true)
@Composable
private fun SignUpLoadingPreview() = PreviewContent(filledState.copy(isLoading = true))

@Preview(name = "Sign up - saving details failed", showSystemUi = true)
@Composable
private fun SignUpRetryPreview() = PreviewContent(
    filledState.copy(
        accountCreated = true,
        generalError = AuthErrors.MSG_PROFILE_SAVE
    )
)
