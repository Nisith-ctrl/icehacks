package com.icehacks.saythegoal.ui

import android.content.Context
import com.icehacks.saythegoal.agent.FilePreferenceStore
import com.icehacks.saythegoal.agent.PreferenceStore
import com.icehacks.saythegoal.agent.SavedPreference
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * The one place the "What I remember" screen gets its data from.
 *
 * Today this opens the same JSON file the agent writes (`createAgent` in the agent module), read
 * fresh each time. When T06 lands, replace the body with `createPreferenceStore(context)` and
 * nothing else in the UI has to change.
 *
 * Known gap until then: the running agent keeps its own copy in memory, so something removed here
 * is only forgotten by the agent after the accessibility service restarts.
 */
internal fun openPreferenceStore(context: Context): PreferenceStore =
    FilePreferenceStore(File(context.filesDir, "saved_preferences.json"))

/** "Use Email for event signups": the same words the agent says aloud, as a sentence. */
internal fun SavedPreference.headline(): String = spoken().replaceFirstChar { it.uppercase() }

/** "9 Nov 2026, 3:12 PM" in the phone's own format. */
internal fun SavedPreference.savedWhen(): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(savedAtMs))
