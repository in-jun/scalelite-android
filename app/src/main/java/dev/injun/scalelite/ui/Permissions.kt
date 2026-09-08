package dev.injun.scalelite.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.injun.scalelite.ble.BlePermissions

/** Re-evaluates [check] every time the app returns to the foreground. */
@Composable
fun rememberOnResume(check: (Context) -> Boolean): Boolean {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(check)
    var value by remember { mutableStateOf(latest(context)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) value = latest(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return value
}

@Composable
fun rememberBluetoothPermission(): Boolean = rememberOnResume { BlePermissions.granted(it) }

@Composable
fun rememberBluetoothPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        onResult(result.values.all { it })
    }
    return { launcher.launch(BlePermissions.required.toTypedArray()) }
}

@Composable
fun rememberNotificationsEnabled(): Boolean = rememberOnResume { context ->
    val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
}

@Composable
fun rememberNotificationPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings(context)
        }
    }
}

/** Launches the Health Connect permission sheet for [permissions]. */
@Composable
fun rememberHealthConnectPermissionRequest(permissions: Set<String>, onResult: (Boolean) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        onResult(granted.containsAll(permissions))
    }
    return { launcher.launch(permissions) }
}

fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/** The system list where the user can exempt the app from battery optimization. */
fun openBatteryOptimizationSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Play Store page of the Health Connect provider, for devices where it is missing or outdated. */
fun openHealthConnectInstall(context: Context) {
    val uri = "market://details".toUri()
        .buildUpon()
        .appendQueryParameter("id", HEALTH_CONNECT_PACKAGE)
        .appendQueryParameter("url", "healthconnect://onboarding")
        .build()
    context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.vending").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
