package dev.bastian.lupa.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.bastian.lupa.data.export.AndroidReportSink
import dev.bastian.lupa.domain.AppDispatchers
import dev.bastian.lupa.domain.ExportResult
import dev.bastian.lupa.domain.StagedShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs

@RunWith(RobolectricTestRunner::class)
class AndroidReportSinkTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    // Robolectric on Windows cannot resolve FileProvider roots, so the URI step is replaced here; the real
    // provider is exercised by sharing a report on the emulator.
    private val sink = AndroidReportSink(
        context,
        AppDispatchers(Dispatchers.Unconfined, Dispatchers.Unconfined),
        shareUri = { "content://share/${it.name}" },
    )

    @Test
    fun `saving writes the text as UTF-8 and replaces the previous content`() = runTest {
        val file = File(context.cacheDir, "target.txt").apply { writeText("a much longer previous content that must disappear") }

        val result = sink.saveTo(Uri.fromFile(file).toString(), "Cámara ✓")

        assertEquals(ExportResult.Saved, result)
        assertEquals("Cámara ✓", file.readText(Charsets.UTF_8))
    }

    @Test
    fun `saving to a place that cannot be written reports a failure`() = runTest {
        val missing = File(context.cacheDir, "no-such-dir/report.json")

        val result = sink.saveTo(Uri.fromFile(missing).toString(), "{}")

        assertIs<ExportResult.Failed>(result)
    }

    @Test
    fun `staging for share writes the file under exports and keeps only the latest report`() = runTest {
        val first = sink.stageForShare("one.csv", "a,b")
        val second = sink.stageForShare("two.csv", "c,d")

        assertIs<StagedShare.Ready>(first)
        assertEquals("content://share/two.csv", assertIs<StagedShare.Ready>(second).uri)
        val dir = File(context.cacheDir, "exports")
        assertEquals(listOf("two.csv"), dir.list()!!.toList())
        assertEquals("c,d", File(dir, "two.csv").readText())
    }
}
