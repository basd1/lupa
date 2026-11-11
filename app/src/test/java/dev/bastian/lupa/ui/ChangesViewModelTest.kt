package dev.bastian.lupa.ui

import app.cash.turbine.test
import dev.bastian.lupa.FakeAppsSource
import dev.bastian.lupa.FakeClock
import dev.bastian.lupa.FakeSettings
import dev.bastian.lupa.FakeSnapshots
import dev.bastian.lupa.app
import dev.bastian.lupa.domain.AnalysisRunner
import dev.bastian.lupa.domain.AppCatalog
import dev.bastian.lupa.domain.AppDispatchers
import dev.bastian.lupa.ui.changes.ChangesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChangesViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers = AppDispatchers(dispatcher, dispatcher)
    private val source = FakeAppsSource(listOf(app("a")))
    private val settings = FakeSettings()
    private val snapshots = FakeSnapshots()
    private val clock = FakeClock(1_000)
    private val runner = AnalysisRunner(AppCatalog(source, settings, clock, dispatchers), snapshots, settings, clock)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ChangesViewModel(snapshots, runner, settings, dispatchers)

    @Test
    fun `with fewer than two snapshots there is nothing to compare`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.test {
            assertNull(expectMostRecentItem().changes)
            vm.analyzeNow()
            val state = expectMostRecentItem()
            assertEquals(1, state.headers.size)
            assertNull(state.changes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `analyzing twice shows what changed between the two newest snapshots`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.test {
            expectMostRecentItem()
            vm.analyzeNow()
            clock.time = 2_000
            source.apps = listOf(app("a"), app("b", "Bee"))
            vm.analyzeNow()
            val state = expectMostRecentItem()
            assertEquals(2, state.headers.size)
            assertEquals(listOf("b"), assertNotNull(state.changes).newApps.map { it.packageName })
            assertEquals(false, state.running)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `two older snapshots can be picked and are compared in time order`() = runTest(dispatcher) {
        snapshots.save(1_000, listOf(app("a")))
        snapshots.save(2_000, listOf(app("a"), app("b")))
        snapshots.save(3_000, listOf(app("a"), app("b"), app("c")))
        val vm = viewModel()
        vm.state.test {
            val first = expectMostRecentItem()
            assertEquals(listOf("c"), assertNotNull(first.changes).newApps.map { it.packageName })

            vm.selectOlder(first.headers.last()) // the oldest, with the newest still selected
            val wide = expectMostRecentItem()
            assertEquals(listOf("b", "c"), assertNotNull(wide.changes).newApps.map { it.packageName })

            vm.selectNewer(first.headers[1]) // 1000 to 2000
            assertEquals(listOf("b"), assertNotNull(expectMostRecentItem().changes).newApps.map { it.packageName })

            vm.selectOlder(first.headers[0]) // the newest picked as "from" while "to" is 2000: they swap
            val swapped = expectMostRecentItem()
            assertEquals(2_000L, swapped.older!!.takenAt)
            assertEquals(3_000L, swapped.newer!!.takenAt)
            assertEquals(listOf("c"), assertNotNull(swapped.changes).newApps.map { it.packageName })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `system apps appear in the comparison only when included`() = runTest(dispatcher) {
        snapshots.save(1_000, listOf(app("a")))
        snapshots.save(2_000, listOf(app("a"), app("sys", isSystem = true)))
        val vm = viewModel()
        vm.state.test {
            assertTrue(assertNotNull(expectMostRecentItem().changes).isEmpty)
            settings.update { it.copy(includeSystemApps = true) }
            assertEquals(listOf("sys"), assertNotNull(expectMostRecentItem().changes).newApps.map { it.packageName })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed analysis shows its reason and can be retried`() = runTest(dispatcher) {
        source.error = IllegalStateException("denied")
        val vm = viewModel()
        vm.state.test {
            expectMostRecentItem()
            vm.analyzeNow()
            assertEquals("denied", expectMostRecentItem().error)
            source.error = null
            vm.analyzeNow()
            val state = expectMostRecentItem()
            assertNull(state.error)
            assertEquals(1, state.headers.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
