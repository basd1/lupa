package dev.bastian.lupa.data.db

import androidx.room.withTransaction
import dev.bastian.lupa.domain.AppDispatchers
import dev.bastian.lupa.domain.InstalledApp
import dev.bastian.lupa.domain.Snapshot
import dev.bastian.lupa.domain.SnapshotHeader
import dev.bastian.lupa.domain.SnapshotRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class RoomSnapshotRepository(
    private val database: LupaDatabase,
    private val dispatchers: AppDispatchers,
) : SnapshotRepository {
    private val dao = database.snapshots()
    private val json = Json { ignoreUnknownKeys = true }

    override val headers: Flow<List<SnapshotHeader>> = dao.headers().map { rows ->
        rows.map { SnapshotHeader(it.id, it.takenAt, it.appCount) }
    }

    override suspend fun save(takenAt: Long, apps: List<InstalledApp>): Long = withContext(dispatchers.io) {
        val rows = apps.map { it.packageName to json.encodeToString(InstalledApp.serializer(), it) }
        database.withTransaction {
            val id = dao.insertSnapshot(SnapshotEntity(takenAt = takenAt, appCount = apps.size))
            dao.insertApps(rows.map { (name, payload) -> SnapshotAppEntity(id, name, payload) })
            dao.trim(SnapshotRepository.MAX_SNAPSHOTS)
            id
        }
    }

    override suspend fun load(id: Long): Snapshot? = withContext(dispatchers.io) {
        dao.header(id)?.let { toSnapshot(it) }
    }

    override suspend fun latest(): Snapshot? = withContext(dispatchers.io) {
        dao.latestHeader()?.let { toSnapshot(it) }
    }

    private suspend fun toSnapshot(header: SnapshotEntity): Snapshot = Snapshot(
        id = header.id,
        takenAt = header.takenAt,
        apps = dao.apps(header.id).map { json.decodeFromString(InstalledApp.serializer(), it.payload) },
    )
}
