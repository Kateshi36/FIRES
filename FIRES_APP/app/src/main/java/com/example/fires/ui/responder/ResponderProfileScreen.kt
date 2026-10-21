package com.example.fires.ui.responder

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.fires.ui.citizen.ProfileScreen
import com.example.fires.ui.common.SectionLabel
import com.example.fires.ui.common.SoftCard
import com.example.fires.util.BatteryOptimization

/**
 * The responder's Profile tab: the same screen as the citizen's (details, appearance, sign out) plus a
 * "Keep alerts reliable" switch for the battery setting that used to hide in the dashboard menu.
 */
@Composable
fun ResponderProfileScreen(onSignOut: () -> Unit, bottomBar: @Composable () -> Unit) {
    val context = LocalContext.current
    // Whether Android still may put the app to sleep. Checked again every time the screen comes
    // back, because the responder changes it in a system dialog or in Settings, outside the app.
    var ignored by remember { mutableStateOf(BatteryOptimization.isIgnoring(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { ignored = BatteryOptimization.isIgnoring(context) }

    ProfileScreen(
        onSignOut = onSignOut,
        bottomBar = bottomBar,
        roleLabel = "Authorized responder",
        showNotifications = false,
        signOutText = "You will need to log in again to see incidents.",
        extra = {
            Spacer(Modifier.height(14.dp))
            SoftCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    SectionLabel("ALERTS")
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Keep alerts reliable", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (ignored) "Android will not put the app to sleep."
                                else "Stops Android from putting the app to sleep, so new reports still reach you.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = ignored,
                            onCheckedChange = { on ->
                                // Android only lets the person change this in its own dialog.
                                if (on && !ignored) {
                                    if (!BatteryOptimization.requestAllow(context)) {
                                        Toast.makeText(context, "Could not open the battery settings.", Toast.LENGTH_LONG).show()
                                    }
                                } else if (!on && ignored) {
                                    Toast.makeText(
                                        context,
                                        "To turn this off, open Android Settings > Apps > F.I.R.E.S. > Battery.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    )
}
