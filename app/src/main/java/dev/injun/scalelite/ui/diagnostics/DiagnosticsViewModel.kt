package dev.injun.scalelite.ui.diagnostics

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.injun.scalelite.ble.BackgroundStart
import dev.injun.scalelite.ble.ScanRegistrar
import dev.injun.scalelite.data.EventLog
import dev.injun.scalelite.data.MeasurementRepository
import dev.injun.scalelite.data.db.EventEntity
import dev.injun.scalelite.data.health.HealthConnectRepository
import dev.injun.scalelite.data.health.HealthConnectStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DiagnosticsUiState(
    val backgroundEnabled: Boolean = false,
    val healthConnect: HealthConnectStatus = HealthConnectStatus.AVAILABLE,
    val healthConnectGranted: Boolean = false,
    val backgroundStartAllowed: Boolean = true,
    val events: List<EventEntity> = emptyList(),
    val lastSyncResult: String? = null,
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    registrar: ScanRegistrar,
    private val healthConnect: HealthConnectRepository,
    private val measurements: MeasurementRepository,
    events: EventLog,
) : ViewModel() {

    private val healthConnectState = MutableStateFlow(HealthConnectStatus.AVAILABLE to false)
    private val backgroundStartAllowed = MutableStateFlow(BackgroundStart.allowed(context))
    private val lastSync = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DiagnosticsUiState> =
        combine(registrar.enabled, healthConnectState, backgroundStartAllowed, events.observe(), lastSync) { enabled, hc, bgAllowed, log, sync ->
            DiagnosticsUiState(enabled, hc.first, hc.second, bgAllowed, log, sync)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DiagnosticsUiState())

    fun refresh() {
        viewModelScope.launch {
            backgroundStartAllowed.value = BackgroundStart.allowed(context)
            val status = healthConnect.status()
            healthConnectState.value = status to (status == HealthConnectStatus.AVAILABLE && healthConnect.hasPermissions())
        }
    }

    fun retryHealthConnect() {
        viewModelScope.launch {
            val synced = measurements.syncPending()
            lastSync.value = "Retried: $synced record(s) written"
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
