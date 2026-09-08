package dev.injun.scalelite.ble

import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.injun.scalelite.data.EventLog
import dev.injun.scalelite.data.db.EventKind
import dev.injun.scalelite.service.WeighingService
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Where the system delivers PendingIntent scan results. This is the cold-start entry of the
 * background path: the process usually does not exist until this fires.
 */
@AndroidEntryPoint
class ScanResultReceiver : BroadcastReceiver() {

    @Inject lateinit var events: EventLog
    @Inject lateinit var registrar: ScanRegistrar

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(context, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, intent: Intent) {
        val error = intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, NO_ERROR)
        if (error != NO_ERROR) {
            events.record(EventKind.ERROR, "Scan failed with code $error; re-registering")
            registrar.register()
            return
        }
        val callbackType = intent.getIntExtra(BluetoothLeScanner.EXTRA_CALLBACK_TYPE, 0)
        val results = IntentCompat.getParcelableArrayListExtra(
            intent,
            BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT,
            ScanResult::class.java,
        ).orEmpty()
        if (callbackType == ScanSettings.CALLBACK_TYPE_MATCH_LOST) {
            events.record(EventKind.WAKE, "Scale out of range: ${results.joinToString { it.device.address }}")
            return
        }
        val address = results.firstOrNull()?.device?.address ?: return
        events.record(EventKind.WAKE, "Scale $address is advertising")
        WeighingService.start(context, address)
    }

    companion object {
        const val ACTION = "dev.injun.scalelite.SCAN_RESULT"
        private const val NO_ERROR = -1
    }
}
