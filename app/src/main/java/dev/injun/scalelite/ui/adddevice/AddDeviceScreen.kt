package dev.injun.scalelite.ui.adddevice

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.injun.scalelite.ble.NearbyScale
import dev.injun.scalelite.core.protocol.Dialect
import dev.injun.scalelite.ui.rememberBluetoothPermission
import dev.injun.scalelite.ui.rememberBluetoothPermissionRequest

@Composable
fun AddDeviceRoute(viewModel: AddDeviceViewModel, onDone: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val granted = rememberBluetoothPermission()
    val request = rememberBluetoothPermissionRequest { if (it) viewModel.startScanning() }

    LaunchedEffect(granted) { if (granted) viewModel.startScanning() else request() }
    DisposableEffect(Unit) { onDispose { viewModel.stopScanning() } }
    LaunchedEffect(state.step) { if (state.step == AddDeviceStep.Saved) onDone() }

    AddDeviceScreen(
        state = state,
        onPick = viewModel::identify,
        onSave = viewModel::save,
        onRetry = viewModel::retry,
        onBack = onDone,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceScreen(
    state: AddDeviceUiState,
    onPick: (NearbyScale) -> Unit,
    onSave: (NearbyScale, Dialect) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Add scale") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (val step = state.step) {
                AddDeviceStep.Scanning -> {
                    Text(
                        "Step on the scale. It only advertises while someone is standing on it, so it appears here within a few seconds.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.nearby, key = { it.address }) { scale -> NearbyRow(scale) { onPick(scale) } }
                    }
                }
                is AddDeviceStep.Identifying -> {
                    Text("Connecting to ${step.scale.label()} to read what it speaks", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                is AddDeviceStep.Identified -> {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(step.scale.label(), style = MaterialTheme.typography.titleMedium)
                            Text(step.scale.address, style = MaterialTheme.typography.bodySmall)
                            Text("Protocol: ${step.dialect.displayName}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Button(onClick = { onSave(step.scale, step.dialect) }, modifier = Modifier.fillMaxWidth()) { Text("Use this scale") }
                    OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Pick another") }
                }
                is AddDeviceStep.Unsupported -> {
                    Text("${step.scale.label()} is not a supported scale.", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "It exposes services ${step.services.joinToString()} but not the layout Scale Lite understands.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Scan again") }
                }
                is AddDeviceStep.Error -> {
                    Text(step.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Scan again") }
                }
                AddDeviceStep.Saved -> Unit
            }
        }
    }
}

@Composable
private fun NearbyRow(scale: NearbyScale, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(scale.label(), style = MaterialTheme.typography.bodyLarge)
                Text(
                    scale.address + if (scale.advertisesScaleService) "  ·  scale service" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("${scale.rssi} dBm", style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun NearbyScale.label(): String = name ?: "Unnamed device"
