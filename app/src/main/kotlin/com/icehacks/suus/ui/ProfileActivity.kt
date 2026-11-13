package com.icehacks.suus.ui

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.icehacks.suus.R
import com.icehacks.suus.agent.Profile
import com.icehacks.suus.agent.ProfileStore
import com.icehacks.suus.agent.createProfileStore

/**
 * "About you": the name, email and phone SuuS puts into forms, saved once and used everywhere.
 * Big fields with Android autofill hints, so a saved Google account can fill them in one tap.
 * Anything left blank, SuuS asks for by voice the first time a form needs it, then saves.
 */
class ProfileActivity : AppCompatActivity() {

    private lateinit var store: ProfileStore
    private lateinit var name: EditText
    private lateinit var email: EditText
    private lateinit var phone: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
        store = createProfileStore(this)
        val (root, column) = page()

        column.addSpaced(bigButton(getString(R.string.back_arrow), ButtonKind.TONAL) { finish() }.apply {
            contentDescription = getString(R.string.back)
        })
        column.addSpaced(heroHeader(getString(R.string.profile_title), getString(R.string.profile_intro), getString(R.string.hero_eyebrow_profile)), 16)

        val p = store.load()
        val form = card()
        name = field(form, R.string.profile_name, p.fullName,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME or InputType.TYPE_TEXT_FLAG_CAP_WORDS,
            View.AUTOFILL_HINT_NAME, first = true)
        email = field(form, R.string.profile_email, p.email,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, View.AUTOFILL_HINT_EMAIL_ADDRESS)
        phone = field(form, R.string.profile_phone, p.phone, InputType.TYPE_CLASS_PHONE, View.AUTOFILL_HINT_PHONE)
        phone.imeOptions = EditorInfo.IME_ACTION_DONE
        column.addSpaced(form, 16)

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

    private fun field(parent: LinearLayout, label: Int, value: String, type: Int, hint: String, first: Boolean = false): EditText {
        val title = titleText(getString(label), 20f)
        val box = EditText(this).apply {
            setText(value)
            inputType = type
            textSize = 22f
            minHeight = dp(64)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setTextColor(themeColor(R.color.suus_text))
            setHintTextColor(themeColor(R.color.suus_text_secondary))
            this.hint = getString(R.string.profile_not_set)
            background = roundedBox(themeColor(R.color.suus_surface_alt), 16, themeColor(R.color.suus_outline), 2)
            setAutofillHints(hint)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
            imeOptions = EditorInfo.IME_ACTION_NEXT
        }
        title.labelFor = View.generateViewId().also { box.id = it }
        parent.addSpaced(title, if (first) 0 else 20)
        parent.addSpaced(box, 8)
        return box
    }

    private fun save() {
        val emailText = email.text.toString().trim()
        if (emailText.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(emailText).matches()) {
            showStatus(getString(R.string.profile_bad_email))
            email.requestFocus()
            return
        }
        store.save(Profile(name.text.toString().trim(), emailText, phone.text.toString().trim()))
        showStatus(getString(R.string.profile_saved))
    }

    private fun showStatus(text: String) {
        status.text = text
        status.visibility = View.VISIBLE
    }
}
