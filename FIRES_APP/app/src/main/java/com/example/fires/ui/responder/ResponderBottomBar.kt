package com.example.fires.ui.responder

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

enum class ResponderTab { DASHBOARD, HISTORY, PROFILE }

/** The three-tab bar of the responder app (round 2): Dashboard, History, Profile. */
@Composable
fun ResponderBottomBar(
    selected: ResponderTab,
    onDashboard: () -> Unit,
    onHistory: () -> Unit,
    onProfile: () -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        NavigationBarItem(
            selected = selected == ResponderTab.DASHBOARD, onClick = onDashboard,
            icon = { Icon(Icons.Filled.Dashboard, contentDescription = null) }, label = { Text("Dashboard") }
        )
        NavigationBarItem(
            selected = selected == ResponderTab.HISTORY, onClick = onHistory,
            icon = { Icon(Icons.Filled.History, contentDescription = null) }, label = { Text("History") }
        )
        NavigationBarItem(
            selected = selected == ResponderTab.PROFILE, onClick = onProfile,
            icon = { Icon(Icons.Filled.Person, contentDescription = null) }, label = { Text("Profile") }
        )
    }
}
