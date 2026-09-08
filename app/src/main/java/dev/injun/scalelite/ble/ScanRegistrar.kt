package dev.injun.scalelite.ble

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.injun.scalelite.data.DeviceRepository
import dev.injun.scalelite.data.EventLog
import dev.injun.scalelite.data.db.EventKind
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keeps a PendingIntent scan registered with the system Bluetooth service for every paired
 * scale. The registration lives in `com.android.bluetooth`, not in this process, so the
 * scale advertising is enough to start the app: no service has to stay alive.
 *
 * The registration is lost whenever that system process restarts (reboot, Bluetooth
 * toggled, app updated); [BluetoothEventsReceiver] calls [register] again on each of those.
 */
@Singleton
class ScanRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val devices: DeviceRepository,
    private val events: EventLog,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))

    /** Whether the user has background recording switched on (independent of Bluetooth state). */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /** Turns background recording on and registers the scan for the current devices. */
    suspend fun enable() {
        prefs.edit { putBoolean(KEY_ENABLED, true) }
        _enabled.value = true
        register()
    }

    suspend fun disable() {
        prefs.edit { putBoolean(KEY_ENABLED, false) }
        _enabled.value = false
        // Spelled out inline so the permission requirement of the scan call is visibly
        // checked here. Below Android 12 the scan permission is fine location.
        val permitted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
        if (permitted) unregister()
        events.record(EventKind.SCAN_REGISTERED, "Background scan stopped")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun unregister() {
        scanner()?.let { runCatching { it.stopScan(pendingIntent()) } }
    }

    /**
     * (Re)registers the scan for every paired device, replacing any previous registration
     * (same PendingIntent, FLAG_UPDATE_CURRENT). No-op while disabled, without permission,
     * with no devices, or with Bluetooth off; the next state change retries.
     */
    suspend fun register() {
        if (!_enabled.value) return
        // Spelled out inline so the permission requirement of the scan call is visibly
        // checked here. Below Android 12 the scan permission is fine location.
        val permitted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
        if (!permitted) {
            events.record(EventKind.ERROR, "Cannot register scan: Bluetooth permission missing")
            return
        }
        val addresses = devices.all().map { it.address }
        if (addresses.isEmpty()) return
        val result = startScan(addresses) ?: run {
            events.record(EventKind.SCAN_REGISTERED, "Bluetooth is off; will register when it comes back")
            return
        }
        if (result == 0) {
            events.record(EventKind.SCAN_REGISTERED, "Background scan registered for ${addresses.size} scale(s)")
        } else {
            events.record(EventKind.ERROR, "startScan returned $result")
        }
    }

    /** Returns startScan's result code, or null when Bluetooth is off. */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun startScan(addresses: List<String>): Int? {
        val scanner = scanner() ?: return null
        // A filter is mandatory for screen-off background scans, and a MAC filter is
        // offloaded to the controller so the application processor stays asleep.
        val filters = addresses.map { ScanFilter.Builder().setDeviceAddress(it).build() }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            .setCallbackType(callbackType())
            .setMatchMode(ScanSettings.MATCH_MODE_STICKY)
            .build()
        return scanner.startScan(filters, settings, pendingIntent())
    }

    /**
     * FIRST_MATCH wakes the app when the scale starts advertising; MATCH_LOST is registered
     * with it so the controller notices the scale going quiet again and the next weigh-in
     * counts as a fresh first match. Controllers without offloaded filtering only support
     * ALL_MATCHES, which is fine because the scale advertises only while someone is on it.
     */
    private fun callbackType(): Int {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        return if (adapter?.isOffloadedFilteringSupported == true) {
            ScanSettings.CALLBACK_TYPE_FIRST_MATCH or ScanSettings.CALLBACK_TYPE_MATCH_LOST
        } else {
            ScanSettings.CALLBACK_TYPE_ALL_MATCHES
        }
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, ScanResultReceiver::class.java).setAction(ScanResultReceiver.ACTION)
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        // The system fills the scan results into the Intent extras, so it must be mutable.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    private fun scanner(): BluetoothLeScanner? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner

    private companion object {
        const val PREFS = "scan"
        const val KEY_ENABLED = "enabled"
        const val REQUEST_CODE = 0
    }
}
