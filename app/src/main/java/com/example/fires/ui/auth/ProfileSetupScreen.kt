package com.example.fires.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
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
import com.example.fires.viewmodel.ProfileSetupUiState
import com.example.fires.viewmodel.ProfileSetupViewModel
import com.example.fires.viewmodel.SessionState

/**
 * Profile setup (C9). A citizen fills in name, address, purok and an emergency contact, then
 * continues to the citizen area.
 *
 * @param onNavigate called once with the route to open after the profile is saved.
 * @param onSignOut called when the person taps Sign out, or when the session has ended.
 *                  The caller ends the Firebase session and opens Login.
 */
@Composable
fun ProfileSetupScreen(
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit,
    viewModel: ProfileSetupViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The effect lives as long as the screen, so it must always call the newest lambdas.
    val currentOnNavigate by rememberUpdatedState(onNavigate)
    val currentOnSignOut by rememberUpdatedState(onSignOut)

    LaunchedEffect(viewModel) {
        viewModel.destinations.collect { destination ->
            if (destination == SessionState.LoggedOut) {
                currentOnSignOut()
            } else {
                destination.toRoute()?.let { currentOnNavigate(it) }
            }
        }
    }

    ProfileSetupContent(
        state = state,
        onNameChange = viewModel::onNameChange,
        onContactChange = viewModel::onContactChange,
        onAddressChange = viewModel::onAddressChange,
        onPurokChange = viewModel::onPurokChange,
        onEmergencyContactChange = viewModel::onEmergencyContactChange,
        onContinueClick = viewModel::onContinueClick,
        onRetryLoad = viewModel::load,
        onSignOut = onSignOut
    )
}

/**
 * The look of the profile setup screen. No ViewModel here, so it can be previewed.
 * Three views: reading the profile (spinner), could not read it (message + Retry), the form.
 */
@Composable
fun ProfileSetupContent(
    state: ProfileSetupUiState,
    onNameChange: (String) -> Unit,
    onContactChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onPurokChange: (String) -> Unit,
    onEmergencyContactChange: (String) -> Unit,
    onContinueClick: () -> Unit,
    onRetryLoad: () -> Unit,
    onSignOut: () -> Unit
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
                    AuthHeader(subtitle = "Set up your profile")
                    Spacer(Modifier.height(28.dp))

                    when {
                        state.isLoadingProfile -> CircularProgressIndicator()

                        state.loadError != null -> {
                            ErrorBanner(state.loadError)
                            Spacer(Modifier.height(16.dp))
                            PrimaryButton(text = "Try again", onClick = onRetryLoad)
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onSignOut) {
                                Text("Sign out", style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        else -> ProfileForm(
                            state = state,
                            onNameChange = onNameChange,
                            onContactChange = onContactChange,
                            onAddressChange = onAddressChange,
                            onPurokChange = onPurokChange,
                            onEmergencyContactChange = onEmergencyContactChange,
                            onContinueClick = onContinueClick,
                            onSignOut = onSignOut
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileForm(
    state: ProfileSetupUiState,
    onNameChange: (String) -> Unit,
    onContactChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onPurokChange: (String) -> Unit,
    onEmergencyContactChange: (String) -> Unit,
    onContinueClick: () -> Unit,
    onSignOut: () -> Unit
) {
    val enabled = !state.isSaving

    Text(
        text = "Responders use these details to find you and reach someone you trust.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(20.dp))

    LabeledTextField(
        label = "Full name",
        value = state.name,
        onValueChange = onNameChange,
        placeholder = "Juan Dela Cruz",
        error = state.errors.name,
        enabled = enabled
    )

    // Only when sign-up never saved a usable number (see ProfileSetupUiState.showContactField).
    if (state.showContactField) {
        Spacer(Modifier.height(12.dp))
        LabeledTextField(
            label = "Contact number",
            value = state.contactNo,
            onValueChange = onContactChange,
            placeholder = "09171234567",
            error = state.errors.contactNo,
            keyboardType = KeyboardType.Phone,
            enabled = enabled
        )
    }

    Spacer(Modifier.height(12.dp))
    LabeledTextField(
        label = "Address",
        value = state.address,
        onValueChange = onAddressChange,
        placeholder = "123 Rizal St.",
        error = state.errors.address,
        enabled = enabled
    )
    Spacer(Modifier.height(12.dp))
    LabeledTextField(
        label = "Purok (optional)",
        value = state.purok,
        onValueChange = onPurokChange,
        placeholder = "Purok 3",
        enabled = enabled
    )
    Spacer(Modifier.height(12.dp))
    LabeledTextField(
        label = "Emergency contact",
        value = state.emergencyContact,
        onValueChange = onEmergencyContactChange,
        placeholder = "09171234567",
        error = state.errors.emergencyContact,
        keyboardType = KeyboardType.Phone,
        enabled = enabled
    )
    Spacer(Modifier.height(16.dp))

    // Saving failed (no connection, etc.). Sits just above the button; typed values stay.
    state.generalError?.let { message ->
        ErrorBanner(message)
        Spacer(Modifier.height(16.dp))
    }

    PrimaryButton(
        text = "Continue to app",
        onClick = onContinueClick,
        loading = state.isSaving
    )
    Spacer(Modifier.height(8.dp))
    // The way out if this is the wrong account. Back would just close the app.
    TextButton(onClick = onSignOut, enabled = enabled) {
        Text("Sign out", style = MaterialTheme.typography.labelLarge)
    }
}

// ---------- Previews ----------

private val noOp: () -> Unit = {}
private val noOpText: (String) -> Unit = {}

@Composable
private fun PreviewContent(state: ProfileSetupUiState) {
    FIRESTheme {
        ProfileSetupContent(
            state = state,
            onNameChange = noOpText,
            onContactChange = noOpText,
            onAddressChange = noOpText,
            onPurokChange = noOpText,
            onEmergencyContactChange = noOpText,
            onContinueClick = noOp,
            onRetryLoad = noOp,
            onSignOut = noOp
        )
    }
}

private val loadedState = ProfileSetupUiState(
    name = "Juan Dela Cruz",
    isLoadingProfile = false
)

@Preview(name = "Profile setup - loading", showSystemUi = true)
@Composable
private fun ProfileLoadingPreview() = PreviewContent(ProfileSetupUiState())

@Preview(name = "Profile setup - empty", showSystemUi = true)
@Composable
private fun ProfileEmptyPreview() = PreviewContent(loadedState)

@Preview(name = "Profile setup - blank field errors", showSystemUi = true)
@Composable
private fun ProfileFieldErrorsPreview() = PreviewContent(
    loadedState.copy(
        name = "",
        errors = Validators.validateProfile(
            name = "", contactNo = "", address = "", emergencyContact = "", requireContact = false
        )
    )
)

@Preview(name = "Profile setup - document was missing", showSystemUi = true)
@Composable
private fun ProfileMissingDocumentPreview() = PreviewContent(
    loadedState.copy(name = "", showContactField = true)
)

@Preview(name = "Profile setup - saving", showSystemUi = true)
@Composable
private fun ProfileSavingPreview() = PreviewContent(
    loadedState.copy(
        address = "123 Rizal St.",
        emergencyContact = "09181234567",
        isSaving = true
    )
)

@Preview(name = "Profile setup - save failed", showSystemUi = true)
@Composable
private fun ProfileSaveFailedPreview() = PreviewContent(
    loadedState.copy(
        address = "123 Rizal St.",
        emergencyContact = "09181234567",
        generalError = AuthErrors.MSG_NETWORK
    )
)

@Preview(name = "Profile setup - could not load", showSystemUi = true)
@Composable
private fun ProfileLoadErrorPreview() = PreviewContent(
    ProfileSetupUiState(isLoadingProfile = false, loadError = AuthErrors.MSG_LOAD_PROFILE)
)
