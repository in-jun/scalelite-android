package dev.injun.scalelite.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.injun.scalelite.data.db.DeviceEntity
import dev.injun.scalelite.data.db.MeasurementEntity
import dev.injun.scalelite.data.health.HealthConnectStatus
import dev.injun.scalelite.service.WeighingState
import dev.injun.scalelite.ui.openHealthConnectInstall
import dev.injun.scalelite.ui.rememberBluetoothPermission
import dev.injun.scalelite.ui.rememberBluetoothPermissionRequest
import dev.injun.scalelite.ui.rememberHealthConnectPermissionRequest
import dev.injun.scalelite.ui.rememberNotificationPermissionRequest
import dev.injun.scalelite.ui.rememberNotificationsEnabled
import dev.injun.scalelite.ui.rememberOnResume
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    onAddDevice: () -> Unit,
    onDiagnostics: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resumed = rememberOnResume { true }
    LaunchedEffect(resumed, state.devices.size) { viewModel.refresh() }

    val bluetoothGranted = rememberBluetoothPermission()
    val requestBluetooth = rememberBluetoothPermissionRequest { viewModel.refresh() }
    val notificationsEnabled = rememberNotificationsEnabled()
    val requestNotifications = rememberNotificationPermissionRequest { }
    val requestHealthConnect = rememberHealthConnectPermissionRequest(viewModel.healthConnectPermissions) { viewModel.refresh() }

    HomeScreen(
        state = state,
        bluetoothGranted = bluetoothGranted,
        notificationsEnabled = notificationsEnabled,
        onRequestBluetooth = requestBluetooth,
        onRequestNotifications = requestNotifications,
        onRequestHealthConnect = requestHealthConnect,
        onMeasureNow = viewModel::measureNow,
        onBackgroundToggle = viewModel::setBackgroundEnabled,
        onRemoveDevice = viewModel::removeDevice,
        onDeleteMeasurement = viewModel::deleteMeasurement,
        onAddDevice = onAddDevice,
        onDiagnostics = onDiagnostics,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    bluetoothGranted: Boolean,
    notificationsEnabled: Boolean,
    onRequestBluetooth: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestHealthConnect: () -> Unit,
    onMeasureNow: (DeviceEntity) -> Unit,
    onBackgroundToggle: (Boolean) -> Unit,
    onRemoveDevice: (DeviceEntity) -> Unit,
    onDeleteMeasurement: (MeasurementEntity) -> Unit,
    onAddDevice: () -> Unit,
    onDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("ScaleLite") },
                actions = {
                    IconButton(onClick = onDiagnostics) { Icon(Icons.Outlined.Info, contentDescription = "Diagnostics") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!bluetoothGranted) {
                item { Banner("Bluetooth permission is needed to talk to the scale.", "Allow", onRequestBluetooth) }
            }
            if (state.healthConnect != HealthConnectStatus.AVAILABLE) {
                item {
                    Banner(
                        if (state.healthConnect == HealthConnectStatus.UPDATE_REQUIRED) "Health Connect needs an update." else "Health Connect is not installed.",
                        "Open Play Store",
                    ) { openHealthConnectInstall(context) }
                }
            } else if (!state.healthConnectGranted) {
                item { Banner("Allow ScaleLite to write weight to Health Connect.", "Allow", onRequestHealthConnect) }
            }
            if (!notificationsEnabled) {
                item { Banner("Notifications are off; you will not see recorded weights or problems.", "Turn on", onRequestNotifications) }
            }

            item { LatestCard(state.latest, state.weighing) }

            if (state.history.size >= 2) {
                item { HistoryChart(state.history) }
            }

            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Record in the background", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Step on the scale and the weight is saved even when the app is closed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.backgroundEnabled,
                        onCheckedChange = onBackgroundToggle,
                        enabled = bluetoothGranted && state.devices.isNotEmpty(),
                    )
                }
            }

            item { SectionTitle("Scales") }
            if (state.devices.isEmpty()) {
                item {
                    Text(
                        "No scale yet. Add one, then step on it whenever you want a reading.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.devices, key = { it.address }) { device ->
                DeviceRow(
                    device = device,
                    busy = state.weighing is WeighingState.Connecting || state.weighing is WeighingState.Reading,
                    enabled = bluetoothGranted,
                    onMeasure = { onMeasureNow(device) },
                    onRemove = { onRemoveDevice(device) },
                )
            }
            item {
                OutlinedButton(onClick = onAddDevice) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text(" Add scale")
                }
            }

            if (state.history.isNotEmpty()) {
                item { SectionTitle("Last 30 days" + if (state.pendingSync > 0) " (${state.pendingSync} waiting for Health Connect)" else "") }
                items(state.history.asReversed(), key = { it.id }) { measurement ->
                    MeasurementRow(measurement) { onDeleteMeasurement(measurement) }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun Banner(text: String, action: String, onAction: () -> Unit) {
    Card {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun LatestCard(latest: MeasurementEntity?, weighing: WeighingState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (weighing) {
                is WeighingState.Connecting -> {
                    Text("Connecting to the scale", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                is WeighingState.Reading -> {
                    Text(formatKg(weighing.grams), style = MaterialTheme.typography.displayMedium)
                    Text("Hold still (${weighing.progress}/${weighing.required})", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(progress = { weighing.progress / weighing.required.toFloat() }, modifier = Modifier.fillMaxWidth())
                }
                is WeighingState.Recorded -> {
                    Text(formatKg(weighing.grams), style = MaterialTheme.typography.displayMedium)
                    Text(
                        if (weighing.syncedToHealthConnect) "Recorded and sent to Health Connect" else "Recorded; Health Connect sync pending",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                is WeighingState.Failed -> {
                    Text(latest?.let { formatKg(it.grams) } ?: "--", style = MaterialTheme.typography.displayMedium)
                    Text(weighing.reason, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                WeighingState.Idle -> {
                    Text(latest?.let { formatKg(it.grams) } ?: "--", style = MaterialTheme.typography.displayMedium)
                    Text(
                        latest?.let { "Last weigh-in ${formatTime(it.epochMillis)}" } ?: "No weigh-in yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryChart(history: List<MeasurementEntity>) {
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val min = history.minOf { it.grams }
    val max = history.maxOf { it.grams }
    val span = (max - min).coerceAtLeast(1_000)
    val start = history.first().epochMillis
    val end = history.last().epochMillis.coerceAtLeast(start + 1)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatKg(max), style = MaterialTheme.typography.labelSmall)
                Text(formatKg(min), style = MaterialTheme.typography.labelSmall)
            }
            Canvas(Modifier.fillMaxWidth().height(140.dp).padding(vertical = 8.dp)) {
                drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
                val points = history.map { m ->
                    Offset(
                        x = (m.epochMillis - start).toFloat() / (end - start) * size.width,
                        y = size.height - (m.grams - min).toFloat() / span * size.height,
                    )
                }
                points.zipWithNext { a, b -> drawLine(color, a, b, strokeWidth = 5f, cap = StrokeCap.Round) }
                points.forEach { drawCircle(color, radius = 7f, center = it) }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: DeviceEntity, busy: Boolean, enabled: Boolean, onMeasure: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(device.name, style = MaterialTheme.typography.bodyLarge)
            Text("${device.address}  ·  ${device.dialect.displayName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick = onMeasure, enabled = enabled && !busy) { Text("Measure") }
        IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = "Remove ${device.name}") }
    }
}

@Composable
private fun MeasurementRow(measurement: MeasurementEntity, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(formatKg(measurement.grams), style = MaterialTheme.typography.bodyLarge)
            Text(
                formatTime(measurement.epochMillis) + if (measurement.healthConnectId == null) "  ·  not in Health Connect yet" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (measurement.healthConnectId == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray) }
    }
}

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

fun formatKg(grams: Int): String = String.format(Locale.getDefault(), "%.2f kg", grams / 1000.0)

fun formatTime(epochMillis: Long): String =
    timeFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
