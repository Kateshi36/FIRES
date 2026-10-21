package com.example.fires.ui.citizen

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fires.data.Puroks
import com.example.fires.data.model.User
import com.example.fires.ui.auth.ErrorBanner
import com.example.fires.ui.common.LabeledDropdown
import com.example.fires.ui.common.LabeledTextField
import com.example.fires.ui.common.PrimaryButton
import com.example.fires.ui.common.RoundHeader
import com.example.fires.ui.common.SecondaryButton
import com.example.fires.ui.common.SectionLabel
import com.example.fires.ui.common.SoftCard
import com.example.fires.ui.common.Tone
import com.example.fires.ui.common.TonePill
import com.example.fires.ui.theme.FIRESTheme
import com.example.fires.ui.theme.FireRedDark
import com.example.fires.ui.theme.FireRedSoft
import com.example.fires.ui.theme.ThemeMode
import com.example.fires.ui.theme.ThemeSettings
import com.example.fires.util.NotificationPrefs
import com.example.fires.util.ProfileRules
import com.example.fires.viewmodel.ProfileForm
import com.example.fires.viewmodel.ProfileUiState
import com.example.fires.viewmodel.ProfileViewModel

/**
 * Profile and settings (round 2 design): the person's details with an Edit button, the
 * Light / Dark / System choice, two notification switches and Sign out.
 */
@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    bottomBar: @Composable () -> Unit,
    roleLabel: String = "Citizen",
    showNotifications: Boolean = true,
    signOutText: String = "You will need to log in again to report a fire.",
    extra: @Composable () -> Unit = {},
    viewModel: ProfileViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ProfileContent(
        state = state,
        themeMode = ThemeSettings.mode,
        onThemeMode = { ThemeSettings.set(context, it) },
        statusOn = readPref(context) { NotificationPrefs.statusEnabled(it) },
        repliesOn = readPref(context) { NotificationPrefs.repliesEnabled(it) },
        onStatusOn = { NotificationPrefs.setStatusEnabled(context, it) },
        onRepliesOn = { NotificationPrefs.setRepliesEnabled(context, it) },
        onEdit = viewModel::openEdit,
        onCloseEdit = viewModel::closeEdit,
        onForm = viewModel::onForm,
        onSave = viewModel::save,
        onRetry = viewModel::retry,
        onSignOut = onSignOut,
        bottomBar = bottomBar,
        roleLabel = roleLabel,
        showNotifications = showNotifications,
        signOutText = signOutText,
        extra = extra
    )
}

/** A saved switch value that follows taps at once (the stored value is only read when first drawn). */
@Composable
private fun readPref(context: Context, read: (Context) -> Boolean): Boolean = remember { read(context) }

/** The look of the screen. No ViewModel here, so it can be previewed. */
@Composable
fun ProfileContent(
    state: ProfileUiState,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    statusOn: Boolean,
    repliesOn: Boolean,
    onStatusOn: (Boolean) -> Unit,
    onRepliesOn: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onCloseEdit: () -> Unit,
    onForm: ((ProfileForm) -> ProfileForm) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    roleLabel: String = "Citizen",
    showNotifications: Boolean = true,
    signOutText: String = "You will need to log in again to report a fire.",
    extra: @Composable () -> Unit = {}
) {
    var confirmSignOut by remember { mutableStateOf(false) }
    // The switches keep their own state so a tap shows at once.
    var status by remember(statusOn) { mutableStateOf(statusOn) }
    var replies by remember(repliesOn) { mutableStateOf(repliesOn) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .verticalScroll(rememberScrollState())
            ) {
                RoundHeader(title = "Profile")

                when {
                    state.isLoading -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    state.error != null -> Column(Modifier.padding(20.dp)) {
                        ErrorBanner(state.error)
                        Spacer(Modifier.height(12.dp))
                        SecondaryButton(text = "Try again", onClick = onRetry)
                    }
                    else -> {
                        val user = state.user ?: User()
                        Identity(user, roleLabel)
                        Spacer(Modifier.height(20.dp))
                        DetailsCard(user, onEdit)
                        Spacer(Modifier.height(14.dp))
                        AppearanceCard(themeMode, onThemeMode)
                        if (showNotifications) {
                            Spacer(Modifier.height(14.dp))
                            NotificationsCard(
                                status = status, replies = replies,
                                onStatus = { status = it; onStatusOn(it) },
                                onReplies = { replies = it; onRepliesOn(it) }
                            )
                        }
                        extra()
                    }
                }

                OutlinedButton(
                    onClick = { confirmSignOut = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).height(54.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Sign out", style = MaterialTheme.typography.labelLarge, color = FireRedDark)
                }
                Text(
                    "F.I.R.E.S. · Barangay Bagumbayan",
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            bottomBar()
        }
    }

    state.form?.let { form ->
        EditDialog(form, state, onForm, onSave, onCloseEdit)
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text(signOutText) },
            confirmButton = {
                TextButton(onClick = { confirmSignOut = false; onSignOut() }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun Identity(user: User, roleLabel: String) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(72.dp).background(FireRedSoft, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                ProfileRules.initials(user.fullName),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = FireRedDark
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(user.fullName.ifBlank { "Your name" }, style = MaterialTheme.typography.titleLarge)
            Text(
                user.email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TonePill(roleLabel, Tone.GREY)
        }
    }
}

@Composable
private fun DetailsCard(user: User, onEdit: () -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionLabel("MY DETAILS", modifier = Modifier.weight(1f))
                TextButton(onClick = onEdit) {
                    Text("Edit", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            DetailRow("Contact", ProfileRules.valueOrDash(user.contactNo))
            DetailRow("Address", ProfileRules.valueOrDash(user.address))
            DetailRow("Purok", ProfileRules.valueOrDash(user.purok))
            DetailRow("Emergency contact", ProfileRules.valueOrDash(user.emergencyContact))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AppearanceCard(mode: ThemeMode, onMode: (ThemeMode) -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            SectionLabel("APPEARANCE")
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ModeButton("Light", Icons.Filled.LightMode, mode == ThemeMode.LIGHT, Modifier.weight(1f)) { onMode(ThemeMode.LIGHT) }
                ModeButton("Dark", Icons.Filled.DarkMode, mode == ThemeMode.DARK, Modifier.weight(1f)) { onMode(ThemeMode.DARK) }
                ModeButton("System", Icons.Filled.PhoneAndroid, mode == ThemeMode.SYSTEM, Modifier.weight(1f)) { onMode(ThemeMode.SYSTEM) }
            }
        }
    }
}

@Composable
private fun ModeButton(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent,
        shadowElevation = if (selected) 1.dp else 0.dp
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NotificationsCard(
    status: Boolean, replies: Boolean, onStatus: (Boolean) -> Unit, onReplies: (Boolean) -> Unit
) {
    SoftCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
            SectionLabel("NOTIFICATIONS", modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
            SwitchRow("Report updates", "Verified, dispatched, on scene", status, onStatus)
            SwitchRow("Replies from responders", "Pops up so you do not miss it", replies, onReplies)
        }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun EditDialog(
    form: ProfileForm,
    state: ProfileUiState,
    onForm: ((ProfileForm) -> ProfileForm) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!state.isSaving) onDismiss() },
        title = { Text("Edit my details") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                state.saveError?.let { ErrorBanner(it) }
                LabeledTextField(
                    label = "Full name", value = form.name,
                    onValueChange = { v -> onForm { it.copy(name = v) } },
                    error = state.errors.name, enabled = !state.isSaving
                )
                LabeledTextField(
                    label = "Contact number", value = form.contactNo,
                    onValueChange = { v -> onForm { it.copy(contactNo = v) } },
                    error = state.errors.contactNo, keyboardType = KeyboardType.Phone, enabled = !state.isSaving
                )
                LabeledTextField(
                    label = "Address", value = form.address,
                    onValueChange = { v -> onForm { it.copy(address = v) } },
                    error = state.errors.address, enabled = !state.isSaving
                )
                LabeledDropdown(
                    label = "Purok (optional)", value = form.purok, options = Puroks.ALL,
                    onOptionSelected = { v -> onForm { it.copy(purok = v) } },
                    placeholder = "Select your purok", noSelectionLabel = "Not specified",
                    enabled = !state.isSaving
                )
                LabeledTextField(
                    label = "Emergency contact", value = form.emergencyContact,
                    onValueChange = { v -> onForm { it.copy(emergencyContact = v) } },
                    error = state.errors.emergencyContact, keyboardType = KeyboardType.Phone,
                    enabled = !state.isSaving
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !state.isSaving) {
                Text(if (state.isSaving) "Saving..." else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.isSaving) { Text("Cancel") } }
    )
}

// ---------- Previews ----------

@Preview(name = "Profile", showSystemUi = true)
@Composable
private fun ProfilePreview() {
    FIRESTheme {
        ProfileContent(
            state = ProfileUiState(
                user = User(
                    fullName = "Maria Santos", email = "maria@email.com", contactNo = "09171234567",
                    address = "12 Rizal St.", purok = "Purok 3", emergencyContact = "09181234567"
                ),
                isLoading = false
            ),
            themeMode = ThemeMode.LIGHT, onThemeMode = {}, statusOn = true, repliesOn = true,
            onStatusOn = {}, onRepliesOn = {}, onEdit = {}, onCloseEdit = {}, onForm = {}, onSave = {},
            onRetry = {}, onSignOut = {}
        )
    }
}
