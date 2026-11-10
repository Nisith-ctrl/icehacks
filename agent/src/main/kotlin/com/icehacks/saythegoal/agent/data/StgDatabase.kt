package com.icehacks.saythegoal.agent.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import com.icehacks.saythegoal.agent.SavedPreference

/**
 * The app's one SQLite file: saved preferences, run sessions and run events (T06).
 *
 * Hand-written SQL on the androidx.sqlite driver API, no Room or KSP (see the vault decision
 * `2026-11-10-data-store-sqlite-driver`). The phone uses `AndroidSQLiteDriver` (framework SQLite);
 * JVM unit tests use `BundledSQLiteDriver`, so the tests run this exact SQL. One connection, and
 * every call holds one lock, so it is safe from the agent's thread and the UI thread at once.
 */
class StgDatabase(driver: SQLiteDriver, fileName: String) : AutoCloseable {
    private val conn: SQLiteConnection = driver.open(fileName)
    private val lock = Any()

    init {
        synchronized(lock) { migrate() }
    }

    // ---------- schema ----------

    private fun migrate() {
        val version = rows("PRAGMA user_version") { it.getLong(0) }.firstOrNull() ?: 0L
        if (version < 1) {
            conn.execSQL("CREATE TABLE IF NOT EXISTS meta(name TEXT PRIMARY KEY NOT NULL, value TEXT)")
            conn.execSQL(
                """CREATE TABLE IF NOT EXISTS preferences(
                    scope TEXT NOT NULL,
                    pref_key TEXT NOT NULL,
                    value TEXT NOT NULL,
                    source_utterance TEXT NOT NULL,
                    saved_at_ms INTEGER NOT NULL,
                    PRIMARY KEY(scope, pref_key))""",
            )
            conn.execSQL(
                """CREATE TABLE IF NOT EXISTS run_sessions(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    kind TEXT NOT NULL,
                    text TEXT NOT NULL,
                    take_over INTEGER NOT NULL DEFAULT 0,
                    started_at_ms INTEGER NOT NULL,
                    ended_at_ms INTEGER,
                    outcome TEXT,
                    outcome_detail TEXT)""",
            )
            conn.execSQL(
                """CREATE TABLE IF NOT EXISTS run_events(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id INTEGER NOT NULL,
                    at_ms INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    name TEXT,
                    label TEXT,
                    detail TEXT,
                    ok INTEGER)""",
            )
            conn.execSQL("CREATE INDEX IF NOT EXISTS run_events_session ON run_events(session_id, id)")
            conn.execSQL("PRAGMA user_version = 1")
        }
    }

    // ---------- meta ----------

    fun meta(name: String): String? = synchronized(lock) {
        rows("SELECT value FROM meta WHERE name = ?", name) { it.textOrNull(0) }.firstOrNull()
    }

    fun setMeta(name: String, value: String) = synchronized(lock) {
        exec("INSERT OR REPLACE INTO meta(name, value) VALUES (?, ?)", name, value)
        Unit
    }

    // ---------- preferences ----------

    /** In save order: an overwrite moves the preference to the end, like the old in-memory list. */
    fun loadPreferences(): List<SavedPreference> = synchronized(lock) {
        rows("SELECT scope, pref_key, value, source_utterance, saved_at_ms FROM preferences ORDER BY rowid") {
            SavedPreference(it.getText(0), it.getText(1), it.getText(2), it.getText(3), it.getLong(4))
        }
    }

    /** One value per scope + key: replaces any older one. */
    fun upsertPreference(p: SavedPreference) = synchronized(lock) {
        exec(
            "INSERT OR REPLACE INTO preferences(scope, pref_key, value, source_utterance, saved_at_ms) VALUES (?, ?, ?, ?, ?)",
            p.scope, p.key, p.value, p.sourceUtterance, p.savedAtMs,
        )
        Unit
    }

    /** Adds [p] only if nothing is saved for its scope + key yet. True if it was added. */
    fun insertPreferenceIfAbsent(p: SavedPreference): Boolean = synchronized(lock) {
        exec(
            "INSERT OR IGNORE INTO preferences(scope, pref_key, value, source_utterance, saved_at_ms) VALUES (?, ?, ?, ?, ?)",
            p.scope, p.key, p.value, p.sourceUtterance, p.savedAtMs,
        ) > 0
    }

    fun deletePreference(scope: String, key: String): Boolean = synchronized(lock) {
        exec("DELETE FROM preferences WHERE scope = ? AND pref_key = ?", scope, key) > 0
    }

    fun clearPreferences() = synchronized(lock) {
        exec("DELETE FROM preferences")
        Unit
    }

    // ---------- run log ----------

    fun insertSession(kind: String, text: String, takeOver: Boolean, startedAtMs: Long): Long = synchronized(lock) {
        exec("INSERT INTO run_sessions(kind, text, take_over, started_at_ms) VALUES (?, ?, ?, ?)", kind, text, takeOver, startedAtMs)
        lastInsertId()
    }

    /** Sets the outcome unless one is already set (the first, most specific outcome wins). */
    fun setSessionOutcome(id: Long, outcome: String, detail: String?) = synchronized(lock) {
        exec("UPDATE run_sessions SET outcome = ?, outcome_detail = ? WHERE id = ? AND outcome IS NULL", outcome, detail, id)
        Unit
    }

    /** Marks the session ended. [outcome] only applies if nothing set one earlier. */
    fun endSession(id: Long, endedAtMs: Long, outcome: String, detail: String?) = synchronized(lock) {
        exec(
            """UPDATE run_sessions SET ended_at_ms = ?,
                outcome_detail = CASE WHEN outcome IS NULL THEN ? ELSE outcome_detail END,
                outcome = COALESCE(outcome, ?)
               WHERE id = ?""",
            endedAtMs, detail, outcome, id,
        )
        Unit
    }

    fun insertEvent(sessionId: Long, atMs: Long, type: String, name: String?, label: String?, detail: String?, ok: Boolean?): Long =
        synchronized(lock) {
            exec(
                "INSERT INTO run_events(session_id, at_ms, type, name, label, detail, ok) VALUES (?, ?, ?, ?, ?, ?, ?)",
                sessionId, atMs, type, name, label, detail, ok,
            )
            lastInsertId()
        }

    fun sessions(): List<RunSession> = synchronized(lock) {
        rows("SELECT id, kind, text, take_over, started_at_ms, ended_at_ms, outcome, outcome_detail FROM run_sessions ORDER BY id") {
            RunSession(
                id = it.getLong(0),
                kind = it.getText(1),
                text = it.getText(2),
                takeOver = it.getLong(3) != 0L,
                startedAtMs = it.getLong(4),
                endedAtMs = it.longOrNull(5),
                outcome = it.textOrNull(6),
                outcomeDetail = it.textOrNull(7),
            )
        }
    }

    fun events(): List<RunEvent> = synchronized(lock) {
        rows("SELECT id, session_id, at_ms, type, name, label, detail, ok FROM run_events ORDER BY id") {
            RunEvent(
                id = it.getLong(0),
                sessionId = it.getLong(1),
                atMs = it.getLong(2),
                type = it.getText(3),
                name = it.textOrNull(4),
                label = it.textOrNull(5),
                detail = it.textOrNull(6),
                ok = it.longOrNull(7)?.let { v -> v != 0L },
            )
        }
    }

    fun clearRuns() = synchronized(lock) {
        exec("DELETE FROM run_events")
        exec("DELETE FROM run_sessions")
        Unit
    }

    override fun close() = synchronized(lock) { conn.close() }

    // ---------- helpers (call with the lock held) ----------

    /** Runs one statement; returns how many rows it changed. */
    private fun exec(sql: String, vararg args: Any?): Int {
        conn.prepare(sql).use { st ->
            bind(st, args)
            while (st.step()) { /* drain */ }
        }
        return rows("SELECT changes()") { it.getLong(0).toInt() }.first()
    }

    private fun lastInsertId(): Long = rows("SELECT last_insert_rowid()") { it.getLong(0) }.first()

    private fun <T> rows(sql: String, vararg args: Any?, read: (SQLiteStatement) -> T): List<T> =
        conn.prepare(sql).use { st ->
            bind(st, args)
            buildList { while (st.step()) add(read(st)) }
        }

    private fun bind(st: SQLiteStatement, args: Array<out Any?>) {
        args.forEachIndexed { i, a ->
            val idx = i + 1
            when (a) {
                null -> st.bindNull(idx)
                is String -> st.bindText(idx, a)
                is Long -> st.bindLong(idx, a)
                is Int -> st.bindLong(idx, a.toLong())
                is Boolean -> st.bindLong(idx, if (a) 1L else 0L)
                else -> st.bindText(idx, a.toString())
            }
        }
    }

    private fun SQLiteStatement.textOrNull(i: Int) = if (isNull(i)) null else getText(i)
    private fun SQLiteStatement.longOrNull(i: Int) = if (isNull(i)) null else getLong(i)
}
