package dev.injun.scalelite.ble

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattConnectionSettings
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.injun.scalelite.core.protocol.Dialect
import dev.injun.scalelite.core.protocol.DialectDetector
import dev.injun.scalelite.core.protocol.GattFingerprint
import dev.injun.scalelite.core.protocol.GattIds
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** What a connection to a scale produces, in order. */
sealed interface GattEvent {
    /** Service discovery finished; [dialect] is null when no known dialect matched. */
    data class Discovered(val fingerprint: GattFingerprint, val dialect: Dialect?) : GattEvent

    /** One raw notification from the dialect's measurement characteristic. */
    data class Frame(val bytes: ByteArray) : GattEvent {
        override fun equals(other: Any?) = other is Frame && bytes.contentEquals(other.bytes)
        override fun hashCode() = bytes.contentHashCode()
    }
}

/**
 * Connects to a scale, identifies its dialect from the GATT tree, subscribes to the
 * measurement characteristic and streams raw frames until the scale disconnects (which it
 * does on its own a few seconds after the weight settles). The flow completes on a clean
 * disconnect and fails with [IOException] on a connection error or an unknown tree.
 *
 * Callers must hold BLUETOOTH_CONNECT (checked by [BlePermissions] before starting).
 */
class GattScaleSession @Inject constructor(@ApplicationContext private val context: Context) {

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(address: String): Flow<GattEvent> = callbackFlow {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            ?: throw IOException("No Bluetooth adapter")
        val device = adapter.getRemoteDevice(address)

        val callback = object : BluetoothGattCallback() {
            private var notifyUuid: UUID? = null

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when {
                    newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS ->
                        gatt.discoverServices()
                    newState == BluetoothProfile.STATE_DISCONNECTED ->
                        // The scale hangs up first once it has sent the settled weight; that
                        // is the normal end of a session, not an error.
                        if (status == BluetoothGatt.GATT_SUCCESS || notifyUuid != null) close()
                        else close(IOException("GATT connection failed (status $status)"))
                }
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    close(IOException("Service discovery failed (status $status)"))
                    return
                }
                val fingerprint = gatt.fingerprint()
                val dialect = DialectDetector.detect(fingerprint)
                trySend(GattEvent.Discovered(fingerprint, dialect))
                if (dialect == null) {
                    close(IOException("Unsupported scale: ${fingerprint.services.sorted()}"))
                    return
                }
                val uuid = UUID.fromString(GattIds.full(DialectDetector.notifyCharacteristic(dialect)))
                val characteristic = gatt.services.firstNotNullOfOrNull { it.getCharacteristic(uuid) }
                    ?: run { close(IOException("Notify characteristic missing")); return }
                notifyUuid = uuid
                gatt.setCharacteristicNotification(characteristic, true)
                val cccd = characteristic.getDescriptor(UUID.fromString(GattIds.full(GattIds.CCCD)))
                    ?: run { close(IOException("CCCD missing")); return }
                writeDescriptor(gatt, cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            }

            // Android 13+ delivers the 3-argument overload; older versions only the deprecated
            // 2-argument one. Handling both on 13+ would duplicate every frame.
            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: return
                deliver(characteristic, value)
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) = deliver(characteristic, value)

            private fun deliver(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                if (characteristic.uuid == notifyUuid) trySend(GattEvent.Frame(value.copyOf()))
            }
        }

        val gatt = connect(device, callback)
        awaitClose {
            gatt.disconnect()
            gatt.close()
        }
    }

    /**
     * Android 17 (API 37) replaced every `connectGatt(Context, ...)` overload with
     * [BluetoothGattConnectionSettings] and deprecated the old ones. The replacement does
     * not exist on Android 8..16, so those versions have to keep calling the deprecated
     * method: the suppression below is the only one in this project and it goes away the
     * day minSdk reaches 37.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun connect(device: BluetoothDevice, callback: BluetoothGattCallback): BluetoothGatt {
        val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            device.connectGatt(
                BluetoothGattConnectionSettings.Builder().setTransport(BluetoothDevice.TRANSPORT_LE).build(),
                ContextCompat.getMainExecutor(context),
                callback,
            )
        } else {
            connectGattLegacy(device, callback)
        }
        return gatt ?: throw IOException("Bluetooth is unavailable")
    }

    @Suppress("DEPRECATION")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun connectGattLegacy(device: BluetoothDevice, callback: BluetoothGattCallback): BluetoothGatt? =
        device.connectGatt(
            context,
            false,
            callback,
            BluetoothDevice.TRANSPORT_LE,
            BluetoothDevice.PHY_LE_1M_MASK,
            Handler(Looper.getMainLooper()),
        )

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun writeDescriptor(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, value: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun BluetoothGatt.fingerprint(): GattFingerprint {
        val serviceIds = mutableSetOf<String>()
        val characteristicIds = mutableMapOf<String, String>()
        for (service in services) {
            val serviceId = GattIds.short(service.uuid.toString()) ?: service.uuid.toString()
            serviceIds += serviceId
            for (characteristic in service.characteristics) {
                val id = GattIds.short(characteristic.uuid.toString()) ?: characteristic.uuid.toString()
                characteristicIds[id] = serviceId
            }
        }
        return GattFingerprint(serviceIds, characteristicIds)
    }
}
