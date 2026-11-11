package dev.bastian.lupa.domain

import dev.bastian.lupa.app
import dev.bastian.lupa.granted
import dev.bastian.lupa.requested
import kotlinx.serialization.SerializationException
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReportExporterTest {
    private fun p(name: String) = "android.permission.$name"

    private val nasty = "Evil <b>\"App\"</b> & 'Co', Ltd"

    private val apps = listOf(
        app("com.plain", "Plain", permissions = listOf(granted(p("CAMERA")), requested(p("RECORD_AUDIO")))),
        app("com.nasty", nasty, installer = null, permissions = listOf(granted(p("READ_SMS")))),
        app("com.unicode", "Cámara ✓ 日本語", isSystem = true),
    ).map(AppAssessment::of)

    private val report = Report.of(apps, generatedAtMillis = 1_700_000_000_000)

    @Test
    fun reportRecordsTheTimeAndTheNumberOfApps() {
        assertEquals("2023-11-14T22:13:20Z", report.generatedAt)
        assertEquals(3, report.appCount)
        assertEquals(Report.FORMAT_VERSION, report.formatVersion)
    }

    @Test
    fun jsonCanBeReadBackIntoTheSameReport() {
        val text = ReportExporter.toJson(report)
        assertEquals(report, ReportExporter.fromJson(text))
    }

    @Test
    fun jsonKeepsSpecialCharactersAndNullsThroughTheRoundTrip() {
        val back = ReportExporter.fromJson(ReportExporter.toJson(report))
        assertEquals(nasty, back.apps.first { it.packageName == "com.nasty" }.label)
        assertEquals(null, back.apps.first { it.packageName == "com.nasty" }.installer)
        assertEquals("Cámara ✓ 日本語", back.apps.first { it.packageName == "com.unicode" }.label)
    }

    @Test
    fun jsonRoundTripKeepsScoresAccessAndPermissions() {
        val back = ReportExporter.fromJson(ReportExporter.toJson(report))
        val plain = back.apps.first { it.packageName == "com.plain" }
        assertEquals(apps[0].score.value, plain.score)
        assertEquals(apps[0].score.level, plain.level)
        assertEquals(listOf(AccessStatus.GRANTED, AccessStatus.REQUESTED), plain.access.map { it.status })
        assertEquals(apps[0].app.permissions, plain.permissions)
        assertTrue(plain.scoreItems.any { it.reason == "granted:CAMERA" })
    }

    @Test
    fun textThatIsNotAReportIsRejected() {
        assertFailsWith<SerializationException> { ReportExporter.fromJson("{\"nope\": true}") }
        assertFailsWith<SerializationException> { ReportExporter.fromJson("not json") }
    }

    @Test
    fun csvQuotesFieldsWithCommasQuotesAndLineBreaks() {
        assertEquals("plain", ReportExporter.csvField("plain"))
        assertEquals("\"a,b\"", ReportExporter.csvField("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", ReportExporter.csvField("say \"hi\""))
        assertEquals("\"line1\nline2\"", ReportExporter.csvField("line1\nline2"))
        assertEquals("\"a\r\nb\"", ReportExporter.csvField("a\r\nb"))
        assertEquals("", ReportExporter.csvField(""))
    }

    @Test
    fun csvFieldsThatLookLikeFormulasAreNeutralised() {
        assertEquals("'=SUM(A1)", ReportExporter.csvField("=SUM(A1)"))
        assertEquals("'+1", ReportExporter.csvField("+1"))
        assertEquals("'@cmd", ReportExporter.csvField("@cmd"))
        assertEquals("'-2", ReportExporter.csvField("-2"))
        assertEquals("a=b", ReportExporter.csvField("a=b"))
    }

    @Test
    fun csvCanBeParsedBackToTheSameFieldsEvenWithHostileNames() {
        val rows = parseCsv(ReportExporter.toCsv(report))
        assertEquals(4, rows.size) // header and three apps
        val header = rows.first()
        assertTrue(rows.all { it.size == header.size }, "every row has the same number of columns")
        val nastyRow = rows.first { it[0] == "com.nasty" }
        assertEquals(nasty, nastyRow[header.indexOf("name")])
        assertEquals("", nastyRow[header.indexOf("installer")])
        assertEquals("UNKNOWN", nastyRow[header.indexOf("origin")])
        val plainRow = rows.first { it[0] == "com.plain" }
        assertEquals("CAMERA", plainRow[header.indexOf("active_categories")])
        assertEquals("MICROPHONE", plainRow[header.indexOf("requested_only_categories")])
        assertEquals("1", plainRow[header.indexOf("permissions_granted")])
        assertEquals("2", plainRow[header.indexOf("permissions_requested")])
    }

    @Test
    fun csvUsesCrLfLineEndingsAndOneRowPerApp() {
        val csv = ReportExporter.toCsv(report)
        assertTrue(csv.endsWith("\r\n"))
        assertEquals(4, csv.split("\r\n").filter { it.isNotEmpty() }.size)
    }

    @Test
    fun htmlEscapesAppNamesAndPackages() {
        val html = ReportExporter.toHtml(report)
        assertFalse(html.contains(nasty), "the raw name must not appear")
        assertTrue(html.contains("Evil &lt;b&gt;&quot;App&quot;&lt;/b&gt; &amp; &#39;Co&#39;, Ltd"))
        assertFalse(html.contains("<b>"))
    }

    @Test
    fun htmlEscapesEveryDangerousCharacter() {
        assertEquals("&lt;script&gt;alert(1)&lt;/script&gt;", ReportExporter.escapeHtml("<script>alert(1)</script>"))
        assertEquals("a &amp; b", ReportExporter.escapeHtml("a & b"))
        assertEquals("&quot;x&quot; &#39;y&#39;", ReportExporter.escapeHtml("\"x\" 'y'"))
        assertEquals("&amp;amp;", ReportExporter.escapeHtml("&amp;"))
        assertEquals("plain ✓", ReportExporter.escapeHtml("plain ✓"))
    }

    @Test
    fun htmlIsSelfContainedWithoutScriptsOrExternalResources() {
        val html = ReportExporter.toHtml(report)
        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue(html.contains("<meta charset=\"utf-8\">"))
        assertFalse(html.contains("<script", ignoreCase = true))
        assertFalse(html.contains("<link", ignoreCase = true))
        assertFalse(html.contains("http://") || html.contains("https://"))
        assertTrue(html.contains("<style>"))
    }

    @Test
    fun htmlListsTheAppsFromTheRiskiestDown() {
        val html = ReportExporter.toHtml(report)
        val smsApp = html.indexOf("com.nasty")
        val plain = html.indexOf("com.plain")
        assertTrue(smsApp in 0 until plain, "the app with the higher score comes first")
    }

    @Test
    fun anEmptyReportIsStillValidInEveryFormat() {
        val empty = Report.of(emptyList(), 0)
        assertEquals(empty, ReportExporter.fromJson(ReportExporter.toJson(empty)))
        assertEquals(1, parseCsv(ReportExporter.toCsv(empty)).size)
        assertTrue(ReportExporter.toHtml(empty).contains("0 apps"))
    }

    @Test
    fun renderPicksTheFormat() {
        assertTrue(ReportExporter.render(report, ExportFormat.JSON).trimStart().startsWith("{"))
        assertTrue(ReportExporter.render(report, ExportFormat.CSV).startsWith("package,name"))
        assertTrue(ReportExporter.render(report, ExportFormat.HTML).startsWith("<!DOCTYPE html>"))
    }
}

/** A small RFC 4180 reader, so the tests check the CSV by what a spreadsheet would read, not by string shape. */
internal fun parseCsv(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        when {
            quoted && ch == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
            quoted && ch == '"' -> quoted = false
            quoted -> field.append(ch)
            ch == '"' -> quoted = true
            ch == ',' -> { row += field.toString(); field.clear() }
            ch == '\r' && text.getOrNull(i + 1) == '\n' -> {
                row += field.toString(); field.clear(); rows += row; row = mutableListOf(); i++
            }
            else -> field.append(ch)
        }
        i++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row }
    return rows
}
