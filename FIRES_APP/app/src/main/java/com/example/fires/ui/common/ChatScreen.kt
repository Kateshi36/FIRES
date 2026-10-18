package com.example.fires.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fires.data.model.FireType
import com.example.fires.data.model.Incident
import com.example.fires.data.model.IncidentStatus
import com.example.fires.data.model.fireTypeEnum
import com.example.fires.data.model.statusEnum
import com.example.fires.ui.auth.ErrorBanner
import com.example.fires.ui.theme.Blue
import com.example.fires.ui.theme.Border
import com.example.fires.ui.theme.FIRESTheme
import com.example.fires.util.ChatItem
import com.example.fires.util.ChatRules
import com.example.fires.viewmodel.ChatUiState
import com.example.fires.viewmodel.ChatViewModel

/**
 * Chat for one report (D8). Shared by citizens and responders. The list is live: a reply from the
 * other side appears without refreshing.
 */
@Composable
fun ChatScreen(
    incidentId: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = viewModel(factory = ChatViewModel.factory(incidentId))
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    ChatContent(
        state = state,
        draft = draft,
        onDraftChange = viewModel::onDraftChange,
        onSend = viewModel::send,
        onRetry = viewModel::retry,
        onBack = onBack
    )
}

/** The look of the chat. No ViewModel here, so it can be previewed. */
@Composable
fun ChatContent(
    state: ChatUiState,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        // imePadding lifts the message box above the keyboard.
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding()) {

            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 16.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Chat", style = MaterialTheme.typography.headlineSmall)
            }

            // Which fire this thread is about. Hidden until the report has loaded.
            state.incident?.let { IncidentHeader(it) }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                    state.loadError != null -> Column(modifier = Modifier.padding(20.dp)) {
                        ErrorBanner(state.loadError)
                        Spacer(Modifier.height(12.dp))
                        SecondaryButton(text = "Try again", onClick = onRetry)
                    }

                    state.items.isEmpty() -> Text(
                        text = "No messages yet. Send a message to start the conversation.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp)
                    )

                    else -> MessageList(state.items)
                }
            }

            state.sendError?.let {
                ErrorBanner(it, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            }

            MessageInput(draft = draft, onDraftChange = onDraftChange, onSend = onSend)
        }
    }
}

/** One compact card: fire type, address and status, so a reply never goes to the wrong report. */
@Composable
private fun IncidentHeader(incident: Incident) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Border),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(incident.fireTypeEnum().label, style = MaterialTheme.typography.titleSmall)
                if (incident.addressText.isNotBlank()) {
                    Text(
                        text = incident.addressText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusChip(incident.statusEnum())
        }
    }
}

@Composable
private fun MessageList(items: List<ChatItem>) {
    val listState = rememberLazyListState()
    // Keep the newest message in view, including when a reply arrives while the screen is open.
    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.lastIndex)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items, key = { it.id }) { MessageBubble(it) }
    }
}

@Composable
private fun MessageBubble(item: ChatItem) {
    val mine = item.isMine
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
    ) {
        if (!mine) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.senderName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.fromResponder) {
                    Spacer(Modifier.width(6.dp))
                    ChipPill("Responder", Blue)
                }
            }
        }
        Surface(
            modifier = Modifier.widthIn(max = 300.dp).padding(top = 2.dp),
            shape = RoundedCornerShape(16.dp),
            color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        ) {
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        Text(
            text = item.timeLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun MessageInput(draft: String, onDraftChange: (String) -> Unit, onSend: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Type a message") },
            shape = RoundedCornerShape(24.dp),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onSend,
            enabled = ChatRules.canSend(draft),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
        }
    }
}

// ---------- Previews ----------

private val previewItems = listOf(
    ChatItem("1", "I can see smoke from the second floor.", "Ana Reyes", true, false, "4 min ago", false),
    ChatItem("2", "Help is on the way. Are there people inside?", "Capt. Santos", false, true, "3 min ago", false),
    ChatItem("3", "Everyone is outside.", "Ana Reyes", true, false, "Sending…", true)
)

private val previewIncident = Incident(
    id = "demo",
    fireType = FireType.STRUCTURAL.value,
    addressText = "123 Rizal St., Purok 3, Bagumbayan",
    status = IncidentStatus.DISPATCHED.value
)

@Preview(name = "Chat - conversation", showSystemUi = true)
@Composable
private fun ChatConversationPreview() {
    FIRESTheme {
        ChatContent(
            state = ChatUiState(items = previewItems, isLoading = false, incident = previewIncident),
            draft = "", onDraftChange = {}, onSend = {}, onRetry = {}, onBack = {}
        )
    }
}

@Preview(name = "Chat - empty", showSystemUi = true)
@Composable
private fun ChatEmptyPreview() {
    FIRESTheme {
        ChatContent(
            state = ChatUiState(isLoading = false),
            draft = "Hello", onDraftChange = {}, onSend = {}, onRetry = {}, onBack = {}
        )
    }
}

@Preview(name = "Chat - send failed", showSystemUi = true)
@Composable
private fun ChatSendFailedPreview() {
    FIRESTheme {
        ChatContent(
            state = ChatUiState(items = previewItems.take(2), isLoading = false, sendError = ChatViewModel.SEND_ERROR),
            draft = "Everyone is outside.", onDraftChange = {}, onSend = {}, onRetry = {}, onBack = {}
        )
    }
}
