package dev.injun.scalelite.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.injun.scalelite.ble.GattEvent
import dev.injun.scalelite.ble.GattScaleSession
import dev.injun.scalelite.core.model.Weighing
import dev.injun.scalelite.core.protocol.ScaleFrame
import dev.injun.scalelite.core.protocol.StableWeightDetector
import dev.injun.scalelite.data.DeviceRepository
import dev.injun.scalelite.data.EventLog
import dev.injun.scalelite.data.MeasurementRepository
import dev.injun.scalelite.data.db.EventKind
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Short-lived foreground service that owns one weigh-in: connect, wait for the weight to
 * settle, store it, push it to Health Connect, stop. It is started by [ScanResultReceiver]
 * from a cold process in the background path and by the Home screen for a manual reading.
 */
@AndroidEntryPoint
class WeighingService : Service() {

    @Inject lateinit var session: GattScaleSession
    @Inject lateinit var devices: DeviceRepository
    @Inject lateinit var measurements: MeasurementRepository
    @Inject lateinit var events: EventLog
    @Inject lateinit var notifications: Notifications
    @Inject lateinit var monitor: WeighingMonitor

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var current: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = intent?.getStringExtra(EXTRA_ADDRESS) ?: run { stopSelf(); return START_NOT_STICKY }
        ServiceCompat.startForeground(
            this,
            Notifications.ID_WEIGHING,
            notifications.weighing(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
        )
        if (current?.isActive == true) return START_NOT_STICKY
        current = scope.launch {
            try {
                weigh(address)
            } finally {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun weigh(address: String) {
        monitor.update(WeighingState.Connecting(address))
        // Checked inline (not through BlePermissions) so the BLUETOOTH_CONNECT requirement
        // of GattScaleSession.connect is visibly satisfied here.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            fail(address, "Bluetooth permission was revoked")
            return
        }
        val detector = StableWeightDetector()
        var codec = devices.find(address)?.dialect?.codec()
        var recorded: WeighingState.Recorded? = null
        try {
            withTimeout(WINDOW_MS) {
                session.connect(address).collect { event ->
                    when (event) {
                        is GattEvent.Discovered -> {
                            events.record(EventKind.CONNECT, "Connected to $address (${event.dialect?.displayName ?: "unknown"})")
                            codec = event.dialect?.codec()
                        }
                        is GattEvent.Frame -> {
                            val frame = codec?.decode(event.bytes) as? ScaleFrame.Weight ?: return@collect
                            val confirmed = detector.offer(frame.grams)
                            if (confirmed == null) {
                                monitor.update(
                                    WeighingState.Reading(address, frame.grams, detector.progress, StableWeightDetector.DEFAULT_REPEATS),
                                )
                            } else {
                                recorded = record(address, Weighing(confirmed, System.currentTimeMillis(), event.bytes))
                            }
                        }
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            if (recorded == null) fail(address, "No settled weight within ${WINDOW_MS / 1000} s")
        } catch (e: Exception) {
            if (recorded == null) fail(address, e.message ?: e.javaClass.simpleName)
        }
        recorded?.let { monitor.update(it) } ?: run {
            if (monitor.state.value !is WeighingState.Failed) fail(address, "Scale disconnected before the weight settled")
        }
    }

    private suspend fun record(address: String, weighing: Weighing): WeighingState.Recorded {
        val kg = String.format(Locale.US, "%.2f", weighing.kilograms)
        events.record(EventKind.WEIGHT, "Settled at $kg kg")
        val row = measurements.record(address, weighing)
        val synced = row?.healthConnectId != null
        events.record(
            EventKind.HEALTH_CONNECT,
            if (synced) "Written to Health Connect" else "Health Connect write pending: ${row?.healthConnectError ?: "duplicate"}",
        )
        notifications.recorded(weighing.grams, synced)
        return WeighingState.Recorded(address, weighing.grams, synced)
    }

    private suspend fun fail(address: String, reason: String) {
        events.record(EventKind.ERROR, reason)
        notifications.problem(reason)
        monitor.update(WeighingState.Failed(address, reason))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ADDRESS = "address"

        /** Captures show connect-to-settled in about 3 s; this covers a slow controller. */
        private const val WINDOW_MS = 45_000L

        fun start(context: Context, address: String) {
            val intent = Intent(context, WeighingService::class.java).putExtra(EXTRA_ADDRESS, address)
            context.startForegroundService(intent)
        }
    }
}
