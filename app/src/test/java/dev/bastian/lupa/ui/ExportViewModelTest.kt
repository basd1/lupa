package dev.bastian.lupa.ui

import app.cash.turbine.test
import dev.bastian.lupa.FakeAppsSource
import dev.bastian.lupa.FakeClock
import dev.bastian.lupa.FakeSettings
import dev.bastian.lupa.app
import dev.bastian.lupa.domain.AppCatalog
import dev.bastian.lupa.domain.AppDispatchers
import dev.bastian.lupa.domain.ExportFormat
import dev.bastian.lupa.domain.ExportResult
import dev.bastian.lupa.domain.ReportExporter
import dev.bastian.lupa.domain.ReportSink
import dev.bastian.lupa.domain.StagedShare
import dev.bastian.lupa.ui.export.ExportStatus
import dev.bastian.lupa.ui.export.ExportViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeSink : ReportSink {
    val saved = mutableListOf<Pair<String, String>>()
    val staged = mutableListOf<Pair<String, String>>()
    var saveResult: ExportResult = ExportResult.Saved
    var stageFails = false

    override suspend fun saveTo(destination: String, content: String): ExportResult {
        saved += destination to content
        return saveResult
    }

    override suspend fun stageForShare(fileName: String, content: String): StagedShare {
        staged += fileName to content
        return if (stageFails) StagedShare.Failed("disk full") else StagedShare.Ready("content://x/$fileName")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val dispatchers = AppDispatchers(dispatcher, dispatcher)
    private val source = FakeAppsSource(listOf(app("com.user", "User app"), app("com.sys", "System app", isSystem = true)))
    private val settings = FakeSettings()
    // 2023-11-14 22:13:20 UTC
    private val clock = FakeClock(1_700_000_000_000)
    private val sink = FakeSink()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ExportViewModel(
        catalog = AppCatalog(source, settings, clock, dispatchers),
        sink = sink,
        clock = clock,
        dispatchers = dispatchers,
        zone = ZoneOffset.UTC,
    )

    @Test
    fun `the suggested file name carries the time and the extension of the format`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals("lupa-report-20231114-2213.json", vm.fileName())
        assertEquals("lupa-report-20231114-2213.csv", vm.fileName(ExportFormat.CSV))
        assertEquals("lupa-report-20231114-2213.html", vm.fileName(ExportFormat.HTML))
    }

    @Test
    fun `saving writes a readable JSON report with the visible apps to the chosen document`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.saveTo("content://docs/1")

        val (destination, content) = sink.saved.single()
        assertEquals("content://docs/1", destination)
        val report = ReportExporter.fromJson(content)
        assertEquals(listOf("com.user"), report.apps.map { it.packageName })
        assertEquals(ExportStatus.Saved, vm.state.value.status)
    }

    @Test
    fun `the system apps setting decides what is exported`() = runTest(dispatcher) {
        settings.update { it.copy(includeSystemApps = true) }
        val vm = viewModel()
        vm.saveTo("content://docs/1")
        assertEquals(2, ReportExporter.fromJson(sink.saved.single().second).appCount)
    }

    @Test
    fun `the chosen format decides the content`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.selectFormat(ExportFormat.CSV)
        vm.saveTo("content://docs/2")
        assertTrue(sink.saved.single().second.startsWith("package,name"))

        vm.selectFormat(ExportFormat.HTML)
        vm.saveTo("content://docs/3")
        assertTrue(sink.saved.last().second.startsWith("<!DOCTYPE html>"))
    }

    @Test
    fun `cancelling the file dialog does nothing`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.saveTo(null)
        assertTrue(sink.saved.isEmpty())
        assertEquals(ExportStatus.Idle, vm.state.value.status)
    }

    @Test
    fun `a write failure is shown and the screen keeps working`() = runTest(dispatcher) {
        sink.saveResult = ExportResult.Failed("read-only")
        val vm = viewModel()
        vm.saveTo("content://docs/1")
        assertEquals(ExportStatus.Failed("read-only"), vm.state.value.status)

        sink.saveResult = ExportResult.Saved
        vm.saveTo("content://docs/1")
        assertEquals(ExportStatus.Saved, vm.state.value.status)
    }

    @Test
    fun `sharing stages the file and asks the screen to open the share sheet once`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.selectFormat(ExportFormat.HTML)
        vm.state.test {
            expectMostRecentItem()
            vm.share()
            val pending = assertNotNull(expectMostRecentItem().pendingShare)
            assertEquals("text/html", pending.mimeType)
            assertEquals("content://x/lupa-report-20231114-2213.html", pending.uri)
            vm.shareHandled()
            assertNull(expectMostRecentItem().pendingShare)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failure while staging the file is reported`() = runTest(dispatcher) {
        sink.stageFails = true
        val vm = viewModel()
        vm.share()
        assertEquals(ExportStatus.Failed("disk full"), vm.state.value.status)
        assertNull(vm.state.value.pendingShare)
    }
}
