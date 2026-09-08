package dev.injun.scalelite.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.injun.scalelite.data.db.EventEntity
import dev.injun.scalelite.data.health.HealthConnectStatus
import dev.injun.scalelite.ui.home.formatTime
import dev.injun.scalelite.ui.openAppSettings
import dev.injun.scalelite.ui.openBatteryOptimizationSettings
import dev.injun.scalelite.ui.rememberBluetoothPermission
import dev.injun.scalelite.ui.rememberNotificationsEnabled
import dev.injun.scalelite.ui.rememberOnResume

@Composable
fun DiagnosticsRoute(viewModel: DiagnosticsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resumed = rememberOnResume { true }
    LaunchedEffect(resumed) { viewModel.refresh() }
    DiagnosticsScreen(
        state = state,
        bluetoothGranted = rememberBluetoothPermission(),
        notificationsEnabled = rememberNotificationsEnabled(),
        onRetryHealthConnect = viewModel::retryHealthConnect,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    state: DiagnosticsUiState,
    bluetoothGranted: Boolean,
    notificationsEnabled: Boolean,
    onRetryHealthConnect: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatusRow("Bluetooth permission", bluetoothGranted)
                    StatusRow("Notifications", notificationsEnabled)
                    StatusRow(
                        "Health Connect",
                        state.healthConnect == HealthConnectStatus.AVAILABLE && state.healthConnectGranted,
                        detail = when (state.healthConnect) {
                            HealthConnectStatus.AVAILABLE -> if (state.healthConnectGranted) "weight write allowed" else "permission missing"
                            HealthConnectStatus.UPDATE_REQUIRED -> "update required"
                            HealthConnectStatus.NOT_INSTALLED -> "not installed"
                        },
                    )
                    StatusRow("Background recording", state.backgroundEnabled)
                    StatusRow(
                        "Battery optimization off",
                        state.backgroundStartAllowed,
                        detail = if (state.backgroundStartAllowed) null else "needed for background wake",
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = onRetryHealthConnect, modifier = Modifier.fillMaxWidth()) { Text("Retry pending Health Connect writes") }
                    state.lastSyncResult?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    OutlinedButton(onClick = { openBatteryOptimizationSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Battery optimization settings")
                    }
                    OutlinedButton(onClick = { openAppSettings(context) }, modifier = Modifier.fillMaxWidth()) { Text("App settings") }
                }
            }
            item {
                Text(
                    "RECENT EVENTS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (state.events.isEmpty()) {
                item { Text("Nothing yet.", style = MaterialTheme.typography.bodySmall) }
            }
            items(state.events, key = { it.id }) { event ->
                EventRow(event)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean, detail: String? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            (if (ok) "OK" else "Missing") + (detail?.let { "  ($it)" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun EventRow(event: EventEntity) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(
            "${formatTime(event.epochMillis)}  ${event.kind.name}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(event.message, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}
