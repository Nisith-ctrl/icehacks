package com.icehacks.saythegoal.ui

import android.content.Context
import com.icehacks.saythegoal.agent.PreferenceStore
import com.icehacks.saythegoal.agent.SavedPreference
import com.icehacks.saythegoal.agent.sharedPreferenceStore
import java.text.DateFormat
import java.util.Date

/**
 * The one place the "What I remember" screen gets its data from: the same store instance the
 * running agent uses (`sharedPreferenceStore`, agent module), so a preference removed here is
 * forgotten by the agent at once. T06 swaps the storage behind it without changing this screen.
 */
internal fun openPreferenceStore(context: Context): PreferenceStore = sharedPreferenceStore(context)

/** "Use Email for event signups": the same words the agent says aloud, as a sentence. */
internal fun SavedPreference.headline(): String = spoken().replaceFirstChar { it.uppercase() }

/** "Nov 9, 2026" in the phone's own format. Short, so the label chip stays on one line. */
internal fun SavedPreference.savedWhen(): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(savedAtMs))
