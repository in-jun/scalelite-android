package dev.injun.scalelite.data

import android.util.Log
import dev.injun.scalelite.data.db.EventDao
import dev.injun.scalelite.data.db.EventEntity
import dev.injun.scalelite.data.db.EventKind
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/**
 * Persistent breadcrumbs for the background path. Logcat is gone by the time a user
 * wonders why last night's weigh-in is missing; this is what the Diagnostics screen shows.
 */
@Singleton
class EventLog @Inject constructor(private val dao: EventDao) {

    fun observe(limit: Int = LIMIT): Flow<List<EventEntity>> = dao.observeLatest(limit)

    suspend fun record(kind: EventKind, message: String) {
        Log.i(TAG, "$kind $message")
        dao.insert(EventEntity(epochMillis = System.currentTimeMillis(), kind = kind, message = message))
        dao.trim(LIMIT)
    }

    private companion object {
        const val TAG = "ScaleLite"
        const val LIMIT = 200
    }
}
