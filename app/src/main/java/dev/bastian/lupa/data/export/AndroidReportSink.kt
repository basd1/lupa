package dev.bastian.lupa.data.export

import android.content.Context
import androidx.core.net.toUri
import androidx.core.content.FileProvider
import dev.bastian.lupa.domain.AppDispatchers
import dev.bastian.lupa.domain.ExportResult
import dev.bastian.lupa.domain.ReportSink
import dev.bastian.lupa.domain.StagedShare
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class AndroidReportSink(
    private val context: Context,
    private val dispatchers: AppDispatchers,
    /** Turns a staged file into a URI other apps can read; the FileProvider of this app unless a test replaces it. */
    private val shareUri: (File) -> String = { file ->
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString()
    },
) : ReportSink {

    override suspend fun saveTo(destination: String, content: String): ExportResult = withContext(dispatchers.io) {
        try {
            // "wt" truncates: saving over an existing, longer file must not leave its tail behind.
            val stream = context.contentResolver.openOutputStream(destination.toUri(), "wt")
                ?: return@withContext ExportResult.Failed("The destination could not be opened")
            stream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            ExportResult.Saved
        } catch (e: IOException) {
            ExportResult.Failed(e.message ?: e.javaClass.simpleName)
        } catch (e: SecurityException) {
            ExportResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    override suspend fun stageForShare(fileName: String, content: String): StagedShare = withContext(dispatchers.io) {
        try {
            val directory = context.cacheDir.resolve(EXPORT_DIR)
            // Only the report being shared is kept; earlier ones are not needed any more.
            directory.deleteRecursively()
            directory.mkdirs()
            val file = directory.resolve(fileName)
            file.writeText(content, Charsets.UTF_8)
            StagedShare.Ready(shareUri(file))
        } catch (e: IOException) {
            StagedShare.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private companion object {
        const val EXPORT_DIR = "exports"
    }
}
