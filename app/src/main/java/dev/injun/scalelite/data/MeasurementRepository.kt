package dev.injun.scalelite.data

import dev.injun.scalelite.core.model.Weighing
import dev.injun.scalelite.data.db.MeasurementDao
import dev.injun.scalelite.data.db.MeasurementEntity
import dev.injun.scalelite.data.health.HealthConnectRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Local source of truth for weigh-ins. Health Connect is a downstream copy: a write that
 * fails (permission revoked, provider missing) leaves the row pending and is retried on
 * the next weigh-in or when the user opens the app.
 */
@Singleton
class MeasurementRepository @Inject constructor(
    private val dao: MeasurementDao,
    private val healthConnect: HealthConnectRepository,
) {
    fun latest(limit: Int = 50): Flow<List<MeasurementEntity>> = dao.observeLatest(limit)

    fun since(sinceMillis: Long): Flow<List<MeasurementEntity>> = dao.observeSince(sinceMillis)

    /** Stores [weighing] and pushes it to Health Connect. Returns the row, or null if it was a duplicate. */
    suspend fun record(deviceAddress: String, weighing: Weighing): MeasurementEntity? {
        val row = MeasurementEntity(
            deviceAddress = deviceAddress,
            epochMillis = weighing.epochMillis,
            grams = weighing.grams,
            rawFrameHex = weighing.rawFrame.joinToString(" ") { "%02X".format(it) },
        )
        val id = dao.insert(row)
        if (id == -1L) return null
        val saved = row.copy(id = id)
        sync(saved)
        return saved
    }

    /** Retries every row that has not reached Health Connect yet. Returns how many succeeded. */
    suspend fun syncPending(): Int = dao.pendingHealthConnect().count { sync(it) }

    private suspend fun sync(row: MeasurementEntity): Boolean =
        healthConnect.writeWeight(row.epochMillis, row.grams)
            .onSuccess { dao.markSynced(row.id, it) }
            .onFailure { dao.markSyncFailed(row.id, it.message ?: it.javaClass.simpleName) }
            .isSuccess

    suspend fun delete(id: Long) = dao.delete(id)
}
