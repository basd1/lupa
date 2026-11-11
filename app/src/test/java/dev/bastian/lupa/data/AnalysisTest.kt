package dev.bastian.lupa.data

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import dev.bastian.lupa.FakeAppsSource
import dev.bastian.lupa.FakeClock
import dev.bastian.lupa.FakeNotifier
import dev.bastian.lupa.FakeSettings
import dev.bastian.lupa.FakeSnapshots
import dev.bastian.lupa.app
import dev.bastian.lupa.data.notify.AndroidChangeNotifier
import dev.bastian.lupa.data.work.AnalysisScheduler
import dev.bastian.lupa.data.work.AnalysisWorker
import dev.bastian.lupa.domain.AnalysisResult
import dev.bastian.lupa.domain.AnalysisRunner
import dev.bastian.lupa.domain.AppCatalog
import dev.bastian.lupa.domain.AppDispatchers
import dev.bastian.lupa.domain.Settings
import dev.bastian.lupa.granted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AnalysisTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val source = FakeAppsSource(listOf(app("a")))
    private val settings = FakeSettings()
    private val snapshots = FakeSnapshots()
    private val clock = FakeClock(1_000)
    private val notifier = FakeNotifier()
    private val dispatchers = AppDispatchers(Dispatchers.Unconfined, Dispatchers.Unconfined)
    private val runner = AnalysisRunner(AppCatalog(source, settings, clock, dispatchers), snapshots, settings, clock)

    private fun worker(attempt: Int = 0): AnalysisWorker {
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                AnalysisWorker(appContext, workerParameters, runner, settings, notifier)
        }
        return TestListenableWorkerBuilder.from(context, AnalysisWorker::class.java)
            .setWorkerFactory(factory)
            .setRunAttemptCount(attempt)
            .build()
    }

    private fun p(name: String) = "android.permission.$name"

    @Test
    fun `the first analysis stores a snapshot and has nothing to compare with`() = runTest {
        val result = runner.run()
        val outcome = assertIs<AnalysisResult.Done>(result).outcome
        assertNull(outcome.changes)
        assertEquals(1, snapshots.stored.size)
        assertEquals(false, outcome.worthNotifying)
    }

    @Test
    fun `the second analysis compares with the first`() = runTest {
        runner.run()
        clock.time = 2_000
        source.apps = listOf(app("a"), app("b"))
        val outcome = assertIs<AnalysisResult.Done>(runner.run()).outcome
        assertEquals(listOf("b"), assertNotNull(outcome.changes).newApps.map { it.packageName })
        assertEquals(2, snapshots.stored.size)
    }

    @Test
    fun `system apps are ignored in the comparison unless the setting includes them`() = runTest {
        source.apps = listOf(app("a"))
        runner.run()
        clock.time = 2_000
        source.apps = listOf(app("a"), app("sys", isSystem = true))
        val hidden = assertIs<AnalysisResult.Done>(runner.run()).outcome
        assertTrue(assertNotNull(hidden.changes).isEmpty)

        settings.update { it.copy(includeSystemApps = true) }
        clock.time = 3_000
        source.apps = listOf(app("a"), app("sys", isSystem = true), app("sys2", isSystem = true))
        val shown = assertIs<AnalysisResult.Done>(runner.run()).outcome
        assertEquals(listOf("sys2"), assertNotNull(shown.changes).newApps.map { it.packageName })
    }

    @Test
    fun `a failing scan is reported and nothing is stored`() = runTest {
        source.error = IllegalStateException("denied")
        val result = runner.run()
        assertEquals("denied", assertIs<AnalysisResult.Failed>(result).reason)
        assertTrue(snapshots.stored.isEmpty())
    }

    @Test
    fun `only the newest twenty snapshots are kept`() = runTest {
        repeat(25) {
            clock.time = 1_000L + it
            runner.run()
        }
        assertEquals(20, snapshots.stored.size)
        assertEquals(1_024L, snapshots.stored.first().takenAt)
        assertEquals(1_005L, snapshots.stored.last().takenAt)
    }

    @Test
    fun `a newly granted permission or a new high risk app is worth a notification and a new plain app is not`() = runTest {
        runner.run()
        clock.time = 2_000
        source.apps = listOf(app("a"), app("plain"))
        assertEquals(false, assertIs<AnalysisResult.Done>(runner.run()).outcome.worthNotifying)

        clock.time = 3_000
        source.apps = listOf(app("a", permissions = listOf(granted(p("CAMERA")))), app("plain"))
        assertEquals(true, assertIs<AnalysisResult.Done>(runner.run()).outcome.worthNotifying)

        clock.time = 4_000
        source.apps = source.apps + app(
            "risky",
            installer = null,
            targetSdk = 21,
            declaresAccessibilityService = true,
            permissions = listOf(granted(p("READ_SMS")), granted(p("ACCESS_BACKGROUND_LOCATION")), granted(p("SYSTEM_ALERT_WINDOW"))),
        )
        val outcome = assertIs<AnalysisResult.Done>(runner.run()).outcome
        assertEquals(listOf("risky"), outcome.newHighRiskApps.map { it.packageName })
        assertEquals(true, outcome.worthNotifying)
    }

    @Test
    fun `the worker notifies when something worth it changed`() = runTest {
        runner.run()
        clock.time = 2_000
        source.apps = listOf(app("a", permissions = listOf(granted(p("RECORD_AUDIO")))))

        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertEquals(1, notifier.outcomes.size)
        assertEquals(2, snapshots.stored.size)
    }

    @Test
    fun `the worker stays quiet when nothing changed or notifications are off`() = runTest {
        runner.run()
        clock.time = 2_000
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertTrue(notifier.outcomes.isEmpty())

        clock.time = 3_000
        settings.update { it.copy(notifyOnChanges = false) }
        source.apps = listOf(app("a", permissions = listOf(granted(p("RECORD_AUDIO")))))
        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertTrue(notifier.outcomes.isEmpty())
    }

    @Test
    fun `the worker retries a failed scan a couple of times and then gives up until the next period`() = runTest {
        source.error = IllegalStateException("denied")
        assertTrue(worker(attempt = 0).doWork() is ListenableWorker.Result.Retry)
        assertTrue(worker(attempt = AnalysisWorker.MAX_ATTEMPTS - 1).doWork() is ListenableWorker.Result.Failure)
    }

    @Test
    fun `the schedule follows the settings`() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val manager = WorkManager.getInstance(context)
        val scheduler = AnalysisScheduler(manager)
        fun infos() = manager.getWorkInfosForUniqueWork(AnalysisWorker.UNIQUE_NAME).get()

        scheduler.apply(Settings(periodicAnalysis = false))
        assertTrue(infos().isEmpty())

        scheduler.apply(Settings(periodicAnalysis = true, periodHours = 24))
        assertEquals(TimeUnit.HOURS.toMillis(24), infos().single().periodicityInfo!!.repeatIntervalMillis)

        scheduler.apply(Settings(periodicAnalysis = true, periodHours = 48))
        val active = infos().filterNot { it.state.isFinished }
        assertEquals(1, active.size)
        assertEquals(TimeUnit.HOURS.toMillis(48), active.single().periodicityInfo!!.repeatIntervalMillis)

        scheduler.apply(Settings(periodicAnalysis = false))
        assertTrue(infos().all { it.state.isFinished })
    }

    @Test
    fun `the notification opens the changes screen and needs the permission`() = runTest {
        val application = context as Application
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runner.run()
        clock.time = 2_000
        source.apps = listOf(app("a", permissions = listOf(granted(p("CAMERA")))), app("b"))
        val outcome = assertIs<AnalysisResult.Done>(runner.run()).outcome

        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        AndroidChangeNotifier(context).notifyChanges(outcome)
        // Robolectric runs this test at API 34, where the runtime permission applies.
        assertTrue(shadowOf(manager).allNotifications.isEmpty())

        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        AndroidChangeNotifier(context).notifyChanges(outcome)
        val posted = shadowOf(manager).allNotifications.single()
        val intent = shadowOf(posted.contentIntent).savedIntent
        assertEquals(true, intent.getBooleanExtra(dev.bastian.lupa.MainActivity.EXTRA_OPEN_CHANGES, false))
    }
}
