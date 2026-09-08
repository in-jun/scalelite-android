package dev.injun.scalelite.ui.adddevice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.injun.scalelite.ble.GattEvent
import dev.injun.scalelite.ble.GattScaleSession
import dev.injun.scalelite.ble.NearbyScale
import dev.injun.scalelite.ble.NearbyScanner
import dev.injun.scalelite.ble.ScanRegistrar
import dev.injun.scalelite.core.protocol.Dialect
import dev.injun.scalelite.data.DeviceRepository
import dev.injun.scalelite.data.EventLog
import dev.injun.scalelite.data.db.EventKind
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

sealed interface AddDeviceStep {
    /** Listening for advertisements; the scale only advertises while someone stands on it. */
    data object Scanning : AddDeviceStep

    data class Identifying(val scale: NearbyScale) : AddDeviceStep

    data class Identified(val scale: NearbyScale, val dialect: Dialect) : AddDeviceStep

    data class Unsupported(val scale: NearbyScale, val services: List<String>) : AddDeviceStep

    data class Error(val message: String) : AddDeviceStep

    data object Saved : AddDeviceStep
}

data class AddDeviceUiState(
    val step: AddDeviceStep = AddDeviceStep.Scanning,
    val nearby: List<NearbyScale> = emptyList(),
)

@HiltViewModel
class AddDeviceViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scanner: NearbyScanner,
    private val session: GattScaleSession,
    private val devices: DeviceRepository,
    private val registrar: ScanRegistrar,
    private val events: EventLog,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddDeviceUiState())
    val uiState: StateFlow<AddDeviceUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    fun startScanning() {
        if (scanJob?.isActive == true) return
        // Android 12+ permission names; below that they do not exist and location covers scanning.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                )
        ) {
            _uiState.update { it.copy(step = AddDeviceStep.Error("Bluetooth permission is required to find the scale.")) }
            return
        }
        _uiState.update { it.copy(step = AddDeviceStep.Scanning) }
        scanJob = viewModelScope.launch {
            scanner.scan()
                .catch { e -> _uiState.update { it.copy(step = AddDeviceStep.Error(e.message ?: "Scan failed")) } }
                .collect { seen ->
                    _uiState.update { state ->
                        val others = state.nearby.filterNot { it.address == seen.address }
                        val merged = (others + seen).sortedWith(
                            compareByDescending<NearbyScale> { it.advertisesScaleService }
                                .thenByDescending { it.name != null }
                                .thenByDescending { it.rssi },
                        )
                        state.copy(nearby = merged)
                    }
                }
        }
    }

    fun stopScanning() {
        scanJob?.cancel()
        scanJob = null
    }

    /** Connects once to read the GATT tree and pick the dialect; nothing is saved yet. */
    fun identify(scale: NearbyScale) {
        stopScanning()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            _uiState.update { it.copy(step = AddDeviceStep.Error("Bluetooth permission is required to connect.")) }
            return
        }
        _uiState.update { it.copy(step = AddDeviceStep.Identifying(scale)) }
        viewModelScope.launch {
            val step = try {
                val discovered = withTimeout(IDENTIFY_TIMEOUT_MS) {
                    session.connect(scale.address).first { it is GattEvent.Discovered } as GattEvent.Discovered
                }
                discovered.dialect?.let { AddDeviceStep.Identified(scale, it) }
                    ?: AddDeviceStep.Unsupported(scale, discovered.fingerprint.services.sorted())
            } catch (e: TimeoutCancellationException) {
                AddDeviceStep.Error("The scale did not answer. Step on it so it wakes up, then try again.")
            } catch (e: Exception) {
                // An unknown tree closes the flow with an exception after emitting Discovered;
                // first() already returned by then, so anything caught here is a real failure.
                AddDeviceStep.Error(e.message ?: "Could not connect")
            }
            _uiState.update { it.copy(step = step) }
        }
    }

    fun save(scale: NearbyScale, dialect: Dialect) {
        viewModelScope.launch {
            devices.add(scale.address, scale.name ?: "Scale ${scale.address.takeLast(5)}", dialect)
            events.record(EventKind.SCAN_REGISTERED, "Added ${scale.address} as ${dialect.displayName}")
            registrar.register()
            _uiState.update { it.copy(step = AddDeviceStep.Saved) }
        }
    }

    fun retry() {
        _uiState.update { it.copy(nearby = emptyList()) }
        startScanning()
    }

    override fun onCleared() {
        stopScanning()
    }

    private companion object {
        const val IDENTIFY_TIMEOUT_MS = 20_000L
    }
}
