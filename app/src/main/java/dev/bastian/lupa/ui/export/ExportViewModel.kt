package dev.bastian.lupa.ui.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bastian.lupa.domain.AppCatalog
import dev.bastian.lupa.domain.AppDispatchers
import dev.bastian.lupa.domain.Clock
import dev.bastian.lupa.domain.ExportFormat
import dev.bastian.lupa.domain.ExportResult
import dev.bastian.lupa.domain.Report
import dev.bastian.lupa.domain.ReportExporter
import dev.bastian.lupa.domain.ReportSink
import dev.bastian.lupa.domain.StagedShare
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

sealed interface ExportStatus {
    data object Idle : ExportStatus
    data object Working : ExportStatus
    data object Saved : ExportStatus
    data class Failed(val reason: String) : ExportStatus
}

data class ExportUiState(
    val format: ExportFormat = ExportFormat.JSON,
    val status: ExportStatus = ExportStatus.Idle,
    /** Set when a file is ready to be handed to the share sheet; the screen reports back with [ExportViewModel.shareHandled]. */
    val pendingShare: PendingShare? = null,
)

data class PendingShare(val uri: String, val mimeType: String)

class ExportViewModel(
    private val catalog: AppCatalog,
    private val sink: ReportSink,
    private val clock: Clock,
    private val dispatchers: AppDispatchers,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val mutableState = MutableStateFlow(ExportUiState())
    val state: StateFlow<ExportUiState> = mutableState

    init {
        if (catalog.state.value.scannedAt == null) viewModelScope.launch { catalog.refresh() }
    }

    fun selectFormat(format: ExportFormat) = mutableState.update { it.copy(format = format, status = ExportStatus.Idle) }

    /** The suggested name for the file, with the time of the export. */
    fun fileName(format: ExportFormat = mutableState.value.format): String {
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").withZone(zone).format(Instant.ofEpochMilli(clock.now()))
        return "lupa-report-$stamp.${format.extension}"
    }

    /** Writes the report to the document the user chose in the system file dialog; null means they cancelled. */
    fun saveTo(destination: String?) {
        if (destination == null) return
        launchExport { content -> sink.saveTo(destination, content).toStatus() }
    }

    fun share() {
        val format = mutableState.value.format
        launchExport { content ->
            when (val staged = sink.stageForShare(fileName(format), content)) {
                is StagedShare.Ready -> {
                    mutableState.update { it.copy(pendingShare = PendingShare(staged.uri, format.mimeType)) }
                    ExportStatus.Idle
                }
                is StagedShare.Failed -> ExportStatus.Failed(staged.reason)
            }
        }
    }

    fun shareHandled() = mutableState.update { it.copy(pendingShare = null) }

    private fun launchExport(deliver: suspend (String) -> ExportStatus) {
        if (mutableState.value.status == ExportStatus.Working) return
        val format = mutableState.value.format
        mutableState.update { it.copy(status = ExportStatus.Working) }
        viewModelScope.launch {
            val apps = catalog.visibleApps.first()
            // Rendering a few hundred apps is CPU work; the sink does its own I/O.
            val content = withContext(dispatchers.default) {
                ReportExporter.render(Report.of(apps, clock.now()), format)
            }
            val status = deliver(content)
            mutableState.update { it.copy(status = status) }
        }
    }

    private fun ExportResult.toStatus(): ExportStatus = when (this) {
        ExportResult.Saved -> ExportStatus.Saved
        is ExportResult.Failed -> ExportStatus.Failed(reason)
    }
}
