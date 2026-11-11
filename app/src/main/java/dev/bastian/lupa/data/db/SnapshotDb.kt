package dev.bastian.lupa.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "snapshots")
data class SnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val takenAt: Long,
    val appCount: Int,
)

/** One app of one snapshot as JSON; one row per app keeps every row far below the cursor window limit. */
@Entity(
    tableName = "snapshot_apps",
    primaryKeys = ["snapshotId", "packageName"],
    foreignKeys = [
        ForeignKey(
            entity = SnapshotEntity::class,
            parentColumns = ["id"],
            childColumns = ["snapshotId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("snapshotId")],
)
data class SnapshotAppEntity(
    val snapshotId: Long,
    val packageName: String,
    val payload: String,
)

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM snapshots ORDER BY takenAt DESC, id DESC")
    fun headers(): Flow<List<SnapshotEntity>>

    @Query("SELECT * FROM snapshots WHERE id = :id")
    suspend fun header(id: Long): SnapshotEntity?

    @Query("SELECT * FROM snapshots ORDER BY takenAt DESC, id DESC LIMIT 1")
    suspend fun latestHeader(): SnapshotEntity?

    @Query("SELECT * FROM snapshot_apps WHERE snapshotId = :id")
    suspend fun apps(id: Long): List<SnapshotAppEntity>

    @Insert
    suspend fun insertSnapshot(snapshot: SnapshotEntity): Long

    @Insert
    suspend fun insertApps(apps: List<SnapshotAppEntity>)

    @Query(
        "DELETE FROM snapshots WHERE id NOT IN " +
            "(SELECT id FROM snapshots ORDER BY takenAt DESC, id DESC LIMIT :keep)",
    )
    suspend fun trim(keep: Int)
}

@Database(entities = [SnapshotEntity::class, SnapshotAppEntity::class], version = 1, exportSchema = true)
abstract class LupaDatabase : RoomDatabase() {
    abstract fun snapshots(): SnapshotDao
}
