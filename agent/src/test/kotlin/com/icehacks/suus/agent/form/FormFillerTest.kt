package com.icehacks.suus.agent.form

import com.icehacks.suus.agent.DraftLedger
import com.icehacks.suus.agent.Profile
import com.icehacks.suus.agent.SavedPreference
import com.icehacks.suus.core.ActionResult
import com.icehacks.suus.core.Approval
import com.icehacks.suus.core.Bounds
import com.icehacks.suus.core.DeviceAction
import com.icehacks.suus.core.ScreenController
import com.icehacks.suus.core.ScreenSnapshot
import com.icehacks.suus.core.ScrollDirection
import com.icehacks.suus.core.UiNode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A tiny simulated Chrome page shaped like the volunteer form (labels in hints, radios inside a
 * fieldset node, a <select> that opens a popup window, the submit button below the fold).
 */
class FakeChromeForm : ScreenController {
    val texts = mutableMapOf("email" to "", "name" to "", "mobile" to "")
    var shift: String? = null
    var role: String? = null
    var confirmBy: String? = null
    var scrolled = false
    var popupOpen = false
    var submittedPage = false
    /** Show the top of the confirmation radio group (first option only) at the bottom of the unscrolled page. */
    var partialGroupAtFold = false
    val performed = mutableListOf<DeviceAction>()

    private val shifts = listOf("Sunday, November 15, 9:00 AM to 12:00 PM", "Monday, November 16, 1:00 PM to 4:00 PM")
    private val roles = listOf("Choose a volunteer role", "Welcome table", "Food serving", "Setup crew", "Cleanup crew")
    private val methods = listOf("Text message", "Phone call", "Email")

    private var y = 0
    private fun node(id: String, cls: String, text: String?, parent: String = "form", hint: String? = null,
                     editable: Boolean = false, checkable: Boolean = false, checked: Boolean = false, clickable: Boolean = false): UiNode {
        y += 100
        return UiNode(id, parent, "android.widget.$cls", text, null, hint, null, null, Bounds(0, y, 1000, y + 80),
            clickable, editable, false, checkable, checked, false, false, true)
    }

    override suspend fun snapshot(): ScreenSnapshot {
        y = 0
        if (submittedPage) {
            val lines = listOf("Harvest Festival Volunteers", "You're signed up", "Volunteer confirmation: VOL-123456",
                "Email address", texts["email"]!!, "Your name", texts["name"]!!, "Shift", shift ?: "", "Preferred volunteer role", role ?: "",
                "Mobile number", texts["mobile"]!!, "How should we send your confirmation?", confirmBy ?: "")
            return ScreenSnapshot("com.android.chrome", "Signup confirmed", 0L,
                listOf(node("url", "EditText", "saythegoal-forms-nisith.vercel.app/volunteer/confirmed.html", parent = "chrome", editable = true)
                    .copy(viewId = "com.android.chrome:id/url_bar")) + lines.mapIndexed { i, t -> node("k$i", "TextView", t, parent = "web") })
        }
        if (popupOpen) {
            return ScreenSnapshot("com.android.chrome", null, 0L, roles.mapIndexed { i, r ->
                node("p$i", "CheckedTextView", r, parent = "list", checkable = true, checked = (role ?: roles[0]) == r, clickable = true)
            })
        }
        val n = mutableListOf(
            node("url", "EditText", "saythegoal-forms-nisith.vercel.app/volunteer/", parent = "chrome", editable = true).copy(viewId = "com.android.chrome:id/url_bar"),
            node("web", "WebView", "Volunteer signup", parent = "chrome"),
            node("form", "View", null, parent = "web"),
        )
        if (!scrolled) {
            n += node("t1", "TextView", "Harvest Festival Volunteers")
            n += node("l1", "View", "Email address (required)")
            n += node("e1", "EditText", texts["email"], hint = "Email address (required)", editable = true, clickable = true)
            n += node("l2", "View", "Your name (required)")
            n += node("e2", "EditText", texts["name"], hint = "Your name (required)", editable = true, clickable = true)
            n += node("fs", "View", "Shift (required)")
            shifts.forEachIndexed { i, s -> n += node("s$i", "RadioButton", s, parent = "fs", checkable = true, checked = shift == s, clickable = true) }
            n += node("l3", "View", "Preferred volunteer role (required)")
            n += node("sel", "View", "Preferred volunteer role (required)", clickable = true)
            if (partialGroupAtFold) {
                n += node("fc", "View", "How should we send your confirmation? (required)")
                n += node("c0", "RadioButton", methods[0], parent = "fc", checkable = true, checked = confirmBy == methods[0], clickable = true)
            }
        } else {
            n += node("l3", "View", "Preferred volunteer role (required)")
            n += node("sel", "View", "Preferred volunteer role (required)", clickable = true)
            n += node("l4", "View", "Mobile number (required)")
            n += node("e3", "EditText", texts["mobile"], hint = "Mobile number (required)", editable = true, clickable = true)
            n += node("fc", "View", "How should we send your confirmation? (required)")
            methods.forEachIndexed { i, m -> n += node("c$i", "RadioButton", m, parent = "fc", checkable = true, checked = confirmBy == m, clickable = true) }
            n += node("go", "Button", "Sign up for shift", clickable = true)
        }
        return ScreenSnapshot("com.android.chrome", "Chrome", 0L, n)
    }

    override suspend fun perform(action: DeviceAction, on: ScreenSnapshot, approval: Approval?): ActionResult {
        performed += action
        val before = snapshot()
        when (action) {
            is DeviceAction.EnterText -> {
                val key = mapOf("e1" to "email", "e2" to "name", "e3" to "mobile")[action.nodeId] ?: return ActionResult.TargetNotFound(action.nodeId)
                texts[key] = action.text
            }
            is DeviceAction.Tap -> when {
                action.isCommit -> if (approval == null) return ActionResult.NotAllowed("no approval") else submittedPage = true
                action.nodeId == "sel" -> popupOpen = true
                action.nodeId.startsWith("p") -> { role = roles[action.nodeId.drop(1).toInt()]; popupOpen = false }
                action.nodeId.startsWith("s") -> shift = shifts[action.nodeId.drop(1).toInt()]
                action.nodeId.startsWith("c") -> confirmBy = methods[action.nodeId.drop(1).toInt()]
                else -> return ActionResult.TargetNotFound(action.nodeId)
            }
            DeviceAction.Back -> popupOpen = false
            is DeviceAction.Scroll -> scrolled = action.direction == ScrollDirection.DOWN
            else -> {}
        }
        val after = snapshot()
        return ActionResult.Done(after, after != before)
    }
}

class FormFillerTest {
    private val emailPref = SavedPreference("event_registration", "confirmation_method", "Email", "Email. Remember that.", 1L)

    private fun filler(page: FakeChromeForm, ledger: DraftLedger = DraftLedger()) =
        FormFiller(page, ledger, { a, s, _, _ -> page.perform(a, s, null) })

    @Test
    fun firstPassFillsKnownFieldsAndReportsWhatsNeeded() = runTest {
        val page = FakeChromeForm()
        val ledger = DraftLedger()
        val report = filler(page, ledger).fill(Profile(), listOf(emailPref), emptyMap())

        assertEquals("alex.rivera.test@example.com", page.texts["email"])
        assertEquals("Alex Rivera", page.texts["name"])
        assertEquals("555-0142", page.texts["mobile"])
        assertEquals("Email", page.confirmBy) // saved preference, offered as the 3rd option
        assertEquals(null, page.shift)        // needs the calendar choice
        assertEquals(null, page.role)         // needs the user's answer
        assertFalse(page.popupOpen)           // the dropdown was opened to read options, then closed
        assertTrue(report.needs.any { it.startsWith("Shift") && it.contains("Sunday, November 15") })
        assertTrue(report.needs.any { it.startsWith("Preferred volunteer role") && it.contains("Food serving") && !it.contains("Choose a volunteer role") })
        assertEquals("go", report.submitNodeId)
        assertEquals("Sign up for shift", report.submitLabel)
        assertTrue(report.describe().contains("call submit"))
        assertTrue(page.performed.none { it is DeviceAction.Tap && (it.nodeId == "go" || it.isCommit) }) // never submits
        assertTrue(ledger.all().any { it.kind == DraftLedger.Kind.TEXT && it.afterText == "Alex Rivera" })
    }

    @Test
    fun secondPassWithAnswersCompletesTheForm() = runTest {
        val page = FakeChromeForm()
        val f = filler(page)
        f.fill(Profile(), listOf(emailPref), emptyMap())
        page.scrolled = false // the agent might be back at the top
        val report = f.fill(Profile(), listOf(emailPref), mapOf("Shift" to "Sunday, November 15", "Preferred volunteer role" to "Food serving"))

        assertEquals("Sunday, November 15, 9:00 AM to 12:00 PM", page.shift)
        assertEquals("Food serving", page.role)
        assertTrue(report.needs.isEmpty())
        assertEquals("Food serving", report.values["Preferred volunteer role"])
        assertEquals("Email", report.values["How should we send your confirmation"])
        assertEquals(6, report.values.size) // every field now holds a value
    }

    @Test
    fun preferenceNotOfferedFallsBackToAsking() = runTest {
        val page = FakeChromeForm()
        val textOnlyPref = SavedPreference("event_registration", "confirmation_method", "Carrier pigeon", "x", 1L)
        val report = filler(page).fill(Profile(), listOf(textOnlyPref), emptyMap())
        assertEquals(null, page.confirmBy)
        assertTrue(report.needs.any { it.contains("isn't offered here") })
    }

    @Test
    fun dropdownValueIsRememberedSinceChromeHidesIt() = runTest {
        val page = FakeChromeForm()
        val f = filler(page)
        f.fill(Profile(), emptyList(), mapOf("Preferred volunteer role" to "Setup crew"))
        val opens = page.performed.count { it == DeviceAction.Tap("sel") }
        val report = f.fill(Profile(), emptyList(), mapOf("Preferred volunteer role" to "Setup crew"))
        assertEquals("Setup crew", page.role)
        assertEquals(opens, page.performed.count { it == DeviceAction.Tap("sel") }) // not reopened
        assertTrue(report.filled.any { it.startsWith("Preferred volunteer role = Setup crew") })
    }

    @Test
    fun radioGroupCutOffAtTheFoldIsRetriedAfterScrolling() = runTest {
        val page = FakeChromeForm().apply { partialGroupAtFold = true }
        val report = filler(page).fill(Profile(), listOf(emailPref), emptyMap())
        assertEquals("Email", page.confirmBy)
        assertTrue(report.failed.toString(), report.failed.isEmpty())
    }

    @Test
    fun anOptionThatReallyIsntOfferedFailsAfterOneRetry() = runTest {
        val page = FakeChromeForm().apply { partialGroupAtFold = true }
        val report = filler(page).fill(Profile(), emptyList(), mapOf("How should we send your confirmation" to "Carrier pigeon"))
        assertEquals(null, page.confirmBy)
        assertTrue(report.failed.toString(), report.failed.any { it.contains("How should we send your confirmation") })
    }

    @Test
    fun resetForgetsDropdownChoices() = runTest {
        val page = FakeChromeForm()
        val f = filler(page)
        f.fill(Profile(), emptyList(), mapOf("Preferred volunteer role" to "Setup crew"))
        val fresh = FakeChromeForm() // a new tab of the same form
        val f2 = FormFiller(fresh, DraftLedger(), { a, s, _, _ -> fresh.perform(a, s, null) })
        f.reset()
        val report = f2.fill(Profile(), emptyList(), emptyMap())
        assertTrue(report.needs.any { it.startsWith("Preferred volunteer role") })
    }
}
