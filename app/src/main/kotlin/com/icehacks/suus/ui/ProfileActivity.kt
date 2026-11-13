package com.icehacks.suus.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.icehacks.suus.R
import com.icehacks.suus.agent.createProfileStore

/**
 * "About you": the name, email and phone SuuS puts into forms, saved once and used everywhere.
 * The same form as in first-run setup ([ProfileForm]), with Android autofill hints. Anything left
 * blank, SuuS asks for by voice the first time a form needs it, then saves.
 */
class ProfileActivity : AppCompatActivity() {

    private lateinit var form: ProfileForm
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
        val (root, column) = page()

        column.addSpaced(bigButton(getString(R.string.back_arrow), ButtonKind.TONAL) { finish() }.apply {
            contentDescription = getString(R.string.back)
        })
        column.addSpaced(heroHeader(getString(R.string.profile_title), getString(R.string.profile_intro), getString(R.string.hero_eyebrow_profile)), 16)

        form = ProfileForm(this, createProfileStore(this))
        column.addSpaced(form.build(), 16)

        status = bodyText("", 18f, secondary = false).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            visibility = View.GONE
        }
        column.addSpaced(status, 12)
        column.addSpaced(bigButton(getString(R.string.profile_save), ButtonKind.PRIMARY) { save() }, 16)
        column.addSpaced(bodyText(getString(R.string.profile_voice_hint), 16f), 16)
        setContentView(root)
        column.animateLayoutChanges()
        if (savedInstanceState == null) column.revealChildren()
    }

    private fun save() {
        status.text = form.save() ?: getString(R.string.profile_saved)
        status.visibility = View.VISIBLE
    }
}
