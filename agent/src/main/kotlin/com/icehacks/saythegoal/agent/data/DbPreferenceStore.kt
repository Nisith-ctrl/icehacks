package com.icehacks.saythegoal.agent.data

import com.icehacks.saythegoal.agent.ObservablePreferenceStore
import com.icehacks.saythegoal.agent.SavedPreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Saved preferences in the `preferences` table (T06 M1). Reads come from an in-memory copy, so
 * `all()` never touches the disk; every change writes the database first, then updates the copy
 * and [preferences] in the same lock, so the UI and the agent always see the same list.
 */
class DbPreferenceStore(private val db: StgDatabase) : ObservablePreferenceStore {
    private val state = MutableStateFlow(db.loadPreferences())
    override val preferences: StateFlow<List<SavedPreference>> = state.asStateFlow()

    override fun all(): List<SavedPreference> = state.value

    @Synchronized
    override fun save(p: SavedPreference) {
        db.upsertPreference(p)
        refresh()
    }

    @Synchronized
    override fun remove(scope: String, key: String): Boolean =
        db.deletePreference(scope, key).also { refresh() }

    @Synchronized
    override fun clear() {
        db.clearPreferences()
        refresh()
    }

    /**
     * Copies preferences from the old JSON store, once per database: after the first call this is
     * a no-op even if the file is still there, so something forgotten never comes back. Never
     * overwrites a preference already in the database. Returns how many were imported.
     */
    @Synchronized
    fun importOnce(legacy: () -> List<SavedPreference>): Int {
        if (db.meta(LEGACY_IMPORTED) != null) return 0
        val imported = legacy().count { db.insertPreferenceIfAbsent(it) }
        db.setMeta(LEGACY_IMPORTED, imported.toString())
        refresh()
        return imported
    }

    private fun refresh() {
        state.value = db.loadPreferences()
    }

    companion object {
        const val LEGACY_IMPORTED = "legacy_json_imported"
    }
}

/** Fallback if the database can't be opened: works, but forgets everything on restart. */
class MemoryPreferenceStore(initial: List<SavedPreference> = emptyList()) : ObservablePreferenceStore {
    private val state = MutableStateFlow(initial)
    override val preferences: StateFlow<List<SavedPreference>> = state.asStateFlow()
    override fun all() = state.value

    @Synchronized
    override fun save(p: SavedPreference) {
        state.value = state.value.filterNot { it.scope == p.scope && it.key == p.key } + p
    }

    @Synchronized
    override fun remove(scope: String, key: String): Boolean {
        val before = state.value
        state.value = before.filterNot { it.scope == scope && it.key == key }
        return state.value.size != before.size
    }

    @Synchronized
    override fun clear() {
        state.value = emptyList()
    }
}
