package dev.injun.scalelite.ble

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.injun.scalelite.core.protocol.GattIds
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** A scale seen while the Add Device screen is open. */
data class NearbyScale(
    val address: String,
    val name: String?,
    val rssi: Int,
    /** True when the advertisement itself lists the FFB0 vendor service. */
    val advertisesScaleService: Boolean,
)

/**
 * Foreground, callback-based scan used only while the user is picking a scale. Unlike the
 * background registration this needs the process alive, which is fine on a visible screen.
 * Runs unfiltered (the scale may not put FFB0 in its advertisement) at low latency.
 */
class NearbyScanner @Inject constructor(@ApplicationContext private val context: Context) {

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    fun scan(): Flow<NearbyScale> = callbackFlow {
        val scanner = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
            ?: throw IOException("Bluetooth is off")
        val ffb0 = ParcelUuid(UUID.fromString(GattIds.full(GattIds.SERVICE_FFB0)))
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(
                    NearbyScale(
                        address = result.device.address,
                        name = result.scanRecord?.deviceName,
                        rssi = result.rssi,
                        advertisesScaleService = result.scanRecord?.serviceUuids?.contains(ffb0) == true,
                    ),
                )
            }

            override fun onScanFailed(errorCode: Int) {
                close(IOException("Scan failed with code $errorCode"))
            }
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(null, settings, callback)
        awaitClose { runCatching { scanner.stopScan(callback) } }
    }
}
