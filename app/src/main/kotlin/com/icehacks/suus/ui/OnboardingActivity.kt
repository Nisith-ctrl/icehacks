package com.icehacks.suus.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.icehacks.suus.R
import com.icehacks.suus.SetupActivity
import com.icehacks.suus.SuusAccessibilityService
import com.icehacks.suus.agent.createProfileStore
import com.icehacks.suus.agent.createSettingsStore
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

/**
 * First-run setup, one page at a time: welcome → every permission SuuS needs → "About you" (the
 * account details forms use) → how SuuS should talk → done. Each page has one obvious button; Back
 * is always there. Finishing marks setup done, and the app opens on the home screen from then on.
 */
class OnboardingActivity : AppCompatActivity() {

    private val scope = MainScope()
    private lateinit var root: ScrollView
    private lateinit var column: LinearLayout
    private var page = 0
    private var profileForm: ProfileForm? = null
    private var permissionRows: List<PermissionRow> = emptyList()
    private var permMessage: TextView? = null
    private var allowAllButton: View? = null

    private val photosPermission =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    /** One permission: what it's for, whether SuuS can't work without it, and how to turn it on. */
    private inner class PermissionRow(
        val title: Int, val detail: Int, val required: Boolean,
        val isDone: () -> Boolean, val turnOn: () -> Unit,
    ) {
        lateinit var badge: TextView
        lateinit var chip: TextView
        lateinit var button: View
        lateinit var box: View
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        super.onCreate(savedInstanceState)
        page = savedInstanceState?.getInt(KEY_PAGE) ?: 0
        val (r, c) = page()
        root = r; column = c
        setContentView(root)
        column.animateLayoutChanges()
        show(page, forward = true, animate = savedInstanceState == null)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PAGE, page) // the colour choice recreates the screen: stay on the same page
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions() // coming back from Accessibility settings or a permission dialog
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshPermissions()
        // "Allow all": after the dialogs, go on to the one switch Android only lets you flip in Settings.
        if (requestCode == REQ_ALL && !serviceEnabled()) startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ---------- pages ----------

    private fun show(target: Int, forward: Boolean, animate: Boolean = true) {
        page = target
        column.layoutTransition = null // rebuild without per-view transitions; the page slides as a whole
        column.removeAllViews()
        profileForm = null
        permissionRows = emptyList()
        permMessage = null
        allowAllButton = null
        when (page) {
            0 -> welcomePage()
            1 -> permissionsPage()
            2 -> aboutPage()
            3 -> stylePage()
            else -> donePage()
        }
        root.scrollTo(0, 0)
        if (animate && motionAllowed()) {
            column.alpha = 0f
            column.translationX = dp(if (forward) 40 else -40).toFloat()
            column.animate().alpha(1f).translationX(0f).setDuration(320).setInterpolator(SPRING_OUT).start()
            column.post { column.revealChildren(35) }
        }
        column.animateLayoutChanges()
        column.announceForAccessibility(getString(R.string.onb_step, page + 1, PAGES))
    }

    /** "STEP 2 OF 5" plus five segments, the done ones filled with the ribbon colours. */
    private fun stepper(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addSpaced(sectionLabel(getString(R.string.onb_step, page + 1, PAGES)).apply { ViewCompatHeadingOff(this) })
        addSpaced(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            for (i in 0 until PAGES) {
                addView(View(context).apply {
                    background = roundedBox(themeColor(if (i <= page) R.color.suus_primary else R.color.suus_outline), 4)
                }, LinearLayout.LayoutParams(0, dp(6), 1f).apply { if (i > 0) marginStart = dp(6) })
            }
        }, 8)
    }

    private fun nav(primaryLabel: Int, onPrimary: () -> Unit, secondary: Pair<Int, () -> Unit>? = null) {
        column.addSpaced(bigButton(getString(primaryLabel), ButtonKind.PRIMARY, onPrimary), 24)
        secondary?.let { (label, action) -> column.addSpaced(bigButton(getString(label), ButtonKind.SECONDARY, action), 12) }
        if (page > 0) column.addSpaced(bigButton(getString(R.string.onb_back), ButtonKind.TONAL) { show(page - 1, forward = false) }, 12)
    }

    private fun welcomePage() {
        column.addSpaced(heroHeader(getString(R.string.onb_welcome_title), getString(R.string.onb_welcome_short), getString(R.string.onb_welcome_eyebrow)))
        column.addSpaced(card().apply {
            addSpaced(bodyText(getString(R.string.onb_welcome_body), 20f, secondary = false))
            addSpaced(bodyText(getString(R.string.onb_welcome_points), 19f).apply { setLineSpacing(0f, 1.4f) }, 14)
        }, 16)
        nav(R.string.onb_start, { show(1, forward = true) })
    }

    private fun permissionsPage() {
        column.addSpaced(stepper())
        column.addSpaced(heroHeader(getString(R.string.onb_perm_head), getString(R.string.onb_perm_short)), 16)
        column.addSpaced(bodyText(getString(R.string.onb_perm_body), 18f), 14)
        allowAllButton = bigButton(getString(R.string.onb_perm_allow_all), ButtonKind.PRIMARY) { allowAll() }
        column.addSpaced(allowAllButton!!, 16)
        permissionRows = listOf(
            PermissionRow(R.string.step_service_title, R.string.step_service_detail, true, ::serviceEnabled) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            PermissionRow(R.string.step_mic_title, R.string.step_mic_detail, true, { granted(Manifest.permission.RECORD_AUDIO) }) {
                askFor(Manifest.permission.RECORD_AUDIO)
            },
            PermissionRow(R.string.step_calendar_title, R.string.step_calendar_detail, false, { granted(Manifest.permission.READ_CALENDAR) }) {
                askFor(Manifest.permission.READ_CALENDAR)
            },
            PermissionRow(R.string.step_photos_title, R.string.step_photos_detail, false, { granted(photosPermission) }) {
                askFor(photosPermission)
            },
            PermissionRow(R.string.step_notifications_title, R.string.step_notifications_detail, false,
                { Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS) }) {
                askFor(Manifest.permission.POST_NOTIFICATIONS)
            },
        )
        for (row in permissionRows) column.addSpaced(permissionCard(row), 12)
        permMessage = bodyText(getString(R.string.onb_perm_missing), 18f, secondary = false).apply {
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(themeColor(R.color.suus_pending))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            visibility = View.GONE
        }
        column.addSpaced(permMessage!!, 16)
        nav(R.string.onb_continue, {
            if (permissionRows.filter { it.required }.all { it.isDone() }) show(2, forward = true)
            else permMessage?.visibility = View.VISIBLE
        })
        refreshPermissions()
    }

    private fun permissionCard(row: PermissionRow): View = card().apply {
        row.box = this
        row.badge = TextView(context).apply {
            textSize = 20f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addSpaced(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(row.badge, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(titleText(getString(row.title), 21f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(14) })
        })
        row.chip = chip()
        addWrapped(row.chip, 10)
        addSpaced(bodyText(getString(row.detail), 17f), 8)
        row.button = bigButton(getString(R.string.onb_allow_one), ButtonKind.SECONDARY) { row.turnOn() }
        addSpaced(row.button, 14)
    }

    private fun refreshPermissions() {
        if (permissionRows.isEmpty()) return
        for (row in permissionRows) {
            val done = row.isDone()
            row.badge.text = if (done) "✓" else "!"
            row.badge.setTextColor(themeColor(if (done) R.color.suus_on_ok else R.color.suus_pending))
            row.badge.background = roundedBox(themeColor(if (done) R.color.suus_ok else R.color.suus_pending_soft), 22)
            when {
                done -> row.chip.setChip(getString(R.string.step_done), ChipKind.DONE)
                row.required -> row.chip.setChip(getString(R.string.onb_perm_required), ChipKind.NEXT)
                else -> row.chip.setChip(getString(R.string.onb_perm_optional), ChipKind.INFO)
            }
            row.button.visibility = if (done) View.GONE else View.VISIBLE
        }
        if (permissionRows.filter { it.required }.all { it.isDone() }) permMessage?.visibility = View.GONE
        // Nothing left to allow: the one big button is Continue.
        allowAllButton?.visibility = if (permissionRows.all { it.isDone() }) View.GONE else View.VISIBLE
    }

    private fun aboutPage() {
        column.addSpaced(stepper())
        column.addSpaced(heroHeader(getString(R.string.onb_about_title), getString(R.string.onb_about_short)), 16)
        column.addSpaced(bodyText(getString(R.string.onb_about_body), 18f), 14)
        val form = ProfileForm(this, createProfileStore(this))
        profileForm = form
        column.addSpaced(form.build(), 16)
        val error = bodyText("", 18f, secondary = false).apply {
            setTextColor(themeColor(R.color.suus_danger))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            visibility = View.GONE
        }
        column.addSpaced(error, 12)
        column.addSpaced(bodyText(getString(R.string.profile_voice_hint), 16f), 12)
        nav(R.string.onb_continue, {
            val problem = form.save()
            if (problem == null) show(3, forward = true) else { error.text = problem; error.visibility = View.VISIBLE }
        })
    }

    private fun stylePage() {
        column.addSpaced(stepper())
        column.addSpaced(heroHeader(getString(R.string.onb_style_head), getString(R.string.onb_style_title)), 16)
        column.addSpaced(bodyText(getString(R.string.onb_style_body), 18f), 14)
        customizeSections(column, scope) {}
        nav(R.string.onb_continue, { show(4, forward = true) })
    }

    private fun donePage() {
        column.addSpaced(stepper())
        column.addSpaced(heroHeader(getString(R.string.onb_done_title), getString(R.string.onb_done_short), getString(R.string.onb_done_eyebrow)), 16)
        column.addSpaced(card().apply { addSpaced(bodyText(getString(R.string.onb_done_body), 20f, secondary = false)) }, 16)
        nav(R.string.onb_finish, {
            val store = createSettingsStore(this)
            store.save(store.load().copy(onboarded = true))
            startActivity(Intent(this, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        })
    }

    // ---------- permissions ----------

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun serviceEnabled(): Boolean {
        val me = ComponentName(this, SuusAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }.any { ComponentName.unflattenFromString(it) == me }
    }

    /** All the dialogs in one go (Android shows them one after another), then the Accessibility switch. */
    private fun allowAll() {
        val missing = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.READ_CALENDAR)
            add(photosPermission)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { !granted(it) }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_ALL)
        else if (!serviceEnabled()) startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    /** If Android won't show the dialog again ("Don't allow" twice), open this app's settings page instead. */
    private fun askFor(permission: String) {
        val prefs = getSharedPreferences("setup", MODE_PRIVATE)
        val askedKey = "asked_$permission"
        val blocked = prefs.getBoolean(askedKey, false) && !ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
        if (blocked) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        } else {
            prefs.edit().putBoolean(askedKey, true).apply()
            ActivityCompat.requestPermissions(this, arrayOf(permission), REQ_ONE)
        }
    }

    private companion object {
        const val PAGES = 5
        const val KEY_PAGE = "page"
        const val REQ_ALL = 2
        const val REQ_ONE = 3
        fun ViewCompatHeadingOff(v: View) = androidx.core.view.ViewCompat.setAccessibilityHeading(v, false)
    }
}
