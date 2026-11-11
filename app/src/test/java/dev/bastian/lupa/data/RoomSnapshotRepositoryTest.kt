package dev.bastian.lupa.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.bastian.lupa.app
import dev.bastian.lupa.data.db.LupaDatabase
import dev.bastian.lupa.data.db.RoomSnapshotRepository
import dev.bastian.lupa.domain.AppDispatchers
import dev.bastian.lupa.domain.SnapshotRepository
import dev.bastian.lupa.granted
import dev.bastian.lupa.requested
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class RoomSnapshotRepositoryTest {
    private lateinit var database: LupaDatabase
    private lateinit var repository: RoomSnapshotRepository

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, LupaDatabase::class.java).allowMainThreadQueries().build()
        repository = RoomSnapshotRepository(database, AppDispatchers(Dispatchers.Unconfined, Dispatchers.Unconfined))
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `a saved snapshot comes back with every field of every app`() = runTest {
        val apps = listOf(
            app("a", "Alpha", permissions = listOf(granted("android.permission.CAMERA"), requested("android.permission.RECORD_AUDIO")), declaresDeviceAdmin = true),
            app("b", "Beta", installer = null, isSystem = true),
        )
        val id = repository.save(1_000, apps)

        val loaded = repository.load(id)!!
        assertEquals(1_000, loaded.takenAt)
        assertEquals(apps.sortedBy { it.packageName }, loaded.apps.sortedBy { it.packageName })
    }

    @Test
    fun `latest is the newest and a missing id is null`() = runTest {
        repository.save(1_000, listOf(app("old")))
        repository.save(3_000, listOf(app("new")))
        repository.save(2_000, listOf(app("middle")))

        assertEquals("new", repository.latest()!!.apps.single().packageName)
        assertNull(repository.load(999))
    }

    @Test
    fun `headers are listed newest first with their app count`() = runTest {
        repository.save(1_000, listOf(app("a")))
        repository.save(2_000, listOf(app("a"), app("b")))

        repository.headers.test {
            val headers = awaitItem()
            assertEquals(listOf(2_000L, 1_000L), headers.map { it.takenAt })
            assertEquals(listOf(2, 1), headers.map { it.appCount })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `only the newest twenty snapshots survive and their apps go with the old ones`() = runTest {
        repeat(23) { repository.save(1_000L + it, listOf(app("p$it"))) }

        repository.headers.test {
            val headers = awaitItem()
            assertEquals(SnapshotRepository.MAX_SNAPSHOTS, headers.size)
            assertEquals(1_022L, headers.first().takenAt)
            assertEquals(1_003L, headers.last().takenAt)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(20, database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM snapshot_apps").use {
            it.moveToFirst()
            it.getInt(0)
        })
    }
}
