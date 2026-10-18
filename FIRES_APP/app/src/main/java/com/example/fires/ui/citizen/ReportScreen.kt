package com.example.fires.ui.citizen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fires.data.model.FireSize
import com.example.fires.data.model.FireType
import com.example.fires.data.model.HazardType
import com.example.fires.data.model.IncidentPhoto
import com.example.fires.data.model.LocationSource
import com.example.fires.data.model.VulnerableGroup
import com.example.fires.ui.auth.ErrorBanner
import com.example.fires.ui.common.ChipGrid
import com.example.fires.ui.common.CountStepper
import com.example.fires.ui.common.LabeledDropdown
import com.example.fires.ui.common.LabeledTextField
import com.example.fires.ui.common.LatLon
import com.example.fires.ui.common.LocationAccess
import com.example.fires.ui.common.LocationBanner
import com.example.fires.ui.common.PrimaryButton
import com.example.fires.ui.common.RadioChoiceRow
import com.example.fires.ui.common.SecondaryButton
import com.example.fires.ui.common.rememberLocationAccess
import com.example.fires.ui.theme.FIRESTheme
import com.example.fires.util.LocationGate
import com.example.fires.util.PhotoProcessor
import com.example.fires.util.PhotoRules
import com.example.fires.util.ReportValidators
import com.example.fires.viewmodel.ReportEvent
import com.example.fires.viewmodel.ReportUiState
import com.example.fires.viewmodel.ReportViewModel
import java.util.Locale

/**
 * Report form (D2): fire type, description, size, people at risk, vulnerable persons, trapped,
 * hazards and the location (GPS, with a pin screen to adjust it). Required fields are checked
 * when the person taps Send.
 *
 * @param onAdjustPin opens the pin screen (it shares this screen's ViewModel).
 * @param onSent the report was saved (or queued while offline). Gets the new incident id.
 */
@Composable
fun ReportScreen(
    onBack: () -> Unit,
    onAdjustPin: () -> Unit,
    onSent: (incidentId: String) -> Unit,
    viewModel: ReportViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val location = rememberLocationAccess()
    val currentOnSent by rememberUpdatedState(onSent)

    // Capture the GPS on opening (and when it becomes available). It never replaces a spot
    // the person already chose; only the "Use my GPS location" button does that.
    val gpsReady = location.gate is LocationGate.Ready
    LaunchedEffect(gpsReady) {
        if (gpsReady) viewModel.useGps(context, force = false)
    }

    // The system photo picker: no permission needed, shows only images.
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onPhotoPicked(context, uri)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ReportEvent.Sent -> currentOnSent(event.incidentId)
            }
        }
    }

    ReportContent(
        state = state,
        location = location,
        onBack = onBack,
        onFireTypeChange = viewModel::onFireTypeChange,
        onDescriptionChange = viewModel::onDescriptionChange,
        onFireSizeChange = viewModel::onFireSizeChange,
        onPeopleChange = viewModel::onPeopleChange,
        onVulnerableToggle = viewModel::onVulnerableToggle,
        onTrappedChange = viewModel::onTrappedChange,
        onHazardToggle = viewModel::onHazardToggle,
        onAddressChange = viewModel::onAddressChange,
        onAddPhoto = {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onRemovePhoto = viewModel::onPhotoRemove,
        onUseGps = { viewModel.useGps(context, force = true) },
        onAdjustPin = onAdjustPin,
        onSendClick = viewModel::onSendClick
    )
}

/** The look of the report form. No ViewModel here, so it can be previewed. */
@Composable
fun ReportContent(
    state: ReportUiState,
    location: LocationAccess,
    onBack: () -> Unit,
    onFireTypeChange: (FireType) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onFireSizeChange: (FireSize) -> Unit,
    onPeopleChange: (Int) -> Unit,
    onVulnerableToggle: (VulnerableGroup) -> Unit,
    onTrappedChange: (Boolean) -> Unit,
    onHazardToggle: (HazardType) -> Unit,
    onAddressChange: (String) -> Unit,
    onAddPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onUseGps: () -> Unit,
    onAdjustPin: () -> Unit,
    onSendClick: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding()) {

            // ---- Top bar ----
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 16.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Report a fire", style = MaterialTheme.typography.headlineSmall)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                // ---- What is happening ----
                LabeledDropdown(
                    label = "Type of fire",
                    value = state.fireType?.label.orEmpty(),
                    options = FireType.entries.map { it.label },
                    onOptionSelected = { chosen ->
                        FireType.entries.firstOrNull { it.label == chosen }?.let(onFireTypeChange)
                    },
                    placeholder = "Choose type",
                    error = state.errors.fireType
                )
                Spacer(Modifier.height(16.dp))

                LabeledTextField(
                    label = "Description",
                    value = state.description,
                    onValueChange = onDescriptionChange,
                    placeholder = "What's burning, how big, anyone trapped?",
                    error = state.errors.description,
                    singleLine = false,
                    minLines = 3
                )
                Spacer(Modifier.height(20.dp))

                // ---- Fire size ----
                SectionTitle("How big is the fire?")
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FireSize.entries.forEach { size ->
                        RadioChoiceRow(
                            text = size.label,
                            selected = state.fireSize == size,
                            onClick = { onFireSizeChange(size) }
                        )
                    }
                }
                state.errors.fireSize?.let { FieldError(it) }
                Spacer(Modifier.height(20.dp))

                // ---- People ----
                SectionTitle("How many people are in danger?")
                Text(
                    "Not sure? Give your best estimate.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CountStepper(
                    value = state.peopleAtRisk,
                    onValueChange = onPeopleChange,
                    min = 0,
                    max = ReportValidators.MAX_PEOPLE
                )
                Spacer(Modifier.height(12.dp))

                SectionTitle("Vulnerable persons inside (if any)")
                Spacer(Modifier.height(8.dp))
                ChipGrid(
                    items = VulnerableGroup.entries.toList(),
                    isSelected = { it in state.vulnerable },
                    label = { it.label },
                    onToggle = onVulnerableToggle
                )
                Spacer(Modifier.height(16.dp))

                // ---- Trapped + hazards ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = state.trapped, role = Role.Switch, onValueChange = onTrappedChange)
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        SectionTitle("Someone is trapped")
                        Text(
                            "Tell responders if anyone cannot get out.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = state.trapped, onCheckedChange = null)
                }
                Spacer(Modifier.height(12.dp))

                SectionTitle("Hazards nearby (if any)")
                Spacer(Modifier.height(8.dp))
                ChipGrid(
                    items = HazardType.entries.toList(),
                    isSelected = { it in state.hazards },
                    label = { it.label },
                    onToggle = onHazardToggle
                )
                Spacer(Modifier.height(24.dp))

                // ---- Photo (optional) ----
                PhotoSection(state = state, onAddPhoto = onAddPhoto, onRemovePhoto = onRemovePhoto)
                Spacer(Modifier.height(24.dp))

                // ---- Location ----
                LocationSection(
                    state = state,
                    location = location,
                    onUseGps = onUseGps,
                    onAdjustPin = onAdjustPin,
                    onAddressChange = onAddressChange
                )
                Spacer(Modifier.height(24.dp))

                // ---- Send ----
                if (state.showFormError) {
                    ErrorBanner("Some details are missing. Check the fields marked in red.")
                    Spacer(Modifier.height(12.dp))
                }
                state.submitError?.let {
                    ErrorBanner(it)
                    Spacer(Modifier.height(12.dp))
                }
                // After a failed attempt the message above says "tap Send again", so the button does too.
                PrimaryButton(
                    text = if (state.submitError != null) "Send again" else "Send report",
                    onClick = onSendClick,
                    loading = state.isSubmitting
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PhotoSection(
    state: ReportUiState,
    onAddPhoto: () -> Unit,
    onRemovePhoto: () -> Unit
) {
    SectionTitle("Photo (optional)")
    Text(
        "A photo helps responders see what is happening. JPEG, PNG or WebP, up to " +
            "${PhotoRules.MAX_SOURCE_BYTES / (1024 * 1024)} MB. We shrink it to save data.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(8.dp))

    val photo = state.photo
    when {
        state.isProcessingPhoto -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text("Preparing photo…", style = MaterialTheme.typography.bodyLarge)
        }

        photo != null -> {
            PhotoPreview(photo)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${(photo.sizeBytes + 512) / 1024} KB",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
                TextButton(onClick = onAddPhoto) { Text("Change") }
                TextButton(onClick = onRemovePhoto) { Text("Remove") }
            }
        }

        else -> SecondaryButton(text = "Add a photo", onClick = onAddPhoto)
    }
    state.photoError?.let { FieldError(it) }
}

@Composable
private fun PhotoPreview(photo: IncidentPhoto) {
    // Decoded once per photo, not on every redraw.
    val bitmap = remember(photo.base64) { PhotoProcessor.decodePreview(photo.base64) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Photo attached to the report",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Filled.Photo,
                contentDescription = "Photo attached to the report",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

@Composable
private fun LocationSection(
    state: ReportUiState,
    location: LocationAccess,
    onUseGps: () -> Unit,
    onAdjustPin: () -> Unit,
    onAddressChange: (String) -> Unit
) {
    val gpsReady = location.gate is LocationGate.Ready

    SectionTitle("Where is the fire?")
    Spacer(Modifier.height(8.dp))

    if (!gpsReady) {
        LocationBanner(location)
        Spacer(Modifier.height(8.dp))
    }

    val point = state.location
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(
            1.dp,
            if (state.errors.location != null) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (point == null) {
                if (state.isLocating) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Finding your location…", style = MaterialTheme.typography.bodyLarge)
                    }
                } else {
                    Text(
                        "No location yet. Use your GPS or place the pin on the map.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Spacer(Modifier.height(12.dp))
                SecondaryButton(text = "Set location on map", onClick = onAdjustPin)
                if (gpsReady && !state.isLocating) {
                    TextButton(onClick = onUseGps) { Text("Use my GPS location") }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (state.locationSource == LocationSource.GPS) "Using GPS location"
                        else "Pin placed on the map",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = String.format(Locale.US, "%.5f, %.5f", point.latitude, point.longitude),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                SecondaryButton(text = "Adjust pin on map", onClick = onAdjustPin)
                if (gpsReady && state.locationSource == LocationSource.PIN && !state.isLocating) {
                    TextButton(onClick = onUseGps) { Text("Use my GPS location instead") }
                }
            }
        }
    }
    state.errors.location?.let { FieldError(it) }

    if (point != null) {
        Spacer(Modifier.height(16.dp))
        LabeledTextField(
            label = "Address or landmark",
            value = state.addressText,
            onValueChange = onAddressChange,
            placeholder = if (state.isLookingUpAddress) "Looking up address…" else "e.g. beside the barangay hall"
        )
        if (state.addressText.isBlank() && !state.isLookingUpAddress) {
            Text(
                "We couldn't find an address automatically. A landmark helps responders find you.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun FieldError(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
    )
}

// ---------- Previews ----------

private fun access(gate: LocationGate) = LocationAccess(gate, {}, {}, {}, {})

@Composable
private fun PreviewForm(state: ReportUiState, gate: LocationGate = LocationGate.Ready) {
    FIRESTheme {
        ReportContent(
            state = state, location = access(gate),
            onBack = {}, onFireTypeChange = {}, onDescriptionChange = {}, onFireSizeChange = {},
            onPeopleChange = {}, onVulnerableToggle = {}, onTrappedChange = {}, onHazardToggle = {},
            onAddressChange = {}, onAddPhoto = {}, onRemovePhoto = {},
            onUseGps = {}, onAdjustPin = {}, onSendClick = {}
        )
    }
}

@Preview(name = "Report - empty", showSystemUi = true)
@Composable
private fun ReportEmptyPreview() = PreviewForm(ReportUiState(isLocating = true))

@Preview(name = "Report - filled with GPS", showSystemUi = true)
@Composable
private fun ReportFilledPreview() = PreviewForm(
    ReportUiState(
        fireType = FireType.STRUCTURAL,
        description = "House on fire, thick smoke from the roof.",
        fireSize = FireSize.MEDIUM,
        peopleAtRisk = 3,
        vulnerable = setOf(VulnerableGroup.CHILDREN, VulnerableGroup.ELDERLY),
        trapped = true,
        hazards = setOf(HazardType.LPG_TANK),
        location = LatLon(14.5995, 120.9842),
        addressText = "123 Rizal St., Manila"
    )
)

@Preview(name = "Report - missing fields", showSystemUi = true)
@Composable
private fun ReportErrorsPreview() = PreviewForm(
    ReportUiState(
        errors = ReportValidators.validate(null, "", null, hasLocation = false),
        showFormError = true
    ),
    gate = LocationGate.GpsOff
)

@Preview(name = "Report - pin placed, no address", showSystemUi = true)
@Composable
private fun ReportPinPreview() = PreviewForm(
    ReportUiState(
        fireType = FireType.VEHICLE,
        location = LatLon(14.6010, 120.9850),
        locationSource = LocationSource.PIN
    ),
    gate = LocationGate.PermissionDenied(canAskAgain = false)
)

@Preview(name = "Report - photo attached", showSystemUi = true)
@Composable
private fun ReportPhotoPreview() = PreviewForm(
    ReportUiState(
        fireType = FireType.RUBBISH,
        photo = IncidentPhoto(base64 = "", sizeBytes = 148L * 1024),
        location = LatLon(14.5995, 120.9842)
    )
)

@Preview(name = "Report - preparing photo", showSystemUi = true)
@Composable
private fun ReportPhotoBusyPreview() = PreviewForm(ReportUiState(isProcessingPhoto = true))

@Preview(name = "Report - photo refused", showSystemUi = true)
@Composable
private fun ReportPhotoErrorPreview() = PreviewForm(ReportUiState(photoError = PhotoRules.MSG_TOO_LARGE))

@Preview(name = "Report - sending", showSystemUi = true)
@Composable
private fun ReportSendingPreview() = PreviewForm(
    ReportUiState(
        fireType = FireType.STRUCTURAL,
        description = "House on fire, thick smoke from the roof.",
        fireSize = FireSize.LARGE,
        location = LatLon(14.5995, 120.9842),
        isSubmitting = true
    )
)

@Preview(name = "Report - send failed", showSystemUi = true)
@Composable
private fun ReportSendFailedPreview() = PreviewForm(
    ReportUiState(
        fireType = FireType.STRUCTURAL,
        description = "House on fire, thick smoke from the roof.",
        fireSize = FireSize.LARGE,
        location = LatLon(14.5995, 120.9842),
        submitError = "We couldn't send your report. Check your connection and tap Send again."
    )
)
