package dev.injun.scalelite.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DeviceEntity::class, MeasurementEntity::class, EventEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ScaleLiteDatabase : RoomDatabase() {
    abstract fun devices(): DeviceDao
    abstract fun measurements(): MeasurementDao
    abstract fun events(): EventDao
}
