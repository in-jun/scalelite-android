package dev.injun.scalelite.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

enum class HealthConnectStatus { AVAILABLE, NOT_INSTALLED, UPDATE_REQUIRED }

/**
 * The only thing written is [WeightRecord]: it is the one body metric Samsung Health
 * pulls back out of Health Connect, and the supported scale sends nothing else.
 */
@Singleton
class HealthConnectRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val permissions: Set<String> = setOf(HealthPermission.getWritePermission(WeightRecord::class))

    fun status(): HealthConnectStatus = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthConnectStatus.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectStatus.UPDATE_REQUIRED
        else -> HealthConnectStatus.NOT_INSTALLED
    }

    suspend fun hasPermissions(): Boolean = runCatching {
        client().permissionController.getGrantedPermissions().containsAll(permissions)
    }.getOrDefault(false)

    /** Inserts one weight and returns the Health Connect record id. */
    suspend fun writeWeight(epochMillis: Long, grams: Int): Result<String> = runCatching {
        check(status() == HealthConnectStatus.AVAILABLE) { "Health Connect is not available" }
        val client = client()
        check(client.permissionController.getGrantedPermissions().containsAll(permissions)) {
            "Weight write permission not granted"
        }
        val time = Instant.ofEpochMilli(epochMillis)
        val record = WeightRecord(
            time = time,
            zoneOffset = ZoneId.systemDefault().rules.getOffset(time),
            weight = Mass.grams(grams.toDouble()),
            metadata = Metadata.autoRecorded(Device(type = Device.TYPE_SCALE)),
        )
        client.insertRecords(listOf(record)).recordIdsList.single()
    }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)
}
