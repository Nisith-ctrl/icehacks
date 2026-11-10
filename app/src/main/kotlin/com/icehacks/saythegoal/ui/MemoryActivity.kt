package com.icehacks.saythegoal.ui

import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.icehacks.saythegoal.R
import com.icehacks.saythegoal.agent.PreferenceStore
import com.icehacks.saythegoal.agent.SavedPreference
import com.icehacks.saythegoal.agent.scopeLabel

/**
 * "What I remember": every preference the agent has saved, in plain words, each with one big
 * Remove button and a confirmation. The agent only saves what the user asks it to remember, and
 * the empty state says so.
 */
class MemoryActivity : AppCompatActivity() {

    private lateinit var list: LinearLayout
    private lateinit var store: PreferenceStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val (root, column) = page()
        column.addSpaced(titleText(getString(R.string.memory_title), 32f))
        column.addSpaced(bodyText(getString(R.string.memory_intro)), 8)
        column.addSpaced(bodyText(getString(R.string.memory_voice_hint), 18f, secondary = false), 8)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addSpaced(list, 8)
        column.addSpaced(bigButton(getString(R.string.back), ButtonKind.SECONDARY) { finish() }, 24)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        // Re-open on every visit: the agent may have saved something since we were last here.
        store = openPreferenceStore(this)
        render()
    }

    private fun render() {
        list.removeAllViews()
        val saved = store.all().sortedByDescending { it.savedAtMs }
        if (saved.isEmpty()) {
            list.addSpaced(
                card().apply {
                    addSpaced(titleText(getString(R.string.memory_empty_title), 22f))
                    addSpaced(bodyText(getString(R.string.memory_empty_body)), 8)
                },
                16,
            )
            return
        }
        for (item in saved) list.addSpaced(itemCard(item), 16)
        if (saved.size >= 2) {
            list.addSpaced(bigButton(getString(R.string.memory_forget_all), ButtonKind.SECONDARY) { confirmForgetAll() }, 24)
        }
    }

    private fun itemCard(item: SavedPreference): View = card().apply {
        // "EVENT SIGNUPS · 10 Nov 2026": the kind of task it applies to, and when it was saved.
        addSpaced(
            bodyText(getString(R.string.memory_label, scopeLabel(item.scope).uppercase(), item.savedWhen()), 15f).apply {
                letterSpacing = 0.06f
                typeface = Typeface.DEFAULT_BOLD
            },
        )
        addSpaced(titleText(item.headline(), 22f), 6)
        if (item.sourceUtterance.isNotBlank()) {
            addSpaced(bodyText(getString(R.string.memory_you_said, item.sourceUtterance)), 4)
        }
        val remove = bigButton(getString(R.string.memory_remove), ButtonKind.DANGER) { confirmRemove(item) }
        // Several Remove buttons on one screen: tell TalkBack which one this is.
        remove.contentDescription = getString(R.string.memory_remove_described, item.headline())
        addSpaced(remove, 16)
    }

    private fun confirmRemove(item: SavedPreference) = confirm(
        getString(R.string.memory_confirm_title),
        getString(R.string.memory_confirm_body, item.headline()),
        getString(R.string.memory_confirm_yes),
    ) {
        store.remove(item.scope, item.key)
        render()
        list.announceForAccessibility(getString(R.string.memory_removed, item.headline()))
    }

    private fun confirmForgetAll() = confirm(
        getString(R.string.memory_forget_all_title),
        getString(R.string.memory_forget_all_body),
        getString(R.string.memory_forget_all_yes),
    ) {
        store.clear()
        render()
        list.announceForAccessibility(getString(R.string.memory_forgot_all))
    }

    /** A dialog with two full-size buttons. The standard dialog buttons are too small to hit reliably. */
    private fun confirm(title: String, body: String, yes: String, onYes: () -> Unit) {
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(24))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(body)
            .setView(buttons)
            .create()
        buttons.addSpaced(
            bigButton(yes, ButtonKind.DANGER) {
                dialog.dismiss()
                onYes()
            },
            8,
        )
        buttons.addSpaced(bigButton(getString(R.string.memory_confirm_no), ButtonKind.SECONDARY) { dialog.dismiss() }, 12)
        dialog.show()
    }
}
