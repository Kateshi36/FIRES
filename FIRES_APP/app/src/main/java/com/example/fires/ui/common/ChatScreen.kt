package com.example.fires.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.example.fires.util.ProfileRules
import com.example.fires.util.incidentCode
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
 * Chat for one report (D8, round 2 design). Shared by citizens and responders. The list is live: a
 * reply from the other side appears without refreshing.
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
    val incident = state.incident
    // The other side: "Responders" until one of them writes (reporter's view), the reporter for a responder.
    val otherName = ChatRules.otherParty(
        state.items,
        if (state.isReporter || incident == null) ChatRules.RESPONDERS_NAME
        else incident.reporterName.ifBlank { ChatRules.REPORTER_FALLBACK_NAME }
    )
    val sub = incident?.let {
        incidentCode(it.id) + if (it.addressText.isNotBlank()) " \u00B7 " + it.addressText else ""
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // imePadding lifts the message box above the keyboard.
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding()) {

            RoundHeader(
                title = otherName,
                sub = sub,
                onBack = onBack,
                trailing = { Avatar(ProfileRules.initials(otherName)) }
            )

            ToneBanner(
                text = if (state.isReporter) "Only you and the responders on this report can read this chat."
                else "Only the responders and the reporter of this report can read this chat.",
                tone = Tone.BLUE,
                icon = Icons.Filled.Lock,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

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

            // One-tap texts for the person who reported the fire. They go into the box, so nothing
            // is sent by a slip of the thumb.
            if (state.isReporter) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChatRules.QUICK_REPLIES.forEach { text ->
                        QuickReplyChip(text = text, onClick = { onDraftChange(text) })
                    }
                }
            }

            MessageInput(draft = draft, onDraftChange = onDraftChange, onSend = onSend)
        }
    }
}

@Composable
private fun Avatar(initials: String) {
    Box(
        modifier = Modifier.size(44.dp).background(Blue.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initials,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
            color = Blue
        )
    }
}

@Composable
private fun QuickReplyChip(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun MessageList(items: List<ChatItem>) {
    val listState = rememberLazyListState()
    val now = System.currentTimeMillis()
    // Keep the newest message in view, including when a reply arrives while the screen is open.
    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.lastIndex)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
            // A centred day label before the first message of each day.
            val label = item.sentAtMillis?.let { ChatRules.dayLabel(it, now) }
            val previous = items.getOrNull(index - 1)?.sentAtMillis?.let { ChatRules.dayLabel(it, now) }
            Column {
                if (label != null && label != previous) {
                    Text(
                        label,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                MessageBubble(item)
            }
        }
    }
}

@Composable
private fun MessageBubble(item: ChatItem) {
    val mine = item.isMine
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start
    ) {
        if (!mine && item.fromResponder) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 3.dp)) {
                Text(
                    text = item.senderName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                ChipPill("Responder", Blue)
            }
        }
        Surface(
            modifier = Modifier.widthIn(max = 300.dp),
            shape = if (mine) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
            // Mine: the dark bubble of the design. Theirs: a white one.
            color = if (mine) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface,
            contentColor = if (mine) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
            shadowElevation = if (mine) 0.dp else 1.dp
        ) {
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
            )
        }
        Text(
            // The clock time once the server has the message; "Sending..." until then.
            text = item.sentAtMillis?.let { ChatRules.clockLabel(it) } ?: item.timeLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

@Composable
private fun MessageInput(draft: String, onDraftChange: (String) -> Unit, onSend: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Write a message") },
            shape = RoundedCornerShape(18.dp),
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
            modifier = Modifier.size(56.dp)
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
