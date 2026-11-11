package dev.bastian.lupa.data.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.bastian.lupa.domain.AnalysisResult
import dev.bastian.lupa.domain.AnalysisRunner
import dev.bastian.lupa.domain.ChangeNotifier
import dev.bastian.lupa.domain.Settings
import dev.bastian.lupa.domain.SettingsStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** Runs an analysis in the background, stores it and tells the user when something sensitive changed. */
class AnalysisWorker(
    appContext: Context,
    params: WorkerParameters,
    private val runner: AnalysisRunner,
    private val settings: SettingsStore,
    private val notifier: ChangeNotifier,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = when (val result = runner.run()) {
        is AnalysisResult.Failed ->
            if (runAttemptCount + 1 >= MAX_ATTEMPTS) Result.failure() else Result.retry()
        is AnalysisResult.Done -> {
            if (result.outcome.worthNotifying && settings.settings.first().notifyOnChanges) {
                notifier.notifyChanges(result.outcome)
            }
            Result.success()
        }
    }

    companion object {
        const val UNIQUE_NAME = "lupa-periodic-analysis"
        const val MAX_ATTEMPTS = 3
    }
}

class AnalysisScheduler(private val workManager: WorkManager) {

    /** Makes the schedule match the settings: one periodic job, replaced when the period changes, or none. */
    fun apply(settings: Settings) {
        if (!settings.periodicAnalysis) {
            workManager.cancelUniqueWork(AnalysisWorker.UNIQUE_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<AnalysisWorker>(settings.periodHours.toLong(), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(AnalysisWorker.UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private companion object {
        const val BACKOFF_MINUTES = 15L
    }
}
