package com.icehacks.saythegoal.agent.data

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.ZoneId

class RunExportTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: StgDatabase
    private var now = 1_762_700_000_000L // 2025-11-09T14:53:20Z
    private val exporter = RunExporter(ZoneId.of("UTC"))

    @Before fun open() { db = StgDatabase(BundledSQLiteDriver(), ":memory:") }
    @After fun close() = db.close()

    /** Demo 2 as the agent logs it: takeover, attach, undo, attach the other one, Send. */
    private fun demo2(logger: DbRunLogger) {
        fun tick(s: Long) { now += s * 1000 }
        logger.sessionStarted(SessionKind.GOAL, "Take over. Attach yesterday's dog photo.", takeOver = true)
        tick(2); logger.actionPerformed("tap", "Show attach media screen", true)
        tick(2); logger.actionPerformed("tap", "Dog in the yard", true)
        tick(1); logger.agentSaid("Attached the photo of the dog in the yard.")
        logger.sessionEnded("finished", "Attached the photo of the dog in the yard.")
        tick(3); logger.sessionStarted(SessionKind.UNDO, "Undo the photo.")
        tick(1); logger.actionPerformed("tap", "Remove image attachment", true)
        logger.sessionEnded("finished", "Removed the photo. Everything else is as it was.")
        tick(3); logger.sessionStarted(SessionKind.GOAL, "Use the one by the door, \"the brown one\".", takeOver = true)
        tick(2); logger.actionPerformed("tap", "Show attach media screen", true)
        tick(1); logger.actionPerformed("tap", "Dog by the door", false, "target not found: gone")
        tick(1); logger.actionPerformed("tap", "Dog by the door", true)
        tick(1); logger.questionAsked("Is this the one?", "Yes")
        tick(2); logger.approvalRequested("To Maya: Made it home!, with the photo by the door. Say Send or Cancel.", "Send", "APPROVED")
        tick(1); logger.actionPerformed("submit", "Send SMS", true)
        logger.sessionOutcome("done", "Sent to Maya.")
        logger.sessionEnded("finished", "Sent to Maya.")
    }

    @Test
    fun totalsCoverTheWholeRun() {
        val logger = DbRunLogger(db, { now })
        val start = now
        demo2(logger)
        val export = exporter.build(logger.sessions(), logger.events(), now)
        val t = export.totals
        assertEquals(3, t.sessions)
        assertEquals(7, t.actions)
        assertEquals(1, t.failedActions)
        assertEquals(5, t.spokenCommands) // three commands + "Yes" + "Send"
        assertEquals(1, t.questions)
        assertEquals(1, t.approvals)
        assertEquals(1, t.approvalsGranted)
        assertEquals(1, t.undos)
        assertEquals(0, t.userTouches)
        assertEquals(now - start, t.spanMs)
        assertEquals(5_000L + 1_000L + 8_000L, t.durationMs) // time inside the three sessions
        assertEquals("done", export.sessions.last().outcome)
        assertEquals("2025-11-09T14:53:20.000Z", export.sessions.first().startedAt)
    }

    @Test
    fun writesJsonAndCsvThatReadBack() {
        val logger = DbRunLogger(db, { now })
        demo2(logger)
        val export = exporter.build(logger.sessions(), logger.events(), now)
        val dir = File(tmp.root, "runs")
        val paths = exporter.write(export, DirectorySink(dir))
        assertEquals(3, paths.size)
        val names = dir.list()!!.sorted()
        assertTrue(names.toString(), names.any { it.matches(Regex("stg-runs-\\d{8}-\\d{6}\\.json")) })
        assertTrue(names.any { it.startsWith("stg-sessions-") && it.endsWith(".csv") })
        assertTrue(names.any { it.startsWith("stg-events-") && it.endsWith(".csv") })

        val json = File(dir, names.single { it.endsWith(".json") }).readText()
        val back = Json.decodeFromString(RunLogExport.serializer(), json)
        assertEquals(export, back)
        assertEquals(3, back.sessions.size)
        assertEquals(listOf(3, 1, 6), back.sessions.map { it.events.size })

        val sessionsCsv = File(dir, names.single { it.startsWith("stg-sessions-") }).readLines()
        assertEquals(5, sessionsCsv.size) // header, 3 sessions, TOTAL
        assertTrue(sessionsCsv[0].startsWith("session_id,kind,take_over,text,"))
        assertTrue(sessionsCsv[3], sessionsCsv[3].contains("\"Use the one by the door, \"\"the brown one\"\".\""))
        assertTrue(sessionsCsv[4].startsWith("TOTAL,3 sessions,"))
        assertTrue(sessionsCsv[4].endsWith(",7,1,5,1,1,1,1,0"))

        val eventsCsv = File(dir, names.single { it.startsWith("stg-events-") }).readLines()
        assertEquals(1 + 10, eventsCsv.size)
    }

    @Test
    fun csvQuoting() {
        assertEquals("plain", RunExporter.csvCell("plain"))
        assertEquals("\"a,b\"", RunExporter.csvCell("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", RunExporter.csvCell("say \"hi\""))
        assertEquals("\"two\nlines\"", RunExporter.csvCell("two\nlines"))
        assertEquals("", RunExporter.csvCell(null))
        assertEquals("1", RunExporter.csvCell(true))
    }

    @Test
    fun emptyLogExportsCleanly() {
        val export = exporter.build(emptyList(), emptyList(), now)
        assertEquals(0, export.totals.sessions)
        assertEquals(0L, export.totals.spanMs)
        assertEquals(2, exporter.sessionsCsv(export).trimEnd().lines().size) // header + TOTAL
        assertTrue(exporter.summaryLine(export.totals).startsWith("0 sessions"))
    }
}
