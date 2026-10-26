package com.example.fires.ui.citizen

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

enum class CitizenTab { HOME, REPORTS, CHAT, PROFILE }

/**
 * The four-tab bar of the citizen app (round 2): Home, My reports, Chat, Profile.
 * Chat opens the conversation of the newest report that is still open.
 */
@Composable
fun CitizenBottomBar(
    selected: CitizenTab,
    onHome: () -> Unit,
    onReports: () -> Unit,
    onChat: () -> Unit,
    onProfile: () -> Unit,
    /** False when the citizen has no open report: the Chat tab is then not shown at all. */
    showChat: Boolean = true
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        NavigationBarItem(
            selected = selected == CitizenTab.HOME, onClick = onHome,
            icon = { Icon(Icons.Filled.Home, contentDescription = null) }, label = { Text("Home") }
        )
        NavigationBarItem(
            selected = selected == CitizenTab.REPORTS, onClick = onReports,
            icon = { Icon(Icons.Filled.Description, contentDescription = null) }, label = { Text("My reports") }
        )
        if (showChat) {
            NavigationBarItem(
                selected = selected == CitizenTab.CHAT, onClick = onChat,
                icon = { Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null) }, label = { Text("Chat") }
            )
        }
        NavigationBarItem(
            selected = selected == CitizenTab.PROFILE, onClick = onProfile,
            icon = { Icon(Icons.Filled.Person, contentDescription = null) }, label = { Text("Profile") }
        )
    }
}
