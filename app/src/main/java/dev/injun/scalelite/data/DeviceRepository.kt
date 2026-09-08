package dev.injun.scalelite.data

import dev.injun.scalelite.core.protocol.Dialect
import dev.injun.scalelite.data.db.DeviceDao
import dev.injun.scalelite.data.db.DeviceEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class DeviceRepository @Inject constructor(private val dao: DeviceDao) {

    val devices: Flow<List<DeviceEntity>> = dao.observeAll()

    suspend fun all(): List<DeviceEntity> = dao.all()

    suspend fun find(address: String): DeviceEntity? = dao.byAddress(address)

    suspend fun add(address: String, name: String, dialect: Dialect) {
        dao.upsert(DeviceEntity(address, name, dialect, System.currentTimeMillis()))
    }

    suspend fun remove(address: String) = dao.delete(address)
}
