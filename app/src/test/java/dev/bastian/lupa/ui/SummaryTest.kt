package dev.bastian.lupa.ui

import app.cash.turbine.test
import dev.bastian.lupa.FakeAppsSource
import dev.bastian.lupa.FakeClock
import dev.bastian.lupa.FakeSettings
import dev.bastian.lupa.app
import dev.bastian.lupa.domain.AppCatalog
import dev.bastian.lupa.domain.AppDispatchers
import dev.bastian.lupa.domain.AppFilter
import dev.bastian.lupa.domain.AppFilterStore
import dev.bastian.lupa.domain.AppAssessment
import dev.bastian.lupa.domain.InstalledApp
import dev.bastian.lupa.domain.PermissionCategory
import dev.bastian.lupa.domain.RiskLevel
import dev.bastian.lupa.domain.SortOrder
import dev.bastian.lupa.domain.SummaryCalculator
import dev.bastian.lupa.domain.filtered
import dev.bastian.lupa.granted
import dev.bastian.lupa.requested
import dev.bastian.lupa.ui.apps.AppsViewModel
import dev.bastian.lupa.ui.summary.SummaryViewModel
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

@OptIn(ExperimentalCoroutinesApi::class)
class SummaryTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private fun p(name: String) = "android.permission.$name"

    private val apps: List<InstalledApp> = listOf(
        app("com.chat", "Chat", permissions = listOf(granted(p("CAMERA")), granted(p("RECORD_AUDIO")), granted(p("READ_CONTACTS")))),
        app("com.maps", "Maps", permissions = listOf(granted(p("ACCESS_FINE_LOCATION")), granted(p("ACCESS_BACKGROUND_LOCATION")), requested(p("CAMERA")))),
        app("com.spy", "Spy", installer = null, targetSdk = 21, declaresAccessibilityService = true,
            permissions = listOf(granted(p("SYSTEM_ALERT_WINDOW")), granted(p("READ_SMS")), granted(p("RECORD_AUDIO")), granted(p("ACCESS_BACKGROUND_LOCATION")))),
        app("com.flashlight", "Flashlight", targetSdk = 25, permissions = listOf(requested(p("CAMERA")))),
        app("com.plain", "Plain"),
        app("com.android.sys", "System thing", isSystem = true, permissions = listOf(granted(p("ACCESS_COARSE_LOCATION")))),
    )

    private val assessed: List<AppAssessment> = apps.map(AppAssessment::of)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun totalsAndKindsAddUp() {
        val summary = SummaryCalculator.of(assessed)
        assertEquals(6, summary.total)
        assertEquals(5, summary.userApps)
        assertEquals(1, summary.systemApps)
        assertEquals(summary.total, summary.byRisk.values.sum())
    }

    @Test
    fun everyCategoryCountEqualsTheListFilteredByThatCategory() {
        val summary = SummaryCalculator.of(assessed)
        PermissionCategory.entries.forEach { category ->
            val card = summary.categories.firstOrNull { it.category == category }
            val listed = assessed.filtered(AppFilter(category = category)).size
            assertEquals(card?.withAccess ?: 0, listed, category.name)
        }
        val camera = summary.categories.first { it.category == PermissionCategory.CAMERA }
        assertEquals(1, camera.withAccess)
        assertEquals(2, camera.requestedOnly)
    }

    @Test
    fun everyRiskCountEqualsTheListFilteredByThatLevel() {
        val summary = SummaryCalculator.of(assessed)
        RiskLevel.entries.forEach { level ->
            assertEquals(summary.byRisk.getValue(level), assessed.filtered(AppFilter(risk = level)).size, level.name)
        }
    }

    @Test
    fun oldTargetCountEqualsTheListFilteredByOldTarget() {
        val summary = SummaryCalculator.of(assessed)
        assertEquals(2, summary.oldTargetApps)
        assertEquals(summary.oldTargetApps, assessed.filtered(AppFilter(oldTargetOnly = true)).size)
    }

    @Test
    fun categoriesAreOrderedByHowManyAppsHaveAccess() {
        val counts = SummaryCalculator.of(assessed).categories.map { it.withAccess }
        assertEquals(counts.sortedDescending(), counts)
    }

    @Test
    fun filtersCombineAndSortOrdersApply() {
        val both = assessed.filtered(AppFilter(category = PermissionCategory.MICROPHONE, risk = RiskLevel.HIGH))
        assertEquals(listOf("Spy"), both.map { it.app.label })
        assertEquals(
            listOf("Spy", "Maps", "Chat"),
            assessed.filtered(AppFilter(category = null, query = "", sort = SortOrder.RISK)).take(3).map { it.app.label },
        )
        assertEquals(
            assessed.map { it.app.label }.sortedBy { it.lowercase() },
            assessed.filtered(AppFilter(sort = SortOrder.NAME)).map { it.app.label },
        )
    }

    @Test
    fun summaryViewModelAndAppsViewModelAgreeOnTheNumbers() = runTest(dispatcher) {
        val settings = FakeSettings(dev.bastian.lupa.domain.Settings(includeSystemApps = false))
        val catalog = AppCatalog(FakeAppsSource(apps), settings, FakeClock(), AppDispatchers(dispatcher, dispatcher))
        val store = AppFilterStore()
        val summaryVm = SummaryViewModel(catalog, store, AppDispatchers(dispatcher, dispatcher))
        val appsVm = AppsViewModel(catalog, store, settings, AppDispatchers(dispatcher, dispatcher))

        summaryVm.state.test {
            val summary = assertNotNull(expectMostRecentItem().summary)
            assertEquals(5, summary.total) // the system app is left out by the setting

            summary.categories.forEach { card ->
                store.show(AppFilter(category = card.category))
                appsVm.state.test {
                    assertEquals(card.withAccess, expectMostRecentItem().apps.size, card.category.name)
                    cancelAndIgnoreRemainingEvents()
                }
            }
            RiskLevel.entries.forEach { level ->
                summaryVm.showRisk(level)
                appsVm.state.test {
                    assertEquals(summary.byRisk.getValue(level), expectMostRecentItem().apps.size, level.name)
                    cancelAndIgnoreRemainingEvents()
                }
            }
            summaryVm.showOldTargets()
            appsVm.state.test {
                assertEquals(summary.oldTargetApps, expectMostRecentItem().apps.size)
                cancelAndIgnoreRemainingEvents()
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun includingSystemAppsChangesTheSummaryAndTheListTogether() = runTest(dispatcher) {
        val settings = FakeSettings()
        val catalog = AppCatalog(FakeAppsSource(apps), settings, FakeClock(), AppDispatchers(dispatcher, dispatcher))
        val store = AppFilterStore()
        val summaryVm = SummaryViewModel(catalog, store, AppDispatchers(dispatcher, dispatcher))
        val appsVm = AppsViewModel(catalog, store, settings, AppDispatchers(dispatcher, dispatcher))
        settings.update { it.copy(includeSystemApps = true) }
        summaryVm.state.test {
            val summary = assertNotNull(expectMostRecentItem().summary)
            assertEquals(6, summary.total)
            appsVm.state.test {
                assertEquals(summary.total, expectMostRecentItem().apps.size)
                cancelAndIgnoreRemainingEvents()
            }
            cancelAndIgnoreRemainingEvents()
        }
    }
}
