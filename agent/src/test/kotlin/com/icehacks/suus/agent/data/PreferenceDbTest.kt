package com.icehacks.suus.agent.data

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.icehacks.suus.agent.FilePreferenceStore
import com.icehacks.suus.agent.SavedPreference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PreferenceDbTest {
    @get:Rule val tmp = TemporaryFolder()

    private val opened = mutableListOf<SuusDatabase>()
    private val dbFile by lazy { File(tmp.root, "suus_data.db").path }

    private fun open() = SuusDatabase(BundledSQLiteDriver(), dbFile).also { opened += it }

    @After fun closeAll() = opened.forEach { runCatching { it.close() } }

    private fun pref(value: String, key: String = "confirmation_method", scope: String = "event_registration", at: Long = 1L) =
        SavedPreference(scope, key, value, "$value. Remember that for event signups.", at)

    @Test fun saveAndRead() {
        val store = DbPreferenceStore(open())
        store.save(pref("Email"))
        assertEquals(listOf(pref("Email")), store.all())
    }

    @Test fun overwriteKeepsOneValuePerScopeAndKeyAndMovesItLast() {
        val store = DbPreferenceStore(open())
        store.save(pref("Email", at = 1))
        store.save(pref("Large", key = "text_size", at = 2))
        store.save(pref("Text message", at = 3))
        assertEquals(listOf("Large", "Text message"), store.all().map { it.value })
        assertEquals(2, store.all().size)
    }

    @Test fun sameKeyInAnotherScopeIsSeparate() {
        val store = DbPreferenceStore(open())
        store.save(pref("Email"))
        store.save(pref("Phone", scope = "volunteering"))
        assertEquals(2, store.all().size)
    }

    @Test fun remove() {
        val store = DbPreferenceStore(open())
        store.save(pref("Email"))
        assertTrue(store.remove("event_registration", "confirmation_method"))
        assertFalse(store.remove("event_registration", "confirmation_method"))
        assertTrue(store.all().isEmpty())
    }

    @Test fun clear() {
        val store = DbPreferenceStore(open())
        store.save(pref("Email"))
        store.save(pref("Large", key = "text_size"))
        store.clear()
        assertTrue(store.all().isEmpty())
    }

    @Test fun survivesRestart() {
        val first = open()
        DbPreferenceStore(first).apply {
            save(pref("Email", at = 5))
            save(pref("Large", key = "text_size", at = 6))
            remove("event_registration", "text_size")
        }
        first.close()
        val again = DbPreferenceStore(open())
        assertEquals(listOf(pref("Email", at = 5)), again.all())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun flowEmitsEveryChange() = runTest {
        val store = DbPreferenceStore(open())
        val seen = mutableListOf<List<String>>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) { store.preferences.collect { l -> seen += l.map { it.value } } }
        store.save(pref("Email"))
        store.save(pref("Large", key = "text_size"))
        store.remove("event_registration", "confirmation_method")
        store.clear()
        job.cancel()
        assertEquals(listOf(emptyList(), listOf("Email"), listOf("Email", "Large"), listOf("Large"), emptyList<String>()), seen)
    }

    @Test fun importsTheOldJsonFileOnce() {
        val json = File(tmp.root, "saved_preferences.json")
        FilePreferenceStore(json).apply {
            save(pref("Email", at = 10))
            save(pref("Large", key = "text_size", at = 11))
        }
        val db = open()
        val store = DbPreferenceStore(db)
        assertEquals(2, store.importOnce { FilePreferenceStore(json).all() })
        assertEquals(listOf("Email", "Large"), store.all().map { it.value })
        assertEquals(10L, store.all().first().savedAtMs)

        // Forget one, then start again with the old file still there: it must not come back.
        store.remove("event_registration", "confirmation_method")
        db.close()
        val again = DbPreferenceStore(open())
        assertEquals(0, again.importOnce { FilePreferenceStore(json).all() })
        assertEquals(listOf("Large"), again.all().map { it.value })
    }

    @Test fun importNeverOverwritesWhatTheDatabaseHas() {
        val store = DbPreferenceStore(open())
        store.save(pref("Text message", at = 20))
        assertEquals(1, store.importOnce { listOf(pref("Email", at = 10), pref("Large", key = "text_size", at = 11)) })
        assertEquals(listOf("Text message", "Large"), store.all().map { it.value })
    }

    @Test fun missingOrBrokenJsonImportsNothing() {
        val broken = File(tmp.root, "saved_preferences.json").apply { writeText("{not json") }
        val store = DbPreferenceStore(open())
        assertEquals(0, store.importOnce { FilePreferenceStore(broken).all() })
        assertTrue(store.all().isEmpty())
    }

    @Test fun storesUnusualText() {
        val store = DbPreferenceStore(open())
        val p = SavedPreference("event_registration", "note", "O'Brien, \"Al\" 🐶\nline 2", "say: it's \"fine\"", 1)
        store.save(p)
        assertEquals(p, DbPreferenceStore(open()).all().single())
    }

    @Test fun memoryFallbackFollowsTheSameRules() {
        val store = MemoryPreferenceStore()
        store.save(pref("Email"))
        store.save(pref("Large", key = "text_size"))
        store.save(pref("Text message"))
        assertEquals(listOf("Large", "Text message"), store.all().map { it.value })
        assertTrue(store.remove("event_registration", "text_size"))
        assertFalse(store.remove("event_registration", "text_size"))
        store.clear()
        assertEquals(emptyList<SavedPreference>(), store.preferences.value)
    }
}
