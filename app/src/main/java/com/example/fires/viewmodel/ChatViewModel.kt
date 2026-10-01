package com.example.fires.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.fires.data.model.Incident
import com.example.fires.data.model.Message
import com.example.fires.data.model.User
import com.example.fires.data.repository.AuthRepository
import com.example.fires.data.repository.IncidentRepository
import com.example.fires.data.repository.MessageRepository
import com.example.fires.data.repository.UserRepository
import com.example.fires.util.ChatItem
import com.example.fires.util.ChatRules
import com.example.fires.util.attempt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** What the chat screen shows (D8). The text being typed is separate: see [ChatViewModel.draft]. */
data class ChatUiState(
    val items: List<ChatItem> = emptyList(),
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val sendError: String? = null,
    /**
     * The report this thread belongs to, shown at the top so the person knows which fire they are
     * talking about. Null until it loads, or if it cannot be read: the chat works without it.
     */
    val incident: Incident? = null
)

/**
 * The chat thread of one report (incidents/{id}/messages), live. Shared by citizens and responders:
 * the sender's name and role come from users/{uid}, so a responder's replies are marked as such.
 *
 * Sending never waits for the server. The message is saved on the phone and shows up in the list at
 * once as "Sending…", then flips to a time when the server confirms. If the server refuses it, the
 * message disappears from the list, the banner says so, and the text goes back into the box.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val incidentId: String,
    private val auth: AuthRepository = AuthRepository(),
    private val users: UserRepository = UserRepository(),
    private val messages: MessageRepository = MessageRepository(),
    incidents: IncidentRepository = IncidentRepository()
) : ViewModel() {

    private sealed interface Feed {
        data object Loading : Feed
        data object Failed : Feed
        data class Loaded(val list: List<Message>) : Feed
    }

    private val restarts = MutableStateFlow(0)
    private val _draft = MutableStateFlow("")
    private val _sendError = MutableStateFlow<String?>(null)

    /** Kept apart from [state] on purpose: the text box must update instantly while typing. */
    val draft: StateFlow<String> = _draft.asStateFlow()

    private val feed = restarts.flatMapLatest {
        messages.observe(incidentId)
            .map<List<Message>, Feed> { Feed.Loaded(it) }
            .onStart { emit(Feed.Loading) }
            .catch { e ->
                Log.w(TAG, "Chat listener stopped", e)
                emit(Feed.Failed)
            }
    }

    // An extra, like the photo on the detail screen: if it fails, the messages still show.
    private val incident: Flow<Incident?> = restarts.flatMapLatest {
        incidents.observe(incidentId)
            .catch { e ->
                Log.w(TAG, "Incident listener stopped", e)
                emit(null)
            }
            .onStart { emit(null) }
    }

    val state: StateFlow<ChatUiState> = combine(feed, _sendError, incident) { current, sendError, report ->
        when (current) {
            Feed.Loading -> ChatUiState(sendError = sendError, incident = report)
            Feed.Failed -> ChatUiState(
                isLoading = false, loadError = LOAD_ERROR, sendError = sendError, incident = report
            )
            is Feed.Loaded -> {
                val now = System.currentTimeMillis()
                val me = auth.currentUid
                ChatUiState(
                    items = current.list.map { it.toItem(me, now) },
                    isLoading = false,
                    sendError = sendError,
                    incident = report
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    /** Name and role of the person typing. Loaded on the first send, then reused. */
    private var sender: User? = null

    fun onDraftChange(text: String) {
        _draft.value = ChatRules.limit(text)
        if (_sendError.value != null) _sendError.value = null
    }

    fun retry() {
        restarts.update { it + 1 }
    }

    fun dismissSendError() {
        _sendError.value = null
    }

    fun send() {
        val text = ChatRules.clean(_draft.value)
        if (!ChatRules.canSend(text)) return
        // Clear the box right away so a double tap cannot send the same text twice.
        _draft.value = ""
        _sendError.value = null

        viewModelScope.launch {
            val uid = auth.currentUid
            if (uid == null) {
                failSend(text, SIGNED_OUT)
                return@launch
            }
            val profile = sender ?: attempt { withTimeout(PROFILE_TIMEOUT_MS) { users.getUser(uid) } }
                .getOrNull()
                ?.also { sender = it }
            if (profile == null) {
                // Without the profile we cannot label the message as citizen or responder. Do not guess.
                failSend(text, SEND_ERROR)
                return@launch
            }

            messages.send(
                incidentId,
                Message(
                    senderId = uid,
                    senderName = profile.fullName,
                    senderRole = profile.role,
                    messageText = text
                )
            ).addOnFailureListener { e ->
                Log.w(TAG, "Message was refused", e)
                failSend(text, SEND_ERROR)
            }
        }
    }

    /** Shows the error and puts the text back, unless the person has already started typing something else. */
    private fun failSend(text: String, message: String) {
        if (_draft.value.isEmpty()) _draft.value = text
        _sendError.value = message
    }

    companion object {
        private const val TAG = "ChatViewModel"
        private const val PROFILE_TIMEOUT_MS = 5_000L
        const val LOAD_ERROR = "We couldn't load the messages. Check your connection and try again."
        const val SEND_ERROR = "Your message wasn't sent. Check your connection and try again."
        const val SIGNED_OUT = "Your session has ended. Please log in again."

        fun factory(incidentId: String) = viewModelFactory {
            initializer { ChatViewModel(incidentId) }
        }
    }
}

private fun Message.toItem(myUid: String?, nowMillis: Long): ChatItem = ChatRules.item(
    id = id,
    senderId = senderId,
    senderName = senderName,
    senderRole = senderRole,
    text = messageText,
    sentAtMillis = sentAt?.toDate()?.time,
    myUid = myUid,
    nowMillis = nowMillis
)
