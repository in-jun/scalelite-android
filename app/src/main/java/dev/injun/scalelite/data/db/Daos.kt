package dev.injun.scalelite.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY addedAtMillis")
    fun observeAll(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices ORDER BY addedAtMillis")
    suspend fun all(): List<DeviceEntity>

    @Query("SELECT * FROM devices WHERE address = :address")
    suspend fun byAddress(address: String): DeviceEntity?

    @Upsert
    suspend fun upsert(device: DeviceEntity)

    @Query("DELETE FROM devices WHERE address = :address")
    suspend fun delete(address: String)
}

@Dao
interface MeasurementDao {
    @Query("SELECT * FROM measurements ORDER BY epochMillis DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurements WHERE epochMillis >= :sinceMillis ORDER BY epochMillis")
    fun observeSince(sinceMillis: Long): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurements WHERE healthConnectId IS NULL ORDER BY epochMillis")
    suspend fun pendingHealthConnect(): List<MeasurementEntity>

    /** Ignores a duplicate (same device and instant), returning -1 in that case. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(measurement: MeasurementEntity): Long

    @Query("UPDATE measurements SET healthConnectId = :recordId, healthConnectError = NULL WHERE id = :id")
    suspend fun markSynced(id: Long, recordId: String)

    @Query("UPDATE measurements SET healthConnectError = :error WHERE id = :id")
    suspend fun markSyncFailed(id: Long, error: String)

    @Query("DELETE FROM measurements WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY epochMillis DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<EventEntity>>

    @Insert
    suspend fun insert(event: EventEntity)

    /** Keeps the table bounded; called after each insert. */
    @Query("DELETE FROM events WHERE id NOT IN (SELECT id FROM events ORDER BY epochMillis DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}
