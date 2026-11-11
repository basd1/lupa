package dev.bastian.lupa.ui

import app.cash.turbine.test
import dev.bastian.lupa.FakeAppsSource
import dev.bastian.lupa.FakeClock
import dev.bastian.lupa.FakeSettings
import dev.bastian.lupa.app
import dev.bastian.lupa.domain.AppCatalog
import dev.bastian.lupa.domain.AppDispatchers
import dev.bastian.lupa.domain.ScanFailure
import dev.bastian.lupa.ui.apps.AppKind
import dev.bastian.lupa.ui.apps.AppsViewModel
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val source = FakeAppsSource(
        listOf(
            app("com.example.zeta", "Zeta"),
            app("com.example.alpha", "Alpha"),
            app("com.android.settings", "Settings", isSystem = true),
        ),
    )
    private val settings = FakeSettings()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = AppsViewModel(
        catalog = AppCatalog(source, settings, FakeClock()),
        settings = settings,
        dispatchers = AppDispatchers(dispatcher, dispatcher),
    )

    @Test
    fun listsUserAppsSortedByNameAndScansOnceOnStart() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.test {
            val state = expectMostRecentItem()
            assertEquals(listOf("Alpha", "Zeta"), state.apps.map { it.label })
            assertEquals(2, state.totalApps)
            assertNull(state.progress)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, source.scans)
    }

    @Test
    fun systemAppsAreHiddenUntilTheSettingIncludesThem() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.test {
            assertEquals(2, expectMostRecentItem().apps.size)
            settings.update { it.copy(includeSystemApps = true) }
            val state = expectMostRecentItem()
            assertEquals(3, state.apps.size)
            assertTrue(state.systemAppsIncluded)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun kindFilterSplitsUserAndSystemApps() = runTest(dispatcher) {
        settings.update { it.copy(includeSystemApps = true) }
        val vm = viewModel()
        vm.state.test {
            expectMostRecentItem()
            vm.onKindChange(AppKind.SYSTEM)
            assertEquals(listOf("Settings"), expectMostRecentItem().apps.map { it.label })
            vm.onKindChange(AppKind.USER)
            assertEquals(listOf("Alpha", "Zeta"), expectMostRecentItem().apps.map { it.label })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun searchMatchesNameAndPackageIgnoringCase() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.test {
            expectMostRecentItem()
            vm.onQueryChange("ALP")
            assertEquals(listOf("Alpha"), expectMostRecentItem().apps.map { it.label })
            vm.onQueryChange("example.zeta")
            assertEquals(listOf("Zeta"), expectMostRecentItem().apps.map { it.label })
            vm.onQueryChange("nothing")
            val empty = expectMostRecentItem()
            assertTrue(empty.apps.isEmpty())
            assertEquals(2, empty.totalApps)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun appsThatFailedToReadAreCountedAndTheRestIsListed() = runTest(dispatcher) {
        source.failures = listOf(ScanFailure("com.broken", "boom"))
        val vm = viewModel()
        vm.state.test {
            val state = expectMostRecentItem()
            assertEquals(1, state.failedApps)
            assertEquals(2, state.apps.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aFailingScanShowsTheErrorAndKeepsTheScreenAlive() = runTest(dispatcher) {
        source.error = IllegalStateException("no access")
        val vm = viewModel()
        vm.state.test {
            val state = expectMostRecentItem()
            assertEquals("no access", state.error)
            assertNull(state.progress)
            assertTrue(state.apps.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun refreshScansAgain() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.test {
            expectMostRecentItem()
            source.apps = source.apps + app("com.example.new", "Newcomer")
            vm.refresh()
            assertTrue(expectMostRecentItem().apps.any { it.label == "Newcomer" })
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, source.scans)
    }
}
