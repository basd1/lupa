package dev.bastian.lupa.domain

import kotlinx.coroutines.flow.first

/** What an analysis found compared with the one before it. */
data class AnalysisOutcome(
    val snapshotId: Long,
    /** Null for the very first analysis, which has nothing to compare with. */
    val changes: SnapshotChanges?,
    /** Apps that appeared since the previous analysis and score as high risk. */
    val newHighRiskApps: List<InstalledApp>,
) {
    /** Worth interrupting the user: a permission was granted, or a risky app showed up. */
    val worthNotifying: Boolean
        get() = changes != null &&
            (changes.newlyGranted.isNotEmpty() || changes.newPermissions.any { it.granted } || newHighRiskApps.isNotEmpty())
}

sealed interface AnalysisResult {
    data class Done(val outcome: AnalysisOutcome) : AnalysisResult
    data class Failed(val reason: String) : AnalysisResult
}

/** Scans the device, stores a snapshot and compares it with the previous one. */
class AnalysisRunner(
    private val catalog: AppCatalog,
    private val snapshots: SnapshotRepository,
    private val settings: SettingsStore,
    private val clock: Clock,
) {
    suspend fun run(): AnalysisResult {
        val previous = snapshots.latest()
        val scan = catalog.refresh(waitIfBusy = true)
            ?: return AnalysisResult.Failed(catalog.state.value.error ?: "The scan did not finish")
        val includeSystem = settings.settings.first().includeSystemApps
        val id = snapshots.save(clock.now(), scan.apps)
        val changes = previous?.let { SnapshotDiff.compare(it.apps.visible(includeSystem), scan.apps.visible(includeSystem)) }
        val highRisk = changes?.newApps.orEmpty().filter { AppAssessment.of(it).score.level == RiskLevel.HIGH }
        return AnalysisResult.Done(AnalysisOutcome(id, changes, highRisk))
    }
}

/** Shows the result of a background analysis to the user. */
interface ChangeNotifier {
    fun notifyChanges(outcome: AnalysisOutcome)
}
