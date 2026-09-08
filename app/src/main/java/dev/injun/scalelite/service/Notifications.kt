package dev.injun.scalelite.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.injun.scalelite.MainActivity
import dev.injun.scalelite.R
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Notifications @Inject constructor(@ApplicationContext private val context: Context) {

    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_WEIGHING, "Weighing in progress", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Shown for a few seconds while a scale is connected" },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULTS, "Recorded weights", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "One notification per weigh-in recorded in the background" },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_PROBLEMS, "Problems", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "A weigh-in could not be recorded and needs attention" },
        )
    }

    /** The foreground-service notification for the connection window. */
    fun weighing(): Notification = NotificationCompat.Builder(context, CHANNEL_WEIGHING)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("Reading the scale")
        .setContentIntent(openApp())
        .setOngoing(true)
        .setSilent(true)
        .build()

    fun recorded(grams: Int, syncedToHealthConnect: Boolean) = post(
        id = ID_RESULT,
        channel = CHANNEL_RESULTS,
        title = String.format(Locale.getDefault(), "%.2f kg recorded", grams / 1000.0),
        text = if (syncedToHealthConnect) "Saved to Health Connect" else "Saved on this device; Health Connect sync pending",
    )

    fun problem(text: String) = post(ID_PROBLEM, CHANNEL_PROBLEMS, "Weigh-in not recorded", text)

    private fun post(id: Int, channel: String, title: String, text: String) {
        // On API 33+ posting without POST_NOTIFICATIONS is silently dropped; check inline so
        // the requirement of notify() is visibly met here.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CHANNEL_WEIGHING = "weighing"
        const val CHANNEL_RESULTS = "results"
        const val CHANNEL_PROBLEMS = "problems"
        const val ID_WEIGHING = 1
        const val ID_RESULT = 2
        const val ID_PROBLEM = 3
    }
}
