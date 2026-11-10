package com.icehacks.suus.agent.data

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.icehacks.suus.agent.FilePreferenceStore
import com.icehacks.suus.agent.ObservablePreferenceStore
import java.io.File

/**
 * The process-wide data layer (T06): one database file shared by the agent, the "What I remember"
 * screen and the debug hooks. The accessibility service and the app's activities run in the same
 * process, so they all get the same instances.
 *
 * If the database can't be opened the app keeps working: preferences fall back to memory and the
 * run log to a no-op, with a warning in Logcat (tag SUUS-Data).
 */
object SuusData {
    const val TAG = "SUUS-Data"
    const val DB_FILE = "suus_data.db"
    const val LEGACY_PREFS_FILE = "saved_preferences.json"

    /** Where exports go, relative to shared storage: `/sdcard/Download/suus/runs/`. */
    const val EXPORT_RELATIVE_PATH = "Download/suus/runs"

    private var database: SuusDatabase? = null
    private var databaseFailed = false
    @Volatile private var prefs: ObservablePreferenceStore? = null
    @Volatile private var runLog: RunLog? = null
    private var dbLogger: DbRunLogger? = null

    @Synchronized
    private fun db(context: Context): SuusDatabase? {
        database?.let { return it }
        if (databaseFailed) return null
        return runCatching {
            val file = context.getDatabasePath(DB_FILE).apply { parentFile?.mkdirs() }
            SuusDatabase(AndroidSQLiteDriver(), file.path)
        }.onFailure {
            databaseFailed = true
            warn("database $DB_FILE failed to open; preferences are memory-only and runs aren't logged", it)
        }.getOrNull()?.also { database = it }
    }

    fun preferences(context: Context): ObservablePreferenceStore = prefs ?: synchronized(this) {
        prefs ?: run {
            val app = context.applicationContext ?: context
            val legacy = File(app.filesDir, LEGACY_PREFS_FILE)
            val db = db(app)
            if (db == null) {
                MemoryPreferenceStore(FilePreferenceStore(legacy).all())
            } else {
                DbPreferenceStore(db).also { store ->
                    val n = store.importOnce { if (legacy.exists()) FilePreferenceStore(legacy).all() else emptyList() }
                    if (n > 0) Log.i(TAG, "imported $n preference(s) from $LEGACY_PREFS_FILE")
                }
            }
        }.also { prefs = it }
    }

    fun runLog(context: Context): RunLog = runLog ?: synchronized(this) {
        runLog ?: RunLog(logger(context.applicationContext ?: context) ?: NoOpRunLogger).also { runLog = it }
    }

    @Synchronized
    private fun logger(context: Context): DbRunLogger? =
        dbLogger ?: db(context)?.let { DbRunLogger(it, warn = ::warn) }?.also { dbLogger = it }

    /** Writes the run log to `/sdcard/Download/suus/runs/` (JSON + 2 CSVs). Returns the paths. */
    fun export(context: Context): List<String> {
        val app = context.applicationContext ?: context
        val logger = logger(app) ?: error("no database, nothing to export")
        val exporter = RunExporter()
        val export = exporter.build(logger.sessions(), logger.events(), System.currentTimeMillis())
        val paths = runCatching { exporter.write(export, DownloadsSink(app, EXPORT_RELATIVE_PATH)) }
            .getOrElse {
                // Shared Downloads refused (unusual): fall back to the app's own external folder.
                warn("export to Downloads failed, using the app folder", it)
                exporter.write(export, DirectorySink(File(app.getExternalFilesDir(null), "runs")))
            }
        Log.i(TAG, "exported: ${exporter.summaryLine(export.totals)} -> ${paths.joinToString()}")
        return paths
    }

    fun clearRuns(context: Context) {
        (logger(context.applicationContext ?: context) ?: return).clear()
        Log.i(TAG, "run log cleared")
    }

    fun summary(context: Context): String {
        val logger = logger(context.applicationContext ?: context) ?: return "no database"
        return RunExporter().summaryLine(RunSummaries.total(logger.sessions(), logger.events()))
    }

    private fun warn(msg: String, t: Throwable) {
        runCatching { Log.w(TAG, msg, t) }
    }
}

/** Saves into shared storage through MediaStore (no storage permission needed on API 29+). */
private class DownloadsSink(private val context: Context, private val relativePath: String) : ExportSink {
    override fun write(file: ExportFile): String {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, file.mime)
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("MediaStore insert failed")
        resolver.openOutputStream(uri)?.use { it.write(file.content.toByteArray()) } ?: error("MediaStore open failed")
        return "/sdcard/$relativePath/${file.name}"
    }
}

// ---------- entry points for the other modules ----------

/** The shared run log: wrap the agent's screen/voice/approval/runner with it (see [RunLog]). */
fun createRunLog(context: Context): RunLog = SuusData.runLog(context)

/**
 * The `DEBUG_RUNLOG` broadcast (debug builds), see data/README.md:
 * `op` = `export` (default) | `clear` | `summary`. Returns a line for Logcat.
 */
fun handleRunLogDebug(context: Context, op: String?): String = runCatching {
    when (op ?: "export") {
        "export" -> "exported to " + SuusData.export(context).joinToString()
        "clear" -> { SuusData.clearRuns(context); "run log cleared" }
        "summary" -> SuusData.summary(context)
        else -> "unknown op '$op' (export | clear | summary)"
    }
}.getOrElse { "DEBUG_RUNLOG $op failed: $it" }
