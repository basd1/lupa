package dev.bastian.lupa.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class AppKind { ALL, USER, SYSTEM }

enum class SortOrder { RISK, NAME, LAST_UPDATED }

/** Everything the list can be narrowed by. The summary numbers are defined with the same functions. */
data class AppFilter(
    val query: String = "",
    val kind: AppKind = AppKind.ALL,
    /** Apps that have this access (granted, or declared for accessibility and device admin). */
    val category: PermissionCategory? = null,
    val risk: RiskLevel? = null,
    /** Apps whose target API level adds points to the score. */
    val oldTargetOnly: Boolean = false,
    val sort: SortOrder = SortOrder.RISK,
) {
    val hasNarrowing: Boolean
        get() = query.isNotBlank() || kind != AppKind.ALL || category != null || risk != null || oldTargetOnly
}

fun AppAssessment.hasAccess(category: PermissionCategory): Boolean = permissions.isActive(category)

fun AppAssessment.hasOldTarget(): Boolean = RiskWeights.oldTargetSdkPoints(app.targetSdk) > 0

fun List<AppAssessment>.filtered(filter: AppFilter): List<AppAssessment> {
    val needle = filter.query.trim()
    val matching = filter { item ->
        val app = item.app
        val kindMatches = when (filter.kind) {
            AppKind.ALL -> true
            AppKind.USER -> !app.isSystem
            AppKind.SYSTEM -> app.isSystem
        }
        kindMatches &&
            (filter.category == null || item.hasAccess(filter.category)) &&
            (filter.risk == null || item.score.level == filter.risk) &&
            (!filter.oldTargetOnly || item.hasOldTarget()) &&
            (needle.isEmpty() ||
                app.label.contains(needle, ignoreCase = true) ||
                app.packageName.contains(needle, ignoreCase = true))
    }
    val byName = compareBy<AppAssessment> { it.app.label.lowercase() }.thenBy { it.app.packageName }
    return when (filter.sort) {
        SortOrder.NAME -> matching.sortedWith(byName)
        SortOrder.RISK -> matching.sortedWith(compareByDescending<AppAssessment> { it.score.value }.then(byName))
        SortOrder.LAST_UPDATED -> matching.sortedWith(compareByDescending<AppAssessment> { it.app.lastUpdateTime }.then(byName))
    }
}

/** The list filter, shared so a card in the summary can open the list already narrowed. */
class AppFilterStore {
    private val mutableFilter = MutableStateFlow(AppFilter())
    val filter: StateFlow<AppFilter> = mutableFilter

    fun update(transform: (AppFilter) -> AppFilter) = mutableFilter.update(transform)

    /** Replaces the narrowing but keeps the chosen sort order. */
    fun show(filter: AppFilter) = mutableFilter.update { filter.copy(sort = it.sort) }
}

data class CategoryCount(
    val category: PermissionCategory,
    /** Apps that have this access. */
    val withAccess: Int,
    /** Apps that ask for it but do not have it granted. */
    val requestedOnly: Int,
)

data class Summary(
    val total: Int,
    val userApps: Int,
    val systemApps: Int,
    val byRisk: Map<RiskLevel, Int>,
    /** Only categories at least one app is involved with, most used first. */
    val categories: List<CategoryCount>,
    val oldTargetApps: Int,
)

object SummaryCalculator {
    fun of(apps: List<AppAssessment>): Summary {
        val categories = PermissionCategory.entries.mapNotNull { category ->
            val withAccess = apps.count { it.hasAccess(category) }
            val requestedOnly = apps.count { item ->
                item.permissions.status(category) == AccessStatus.REQUESTED
            }
            if (withAccess + requestedOnly == 0) null else CategoryCount(category, withAccess, requestedOnly)
        }.sortedByDescending { it.withAccess }
        return Summary(
            total = apps.size,
            userApps = apps.count { !it.app.isSystem },
            systemApps = apps.count { it.app.isSystem },
            byRisk = RiskLevel.entries.associateWith { level -> apps.count { it.score.level == level } },
            categories = categories,
            oldTargetApps = apps.count { it.hasOldTarget() },
        )
    }
}
