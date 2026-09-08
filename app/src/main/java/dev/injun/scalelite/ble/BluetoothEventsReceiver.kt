package dev.injun.scalelite.ble

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-registers the background scan after everything that wipes it out of the system
 * Bluetooth process: a reboot, this app being updated, and Bluetooth being turned back on.
 */
@AndroidEntryPoint
class BluetoothEventsReceiver : BroadcastReceiver() {

    @Inject lateinit var registrar: ScanRegistrar

    override fun onReceive(context: Context, intent: Intent) {
        val shouldRegister = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> true
            BluetoothAdapter.ACTION_STATE_CHANGED ->
                intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) == BluetoothAdapter.STATE_ON
            else -> false
        }
        if (!shouldRegister) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                registrar.register()
            } finally {
                pending.finish()
            }
        }
    }
}
