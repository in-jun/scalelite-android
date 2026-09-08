package dev.injun.scalelite.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.injun.scalelite.core.protocol.Dialect

/** A scale the user has paired. One row per MAC address. */
@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val address: String,
    val name: String,
    val dialect: Dialect,
    val addedAtMillis: Long,
)

/** One confirmed weigh-in. [healthConnectId] is null until the record reaches Health Connect. */
@Entity(
    tableName = "measurements",
    indices = [Index(value = ["deviceAddress", "epochMillis"], unique = true)],
)
data class MeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceAddress: String,
    val epochMillis: Long,
    val grams: Int,
    val rawFrameHex: String,
    val healthConnectId: String? = null,
    val healthConnectError: String? = null,
)

/** Diagnostic trail of what the background path did, newest first in the UI. */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochMillis: Long,
    val kind: EventKind,
    val message: String,
)

enum class EventKind { SCAN_REGISTERED, WAKE, CONNECT, WEIGHT, HEALTH_CONNECT, ERROR }
