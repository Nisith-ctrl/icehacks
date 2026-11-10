package com.icehacks.saythegoal.ui

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.icehacks.saythegoal.R
import com.icehacks.saythegoal.agent.PreferenceStore
import com.icehacks.saythegoal.agent.SavedPreference

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
    }

    private fun itemCard(item: SavedPreference): View = card().apply {
        addSpaced(titleText(item.headline(), 22f))
        addSpaced(bodyText(getString(R.string.memory_saved_on, item.savedWhen())), 8)
        if (item.sourceUtterance.isNotBlank()) {
            addSpaced(bodyText(getString(R.string.memory_you_said, item.sourceUtterance)), 4)
        }
        val remove = bigButton(getString(R.string.memory_remove), ButtonKind.DANGER) { confirmRemove(item) }
        // Several Remove buttons on one screen: tell TalkBack which one this is.
        remove.contentDescription = getString(R.string.memory_remove_described, item.headline())
        addSpaced(remove, 16)
    }

    /** A dialog with two full-size buttons. The standard dialog buttons are too small to hit reliably. */
    private fun confirmRemove(item: SavedPreference) {
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(24))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.memory_confirm_title)
            .setMessage(getString(R.string.memory_confirm_body, item.headline()))
            .setView(buttons)
            .create()
        buttons.addSpaced(
            bigButton(getString(R.string.memory_confirm_yes), ButtonKind.DANGER) {
                store.remove(item.scope, item.key)
                dialog.dismiss()
                render()
                list.announceForAccessibility(getString(R.string.memory_removed, item.headline()))
            },
            8,
        )
        buttons.addSpaced(bigButton(getString(R.string.memory_confirm_no), ButtonKind.SECONDARY) { dialog.dismiss() }, 12)
        dialog.show()
    }
}
