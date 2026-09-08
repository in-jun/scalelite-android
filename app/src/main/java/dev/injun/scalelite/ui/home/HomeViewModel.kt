package dev.injun.scalelite.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.injun.scalelite.ble.ScanRegistrar
import dev.injun.scalelite.data.DeviceRepository
import dev.injun.scalelite.data.MeasurementRepository
import dev.injun.scalelite.data.db.DeviceEntity
import dev.injun.scalelite.data.db.MeasurementEntity
import dev.injun.scalelite.data.health.HealthConnectRepository
import dev.injun.scalelite.data.health.HealthConnectStatus
import dev.injun.scalelite.service.WeighingMonitor
import dev.injun.scalelite.service.WeighingService
import dev.injun.scalelite.service.WeighingState
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val devices: List<DeviceEntity> = emptyList(),
    val latest: MeasurementEntity? = null,
    val history: List<MeasurementEntity> = emptyList(),
    val pendingSync: Int = 0,
    val backgroundEnabled: Boolean = false,
    val weighing: WeighingState = WeighingState.Idle,
    val healthConnect: HealthConnectStatus = HealthConnectStatus.AVAILABLE,
    val healthConnectGranted: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val devices: DeviceRepository,
    private val measurements: MeasurementRepository,
    private val healthConnect: HealthConnectRepository,
    private val registrar: ScanRegistrar,
    monitor: WeighingMonitor,
) : ViewModel() {

    val healthConnectPermissions: Set<String> get() = healthConnect.permissions

    private val healthConnectState = MutableStateFlow(HealthConnectStatus.AVAILABLE to false)

    private data class Data(
        val devices: List<DeviceEntity>,
        val history: List<MeasurementEntity>,
        val latest: MeasurementEntity?,
    )

    private val data = combine(
        devices.devices,
        measurements.since(System.currentTimeMillis() - HISTORY_WINDOW.toMillis()),
        measurements.latest(limit = 1),
    ) { devices, history, latest -> Data(devices, history, latest.firstOrNull()) }

    val uiState: StateFlow<HomeUiState> =
        combine(data, registrar.enabled, monitor.state, healthConnectState) { data, enabled, weighing, hc ->
            HomeUiState(
                devices = data.devices,
                latest = data.latest,
                history = data.history,
                pendingSync = data.history.count { it.healthConnectId == null },
                backgroundEnabled = enabled,
                weighing = weighing,
                healthConnect = hc.first,
                healthConnectGranted = hc.second,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    /** Called on every resume: permissions can change behind the app's back. */
    fun refresh() {
        viewModelScope.launch {
            val status = healthConnect.status()
            val granted = status == HealthConnectStatus.AVAILABLE && healthConnect.hasPermissions()
            healthConnectState.value = status to granted
            if (granted) measurements.syncPending()
        }
    }

    fun measureNow(device: DeviceEntity) = WeighingService.start(context, device.address)

    fun setBackgroundEnabled(enabled: Boolean) {
        viewModelScope.launch { if (enabled) registrar.enable() else registrar.disable() }
    }

    fun removeDevice(device: DeviceEntity) {
        viewModelScope.launch {
            devices.remove(device.address)
            registrar.register()
        }
    }

    fun deleteMeasurement(measurement: MeasurementEntity) {
        viewModelScope.launch { measurements.delete(measurement.id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        val HISTORY_WINDOW: Duration = Duration.ofDays(30)
    }
}
