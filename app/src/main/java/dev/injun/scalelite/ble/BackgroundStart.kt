package dev.injun.scalelite.ble

import android.content.Context
import android.os.PowerManager

/**
 * Android 12+ refuses to start a foreground service from the background unless the app
 * is exempt from battery optimization (one of the documented exemptions). The scan wake
 * arrives in the background by definition, so without the exemption the weigh-in cannot
 * run; the UI asks for it whenever background recording is on.
 */
object BackgroundStart {
    fun allowed(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
}
