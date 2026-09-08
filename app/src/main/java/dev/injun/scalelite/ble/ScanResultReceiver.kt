package dev.injun.scalelite.ble

import android.app.ForegroundServiceStartNotAllowedException
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.IntentCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.injun.scalelite.data.EventLog
import dev.injun.scalelite.data.db.EventKind
import dev.injun.scalelite.service.Notifications
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
    @Inject lateinit var notifications: Notifications

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(context, intent)
            } catch (e: Exception) {
                // An exception here would take the whole cold-started process down with it.
                events.record(EventKind.ERROR, "Wake handling failed: ${e.message ?: e.javaClass.simpleName}")
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
        try {
            WeighingService.start(context, address)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                // Android 12+ blocks a foreground-service start from the background unless the
                // app is exempt from battery optimization. Tell the user instead of dying.
                events.record(EventKind.ERROR, BACKGROUND_BLOCKED)
                notifications.problem(BACKGROUND_BLOCKED)
            } else {
                throw e
            }
        }
    }

    companion object {
        const val ACTION = "dev.injun.scalelite.SCAN_RESULT"
        const val BACKGROUND_BLOCKED =
            "Android blocked the background weigh-in. Turn off battery optimization for Scale Lite " +
                "(Diagnostics > Battery optimization settings) so the scale can wake the app."
        private const val NO_ERROR = -1
    }
}
