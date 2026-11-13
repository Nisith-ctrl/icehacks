package com.icehacks.suus.ui

import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.icehacks.suus.R
import com.icehacks.suus.agent.ObservablePreferenceStore
import com.icehacks.suus.agent.PreferenceStore
import com.icehacks.suus.agent.SavedPreference
import com.icehacks.suus.agent.scopeLabel
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * "What I remember": every preference the agent has saved, in plain words, each with one big
 * Remove button and a confirmation. The agent only saves what the user asks it to remember, and
 * the empty state says so. With T06's observable store the list updates live while it's open.
 */
class MemoryActivity : AppCompatActivity() {

    private val scope = MainScope()
    private var watch: Job? = null
    private lateinit var list: LinearLayout
    private lateinit var store: PreferenceStore

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
        val (root, column) = page()
        column.addHero(heroHeader(getString(R.string.memory_title), getString(R.string.memory_intro), getString(R.string.hero_eyebrow_memory), onBack = { finish() }))
        column.addSpaced(
            card().apply {
                background = roundedBox(themeColor(R.color.suus_primary_soft), 24)
                addSpaced(bodyText(getString(R.string.memory_voice_hint), 18f).apply {
                    setTextColor(themeColor(R.color.suus_on_primary_soft))
                })
            },
            16,
        )
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addSpaced(list, 4)
        setContentView(root)
        column.animateLayoutChanges()
        list.animateLayoutChanges() // saved items slide in and out as they are added or removed
        if (savedInstanceState == null) column.revealChildren()
    }

    override fun onStart() {
        super.onStart()
        store = openPreferenceStore(this)
        val live = store as? ObservablePreferenceStore
        if (live != null) {
            // Saves and removals (by voice or here) show up while the screen is open.
            watch = scope.launch { live.preferences.collect { render(it) } }
        } else {
            render(store.all())
        }
    }

    override fun onStop() {
        watch?.cancel()
        watch = null
        super.onStop()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render(items: List<SavedPreference>) {
        list.removeAllViews()
        val saved = items.sortedByDescending { it.savedAtMs }
        if (saved.isEmpty()) {
            list.addSpaced(
                card().apply {
                    addSpaced(
                        LinearLayout(context).apply {
                            gravity = android.view.Gravity.CENTER_HORIZONTAL
                            addView(suusSprite(), LinearLayout.LayoutParams(dp(88), dp(120)))
                        },
                    )
                    addSpaced(titleText(getString(R.string.memory_empty_title), 24f), 16)
                    addSpaced(bodyText(getString(R.string.memory_empty_body)), 8)
                },
                16,
            )
            return
        }
        list.addSpaced(sectionLabel(resources.getQuantityString(R.plurals.memory_count, saved.size, saved.size)), 24)
        for (item in saved) list.addSpaced(itemCard(item), 12)
        if (saved.size >= 2) {
            list.addSpaced(bigButton(getString(R.string.memory_forget_all), ButtonKind.SECONDARY) { confirmForgetAll() }, 24)
        }
    }

    private fun itemCard(item: SavedPreference): View = card().apply {
        // "EVENT SIGNUPS · Nov 10, 2026": the kind of task it applies to, and when it was saved.
        addWrapped(chip().apply {
            setChip(getString(R.string.memory_label, scopeLabel(item.scope).uppercase(), item.savedWhen()), ChipKind.INFO)
            textSize = 15f
            letterSpacing = 0.04f
        })
        addSpaced(titleText(item.headline(), 24f), 12)
        if (item.sourceUtterance.isNotBlank()) {
            addSpaced(bodyText(getString(R.string.memory_you_said, item.sourceUtterance)).apply {
                setTypeface(typeface, Typeface.ITALIC)
            }, 6)
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
        if (store !is ObservablePreferenceStore) render(store.all())
        list.announceForAccessibility(getString(R.string.memory_removed, item.headline()))
    }

    private fun confirmForgetAll() = confirm(
        getString(R.string.memory_forget_all_title),
        getString(R.string.memory_forget_all_body),
        getString(R.string.memory_forget_all_yes),
    ) {
        store.clear()
        if (store !is ObservablePreferenceStore) render(store.all())
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
        dialog.window?.setBackgroundDrawable(roundedBox(themeColor(R.color.suus_surface), 28))
        dialog.show()
    }
}
