package dev.bastian.lupa.domain

/** Where a rendered report goes. Locations are URI strings so the domain stays free of Android types. */
interface ReportSink {
    /** Writes [content] to a document the user picked with the system file dialog. */
    suspend fun saveTo(destination: String, content: String): ExportResult

    /** Stores [content] where a share target can read it and returns the shareable URI. */
    suspend fun stageForShare(fileName: String, content: String): StagedShare
}

sealed interface ExportResult {
    data object Saved : ExportResult
    data class Failed(val reason: String) : ExportResult
}

sealed interface StagedShare {
    data class Ready(val uri: String) : StagedShare
    data class Failed(val reason: String) : StagedShare
}
