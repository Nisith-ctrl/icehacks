package com.icehacks.saythegoal.agent.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One exported file: name, MIME type, contents. */
data class ExportFile(val name: String, val mime: String, val content: String)

/** Where export files go: the Downloads folder on the phone, a plain directory in tests. */
fun interface ExportSink {
    /** Writes the file; returns where it ended up (a path to `adb pull`). */
    fun write(file: ExportFile): String
}

class DirectorySink(private val dir: File) : ExportSink {
    override fun write(file: ExportFile): String {
        dir.mkdirs()
        val f = File(dir, file.name)
        f.writeText(file.content)
        return f.path
    }
}

@Serializable
data class ExportedSession(
    val id: Long,
    val kind: String,
    val text: String,
    val takeOver: Boolean,
    val startedAtMs: Long,
    val startedAt: String,
    val endedAtMs: Long?,
    val outcome: String?,
    val outcomeDetail: String?,
    val summary: RunSummary,
    val events: List<RunEvent>,
)

@Serializable
data class RunLogExport(
    val exportedAtMs: Long,
    val exportedAt: String,
    val totals: RunSummary,
    val sessions: List<ExportedSession>,
)

/** Builds the T06 M3 export: one JSON file with everything, plus sessions and events as CSV. */
class RunExporter(private val zone: ZoneId = ZoneId.systemDefault()) {

    fun build(sessions: List<RunSession>, events: List<RunEvent>, nowMs: Long): RunLogExport = RunLogExport(
        exportedAtMs = nowMs,
        exportedAt = iso(nowMs),
        totals = RunSummaries.total(sessions, events),
        sessions = sessions.map { s ->
            ExportedSession(
                s.id, s.kind, s.text, s.takeOver, s.startedAtMs, iso(s.startedAtMs), s.endedAtMs,
                s.outcome, s.outcomeDetail, RunSummaries.of(s, events), events.filter { it.sessionId == s.id },
            )
        },
    )

    fun files(export: RunLogExport): List<ExportFile> {
        val stamp = FILE_STAMP.withZone(zone).format(Instant.ofEpochMilli(export.exportedAtMs))
        return listOf(
            ExportFile("stg-runs-$stamp.json", "application/json", JSON.encodeToString(RunLogExport.serializer(), export)),
            ExportFile("stg-sessions-$stamp.csv", "text/csv", sessionsCsv(export)),
            ExportFile("stg-events-$stamp.csv", "text/csv", eventsCsv(export)),
        )
    }

    fun write(export: RunLogExport, sink: ExportSink): List<String> = files(export).map { sink.write(it) }

    fun sessionsCsv(export: RunLogExport): String = buildString {
        appendRow(
            "session_id", "kind", "take_over", "text", "started_at", "ended_at", "duration_s", "outcome", "outcome_detail",
            "actions", "failed_actions", "spoken_commands", "questions", "approvals", "approvals_granted", "undos", "user_touches",
        )
        for (s in export.sessions) {
            val m = s.summary
            appendRow(
                s.id, s.kind, s.takeOver, s.text, s.startedAt, s.endedAtMs?.let { iso(it) }, seconds(m.durationMs),
                s.outcome, s.outcomeDetail, m.actions, m.failedActions, m.spokenCommands, m.questions, m.approvals,
                m.approvalsGranted, m.undos, m.userTouches,
            )
        }
        val t = export.totals
        appendRow(
            "TOTAL", "${t.sessions} sessions", "", "", "", "", seconds(t.durationMs), "", "span ${seconds(t.spanMs)} s",
            t.actions, t.failedActions, t.spokenCommands, t.questions, t.approvals, t.approvalsGranted, t.undos, t.userTouches,
        )
    }

    fun eventsCsv(export: RunLogExport): String = buildString {
        appendRow("event_id", "session_id", "at", "type", "name", "label", "detail", "ok")
        for (e in export.sessions.flatMap { it.events }) {
            appendRow(e.id, e.sessionId, iso(e.atMs), e.type, e.name, e.label, e.detail, e.ok)
        }
    }

    /** One line for Logcat: the totals. */
    fun summaryLine(t: RunSummary) =
        "${t.sessions} sessions, ${t.actions} actions (${t.failedActions} failed), ${t.spokenCommands} spoken commands, " +
            "${t.questions} questions, ${t.approvals} approvals (${t.approvalsGranted} granted), ${t.undos} undos, " +
            "${t.userTouches} user touches, ${seconds(t.durationMs)} s in sessions, ${seconds(t.spanMs)} s first to last"

    private fun iso(ms: Long) = ISO_MS.format(Instant.ofEpochMilli(ms).atZone(zone))

    private fun seconds(ms: Long) = String.format(java.util.Locale.ROOT, "%.1f", ms / 1000.0)

    private fun StringBuilder.appendRow(vararg cells: Any?) {
        append(cells.joinToString(",") { csvCell(it) }).append("\r\n")
    }

    companion object {
        private val JSON = Json { prettyPrint = true; encodeDefaults = true }
        private val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        /** ISO 8601 with fixed milliseconds, e.g. 2026-11-10T09:05:11.494-05:00 (sorts and parses in spreadsheets). */
        private val ISO_MS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

        fun csvCell(v: Any?): String {
            val s = when (v) {
                null -> ""
                is Boolean -> if (v) "1" else "0"
                else -> v.toString()
            }
            return if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
        }
    }
}
