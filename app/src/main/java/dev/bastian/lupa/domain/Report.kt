package dev.bastian.lupa.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

enum class ExportFormat(val extension: String, val mimeType: String) {
    JSON("json", "application/json"),
    CSV("csv", "text/csv"),
    HTML("html", "text/html"),
}

@Serializable
data class ReportScoreItem(val reason: String, val points: Int)

@Serializable
data class ReportAccess(val category: PermissionCategory, val status: AccessStatus)

@Serializable
data class ReportApp(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val system: Boolean,
    val targetSdk: Int,
    val minSdk: Int,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val installer: String?,
    val origin: InstallOrigin,
    val score: Int,
    val level: RiskLevel,
    val scoreItems: List<ReportScoreItem>,
    val access: List<ReportAccess>,
    val permissions: List<RequestedPermission>,
    val exportedComponents: ExportedComponents,
)

/** The content of an exported report, independent of the file format. */
@Serializable
data class Report(
    val formatVersion: Int = FORMAT_VERSION,
    /** ISO 8601, UTC. */
    val generatedAt: String,
    val appCount: Int,
    val apps: List<ReportApp>,
) {
    companion object {
        const val FORMAT_VERSION = 1

        fun of(apps: List<AppAssessment>, generatedAtMillis: Long): Report = Report(
            generatedAt = Instant.ofEpochMilli(generatedAtMillis).toString(),
            appCount = apps.size,
            apps = apps.map { it.toReportApp() },
        )
    }
}

private fun AppAssessment.toReportApp() = ReportApp(
    packageName = app.packageName,
    label = app.label,
    versionName = app.versionName,
    versionCode = app.versionCode,
    system = app.isSystem,
    targetSdk = app.targetSdk,
    minSdk = app.minSdk,
    firstInstallTime = app.firstInstallTime,
    lastUpdateTime = app.lastUpdateTime,
    installer = app.installer,
    origin = origin,
    score = score.value,
    level = score.level,
    scoreItems = score.items.map { ReportScoreItem(it.reason.key(), it.points) },
    access = permissions.categories.map { ReportAccess(it.category, it.status) },
    permissions = app.permissions,
    exportedComponents = app.exported,
)

/** A stable, language-independent name for a score reason. */
fun ScoreReason.key(): String = when (this) {
    is ScoreReason.Granted -> "granted:${category.name}"
    is ScoreReason.RequestedOnly -> "requested_only:${category.name}"
    is ScoreReason.OldTargetSdk -> "old_target_sdk:$targetSdk"
    is ScoreReason.UnverifiedOrigin -> "unverified_origin:${origin.name}"
    is ScoreReason.Together -> "together:${combination.name}"
}

object ReportExporter {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun render(report: Report, format: ExportFormat): String = when (format) {
        ExportFormat.JSON -> toJson(report)
        ExportFormat.CSV -> toCsv(report)
        ExportFormat.HTML -> toHtml(report)
    }

    fun toJson(report: Report): String = json.encodeToString(Report.serializer(), report)

    /** Reads a JSON report back; throws [kotlinx.serialization.SerializationException] if it is not one. */
    fun fromJson(text: String): Report = json.decodeFromString(Report.serializer(), text)

    private val csvHeader = listOf(
        "package", "name", "version", "system", "target_sdk", "min_sdk", "installer", "origin", "score", "level",
        "active_categories", "requested_only_categories", "permissions_granted", "permissions_requested",
        "exported_components",
    )

    /** RFC 4180 CSV: fields with commas, quotes or line breaks are quoted and quotes are doubled. */
    fun toCsv(report: Report): String = buildString {
        append(csvHeader.joinToString(",")).append("\r\n")
        report.apps.forEach { app ->
            val active = app.access.filter { it.status != AccessStatus.REQUESTED }.joinToString(";") { it.category.name }
            val requestedOnly = app.access.filter { it.status == AccessStatus.REQUESTED }.joinToString(";") { it.category.name }
            val row = listOf(
                app.packageName,
                app.label,
                app.versionName.orEmpty(),
                app.system.toString(),
                app.targetSdk.toString(),
                app.minSdk.toString(),
                app.installer.orEmpty(),
                app.origin.name,
                app.score.toString(),
                app.level.name,
                active,
                requestedOnly,
                app.permissions.count { it.granted }.toString(),
                app.permissions.size.toString(),
                app.exportedComponents.total.toString(),
            )
            append(row.joinToString(",") { csvField(it) }).append("\r\n")
        }
    }

    /**
     * Quotes a field when needed. A text that a spreadsheet would read as a formula (it starts with = + - @) gets a
     * leading apostrophe so opening the report cannot run anything.
     */
    fun csvField(value: String): String {
        val safe = if (value.isNotEmpty() && value[0] in FORMULA_STARTS) "'$value" else value
        val needsQuotes = safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    private val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t')

    /** Escapes text for use inside HTML element content and double-quoted attributes. */
    fun escapeHtml(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(ch)
            }
        }
    }

    /** A single self-contained page: inline style, no scripts, no external requests. */
    fun toHtml(report: Report): String = buildString {
        fun e(text: String) = escapeHtml(text)
        append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n")
        append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        append("<title>Lupa report</title>\n<style>\n").append(HTML_STYLE).append("</style>\n</head>\n<body>\n")
        append("<h1>Lupa report</h1>\n")
        append("<p>Generated ").append(e(report.generatedAt)).append(" on the device. ")
        append(report.appCount).append(" apps. The score is indicative, not a verdict.</p>\n")
        append("<table>\n<thead><tr><th>App</th><th>Package</th><th>Score</th><th>Level</th><th>Access</th></tr></thead>\n<tbody>\n")
        report.apps.sortedByDescending { it.score }.forEach { app ->
            val active = app.access.filter { it.status != AccessStatus.REQUESTED }.joinToString(", ") { e(prettyName(it.category.name)) }
            append("<tr class=\"").append(app.level.name.lowercase()).append("\"><td>").append(e(app.label)).append("</td><td>")
            append(e(app.packageName)).append("</td><td>").append(app.score).append("</td><td>")
            append(app.level.name.lowercase()).append("</td><td>").append(active).append("</td></tr>\n")
        }
        append("</tbody>\n</table>\n<h2>Details</h2>\n")
        report.apps.sortedByDescending { it.score }.forEach { app ->
            append("<details><summary>").append(e(app.label)).append(" (").append(app.score).append(")</summary>\n")
            append("<p>").append(e(app.packageName)).append(" · version ").append(e(app.versionName.orEmpty()))
            append(" · target API ").append(app.targetSdk).append(" · origin ").append(e(prettyName(app.origin.name)))
            append(" · exported components ").append(app.exportedComponents.total).append("</p>\n")
            if (app.scoreItems.isNotEmpty()) {
                append("<ul>\n")
                app.scoreItems.forEach { append("<li>").append(e(it.reason)).append(" +").append(it.points).append("</li>\n") }
                append("</ul>\n")
            }
            if (app.permissions.isNotEmpty()) {
                append("<p class=\"perms\">")
                append(app.permissions.joinToString(", ") { e(it.name.substringAfterLast('.')) + if (it.granted) " (granted)" else "" })
                append("</p>\n")
            }
            append("</details>\n")
        }
        append("</body>\n</html>\n")
    }

    private fun prettyName(name: String): String =
        name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

    private const val HTML_STYLE = "body{font-family:sans-serif;margin:1.5rem;color:#1b1b1b}" +
        "table{border-collapse:collapse;width:100%;table-layout:fixed}th,td{border:1px solid #ccc;padding:4px 8px;text-align:left;font-size:14px;overflow-wrap:anywhere}" +
        "th:nth-child(1){width:18%}th:nth-child(2){width:26%}th:nth-child(3){width:7%}th:nth-child(4){width:8%}" +
        "tr.high td{background:#fbe0dd}tr.medium td{background:#efe8fb}tr.low td{background:#e3f0f8}" +
        "details{margin:6px 0}.perms{font-size:12px;color:#555;word-break:break-word}\n"
}
