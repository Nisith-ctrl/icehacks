package com.icehacks.suus.agent.form

import com.icehacks.suus.agent.Profile
import com.icehacks.suus.agent.SavedPreference
import com.icehacks.suus.core.ScreenSnapshot
import com.icehacks.suus.core.SuusJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private fun fixture(name: String): ScreenSnapshot =
    SuusJson.decodeFromString(File("../fixtures/screens/$name.json").readText())

/** Real snapshots of the live test forms in Chrome (stg_pixel_35, Chrome 124). */
class FormReaderTest {

    @Test
    fun libraryFormEmpty() {
        val form = FormReader.read(fixture("chrome-library-empty"))
        assertEquals("Intro to 3D Printing Workshop", form.title)
        val byLabel = form.fields.associateBy { it.label }
        assertEquals(listOf("Session", "Full name", "Email", "Phone", "Confirmation method"), form.fields.map { it.label })
        assertEquals(FieldKind.CHOICE, byLabel["Session"]!!.kind)
        assertEquals(2, byLabel["Session"]!!.options.size)
        assertTrue(byLabel["Session"]!!.options[0].label.startsWith("Friday, November 13"))
        assertEquals(FieldKind.TEXT, byLabel["Full name"]!!.kind)
        assertEquals("n22", byLabel["Full name"]!!.nodeId)
        assertNull(byLabel["Full name"]!!.value)
        assertTrue(form.fields.all { it.required })
        assertNull(form.submitNodeId) // "Register" is below the fold in this snapshot
    }

    @Test
    fun libraryFormFilledAndScrolled() {
        val form = FormReader.read(fixture("chrome-library-filled"))
        val byLabel = form.fields.associateBy { it.label }
        assertEquals("Alex Rivera", byLabel["Full name"]!!.value)
        assertEquals("alex.rivera.test@example.com", byLabel["Email"]!!.value)
        assertEquals("555-0142", byLabel["Phone"]!!.value)
        assertTrue(byLabel["Session"]!!.value!!.startsWith("Saturday"))
        assertEquals("Email", byLabel["Confirmation method"]!!.value)
        assertEquals(listOf("Email", "Text message"), byLabel["Confirmation method"]!!.options.map { it.label })
        assertEquals("Register", form.submitLabel)
    }

    @Test
    fun volunteerFormDifferentLabelsAndOrder() {
        val form = FormReader.read(fixture("chrome-volunteer-empty"))
        assertEquals(listOf("Email address", "Your name", "Shift", "Preferred volunteer role", "Mobile number"), form.fields.map { it.label })
        val role = form.fields.first { it.label == "Preferred volunteer role" }
        assertEquals(FieldKind.SELECT, role.kind)
        assertEquals("n26", role.nodeId)
        assertTrue(role.required)
        assertEquals(FieldKind.CHOICE, form.fields.first { it.label == "Shift" }.kind)
    }

    @Test
    fun openDropdownOptions() {
        val opts = FormReader.selectOptions(fixture("chrome-volunteer-select-open"))
        assertEquals(listOf("Choose a volunteer role", "Welcome table", "Food serving", "Setup crew", "Cleanup crew"), opts.map { it.label })
        assertTrue(opts[0].checked)
    }

    @Test
    fun labelCleaning() {
        assertEquals("Full name", FormReader.clean("Full name (required)"))
        assertEquals("How should we send your confirmation", FormReader.clean("How should we send your confirmation? (required)"))
        assertEquals("Email", FormReader.clean("Email *"))
    }
}

class FormMapperTest {
    private val profile = Profile()
    private val emailPref = SavedPreference("event_registration", "confirmation_method", "Email", "Email. Remember that.", 1L)

    @Test
    fun libraryPlanUsesProfileCalendarAndAsks() {
        val plans = FormMapper.plan(FormReader.read(fixture("chrome-library-filled")), profile, emptyList())
            .associateBy { it.field.label }
        assertEquals(Source.CHOSEN_OPTION, plans["Session"]!!.source)
        assertEquals(Source.PROFILE, plans["Full name"]!!.source)
        assertEquals("Alex Rivera", plans["Full name"]!!.target)
        assertEquals("alex.rivera.test@example.com", plans["Email"]!!.target)
        assertEquals("555-0142", plans["Phone"]!!.target)
        assertEquals(Source.ASK, plans["Confirmation method"]!!.source) // no preference saved yet
        assertTrue(plans["Full name"]!!.done)
    }

    @Test
    fun savedPreferenceAppliesWhenTheFormOffersIt() {
        val plans = FormMapper.plan(FormReader.read(fixture("chrome-library-filled")), profile, listOf(emailPref))
            .associateBy { it.field.label }
        assertEquals(Source.PREFERENCE, plans["Confirmation method"]!!.source)
        assertEquals("Email", plans["Confirmation method"]!!.target)
    }

    @Test
    fun savedPreferenceSkippedWhenNotOffered() {
        val textOnly = FormModel("x", listOf(
            FormField("Confirmation method", FieldKind.CHOICE, true, null, listOf(FormOption("Text message", "n1", false), FormOption("Phone call", "n2", false))),
        ), null, null)
        assertEquals(Source.ASK, FormMapper.plan(textOnly, profile, listOf(emailPref)).single().source)
    }

    @Test
    fun savedPreferenceIgnoredForUnrelatedQuestion() {
        val other = FormModel("x", listOf(
            FormField("Newsletter format", FieldKind.CHOICE, true, null, listOf(FormOption("Email", "n1", false), FormOption("Paper", "n2", false))),
        ), null, null)
        assertEquals(Source.ASK, FormMapper.plan(other, profile, listOf(emailPref)).single().source)
    }

    @Test
    fun volunteerPlanMapsRenamedFields() {
        val plans = FormMapper.plan(FormReader.read(fixture("chrome-volunteer-empty")), profile, listOf(emailPref))
            .associateBy { it.field.label }
        assertEquals("alex.rivera.test@example.com", plans["Email address"]!!.target)
        assertEquals("Alex Rivera", plans["Your name"]!!.target)
        assertEquals("555-0142", plans["Mobile number"]!!.target)
        assertEquals(Source.CHOSEN_OPTION, plans["Shift"]!!.source)
        assertEquals(Source.ASK, plans["Preferred volunteer role"]!!.source)
    }

    @Test
    fun answersWinAndMatchOptionsLoosely() {
        val plans = FormMapper.plan(
            FormReader.read(fixture("chrome-volunteer-empty")), profile, emptyList(),
            answers = mapOf("shift" to "Sunday", "Preferred volunteer role (required)" to "Food serving"),
        ).associateBy { it.field.label }
        assertEquals(Source.ANSWER, plans["Shift"]!!.source)
        assertTrue(plans["Shift"]!!.target!!.startsWith("Sunday, November 15"))
        assertEquals("Food serving", plans["Preferred volunteer role"]!!.target)
    }

    @Test
    fun choiceAndLabelMatching() {
        assertTrue(sameChoice("Email", "email"))
        assertTrue(sameChoice("Saturday, November 14, 2:00 PM to 3:30 PM", "Saturday, November 14"))
        assertFalse(sameChoice("Email", "Text message"))
        assertTrue(sameLabel("Session", "Session (required)"))
        assertFalse(sameLabel("Email", "Phone"))
    }
}

class ConfirmationCheckTest {
    /** Built from the live confirmation page's markup (fixtures/forms/library/confirmed.html). */
    private fun page(vararg texts: String) = ScreenSnapshot("com.android.chrome", "Registration confirmed", 0L,
        texts.mapIndexed { i, t ->
            com.icehacks.suus.core.UiNode("n$i", null, "android.widget.TextView", t, null, null, null, null,
                com.icehacks.suus.core.Bounds(0, i * 50, 1000, i * 50 + 40), false, false, false, false, false, false, false, true)
        })

    private val submitted = linkedMapOf(
        "Session" to "Saturday, November 14, 2:00 PM to 3:30 PM",
        "Full name" to "Alex Rivera", "Email" to "alex.rivera.test@example.com",
        "Phone" to "555-0142", "Confirmation method" to "Email",
    )

    @Test
    fun verifiedConfirmation() {
        val r = ConfirmationCheck.check(page(
            "Intro to 3D Printing Workshop", "Registration confirmed", "Confirmation number: LIB-482913",
            "Session", "Saturday, November 14, 2:00 PM to 3:30 PM", "Full name", "Alex Rivera",
            "Email", "alex.rivera.test@example.com", "Phone", "555-0142", "Confirmation method", "Email",
        ), submitted)
        assertTrue(r.describe(), r.confirmed)
        assertEquals("LIB-482913", r.number)
        assertTrue(r.missing.isEmpty())
    }

    @Test
    fun formStillShowingIsNotAConfirmation() {
        val r = ConfirmationCheck.check(page("Intro to 3D Printing Workshop", "Please fill in Phone", "Register"), submitted)
        assertFalse(r.confirmed)
        assertTrue(r.describe().contains("not sure"))
    }

    @Test
    fun noRegistrationFound() {
        val r = ConfirmationCheck.check(page("No registration found"), submitted)
        assertFalse(r.confirmed)
    }
}

/** Real scrolled volunteer form: radio ids like "send-email" must not look like a Send button. */
class CommitDetectionOnFormsTest {
    private val snap = fixture("chrome-volunteer-scrolled")
    private fun byText(t: String) = snap.nodes.first { it.text == t }

    @Test
    fun confirmationRadiosAreNotCommits() {
        for (t in listOf("Text message", "Phone call", "Email")) {
            assertFalse(t, com.icehacks.suus.agent.AgentLoop.looksLikeCommit(snap, byText(t)))
        }
    }

    @Test
    fun signUpButtonIsACommit() {
        assertTrue(com.icehacks.suus.agent.AgentLoop.looksLikeCommit(snap, byText("Sign up for shift")))
    }

    @Test
    fun scrolledVolunteerFormReadsTheConfirmationGroup() {
        val f = FormReader.read(snap).fields.first { it.label == "How should we send your confirmation" }
        assertEquals(listOf("Text message", "Phone call", "Email"), f.options.map { it.label })
        assertEquals("Sign up for shift", FormReader.read(snap).submitLabel)
    }
}

