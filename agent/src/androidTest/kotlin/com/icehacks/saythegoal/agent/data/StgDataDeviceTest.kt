package com.icehacks.saythegoal.agent.data

import android.content.ContentUris
import android.provider.MediaStore
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.icehacks.saythegoal.agent.FilePreferenceStore
import com.icehacks.saythegoal.agent.SavedPreference
import com.icehacks.saythegoal.agent.createPreferenceStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * On a phone or emulator: the real framework SQLite driver, the one shared store, the one-time
 * import from the old JSON file, and the export into /sdcard/Download/stg/runs/.
 * Run: `./gradlew :agent:connectedDebugAndroidTest` with an emulator running.
 */
@RunWith(AndroidJUnit4::class)
class StgDataDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun sharedStoreImportsOnceSurvivesAndExports() {
        // A fresh install with the old JSON file from the T03 agent.
        ctx.deleteDatabase(StgData.DB_FILE)
        val legacy = File(ctx.filesDir, StgData.LEGACY_PREFS_FILE).apply { delete() }
        FilePreferenceStore(legacy).save(
            SavedPreference("event_registration", "confirmation_method", "Email", "Email. Remember that for event signups.", 1L),
        )

        val store = createPreferenceStore(ctx)
        assertSame("one instance for the agent and the memory screen", store, createPreferenceStore(ctx))
        assertEquals(listOf("Email"), store.all().map { it.value })
        assertEquals(store.all(), store.preferences.value)

        store.save(SavedPreference("volunteering", "role", "Greeter", "Greeter, remember that.", 2L))
        assertTrue(store.remove("event_registration", "confirmation_method"))

        // What a restart would read: a separate connection on the same file.
        StgDatabase(AndroidSQLiteDriver(), ctx.getDatabasePath(StgData.DB_FILE).path).use { reopened ->
            assertEquals(listOf("Greeter"), reopened.loadPreferences().map { it.value })
            assertEquals("1", reopened.meta(DbPreferenceStore.LEGACY_IMPORTED))
        }

        // Run log through the shared RunLog, then the adb export.
        val log = createRunLog(ctx)
        assertSame(log, createRunLog(ctx))
        StgData.clearRuns(ctx)
        log.logger.sessionStarted(SessionKind.GOAL, "register me for the library workshop")
        log.logger.actionPerformed("tap", "Gmail", true)
        log.logger.questionAsked("Which confirmation method should I use?", "Email")
        log.logger.approvalRequested("Library workshop, Thursday 10 AM.", "Submit", "APPROVED")
        log.logger.sessionEnded("finished", "You're registered.")
        assertTrue(StgData.summary(ctx).startsWith("1 sessions, 1 actions (0 failed), 3 spoken commands"))

        val paths = StgData.export(ctx)
        assertEquals(3, paths.size)
        assertTrue(paths.toString(), paths.all { it.startsWith("/sdcard/Download/stg/runs/stg-") })

        // The files are really in shared Downloads (read back through MediaStore).
        val names = paths.map { it.substringAfterLast('/') }
        val found = mutableMapOf<String, String>()
        ctx.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME),
            "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?", arrayOf("Download/stg/runs%"), null,
        )!!.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1)
                if (name in names) {
                    val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0))
                    found[name] = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                }
            }
        }
        assertEquals(names.toSet(), found.keys)
        val json = found.getValue(names.single { it.endsWith(".json") })
        assertTrue(json.contains("\"spokenCommands\": 3"))
        assertTrue(json.contains("register me for the library workshop"))

        assertEquals("run log cleared", handleRunLogDebug(ctx, "clear"))
        assertTrue(StgData.summary(ctx).startsWith("0 sessions"))
        assertTrue(handleRunLogDebug(ctx, "nope").startsWith("unknown op"))
    }
}
