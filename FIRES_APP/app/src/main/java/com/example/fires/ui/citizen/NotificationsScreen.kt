package com.example.fires.ui.citizen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fires.ui.auth.ErrorBanner
import com.example.fires.ui.common.RoundHeader
import com.example.fires.ui.common.SecondaryButton
import com.example.fires.ui.common.SoftCard
import com.example.fires.util.CitizenUpdate
import com.example.fires.util.CitizenUpdates
import com.example.fires.util.timeAgoLabel
import com.example.fires.viewmodel.MyReportsUiState
import com.example.fires.viewmodel.MyReportsViewModel

/**
 * Notifications (the bell on Home): the latest update on each of my reports. Tap one to open its
 * status screen. It reads the same live list as "My reports", so nothing new is stored.
 */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    viewModel: MyReportsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NotificationsContent(state = state, onBack = onBack, onOpen = onOpen, onRetry = viewModel::retry)
}

@Composable
fun NotificationsContent(
    state: MyReportsUiState,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onRetry: () -> Unit
) {
    val updates = CitizenUpdates.from(state.reports)
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            RoundHeader(title = "Notifications", sub = "Updates on your reports", onBack = onBack)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                    state.error != null -> Column(modifier = Modifier.padding(20.dp)) {
                        ErrorBanner(state.error)
                        Spacer(Modifier.height(12.dp))
                        SecondaryButton(text = "Try again", onClick = onRetry)
                    }

                    updates.isEmpty() -> Text(
                        text = CitizenUpdates.EMPTY,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp)
                    )

                    else -> {
                        val now = System.currentTimeMillis()
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(updates, key = { it.incidentId }) { update ->
                                UpdateCard(update, now, onOpen)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateCard(update: CitizenUpdate, now: Long, onOpen: (String) -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth().clickable { onOpen(update.incidentId) }) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = update.code + if (update.whenMillis > 0) " · " + timeAgoLabel(update.whenMillis, now) else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(update.headline, style = MaterialTheme.typography.titleMedium)
            Text(
                update.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
