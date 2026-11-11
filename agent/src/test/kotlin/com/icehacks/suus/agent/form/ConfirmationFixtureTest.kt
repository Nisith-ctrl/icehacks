package com.icehacks.suus.agent.form

import com.icehacks.suus.core.ScreenSnapshot
import com.icehacks.suus.core.SuusJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Real confirmation pages recorded in Chrome on the emulator during the IC3 / IC4 runs (2026-11-11). */
class ConfirmationFixtureTest {
    private fun fixture(name: String): ScreenSnapshot =
        SuusJson.decodeFromString(File("../fixtures/screens/$name.json").readText())

    @Test
    fun libraryConfirmationIsVerified() {
        val r = ConfirmationCheck.check(fixture("chrome-library-confirmation"), linkedMapOf(
            "Session" to "Friday, November 13, 2:00 PM to 3:30 PM",
            "Full name" to "Alex Rivera", "Email" to "alex.rivera.test@example.com",
            "Phone" to "555-0142", "Confirmation method" to "Email",
        ))
        assertTrue(r.describe(), r.confirmed)
        assertEquals("LIB-282497", r.number)
        assertTrue(r.missing.toString(), r.missing.isEmpty())
    }

    @Test
    fun volunteerConfirmationIsVerified() {
        val r = ConfirmationCheck.check(fixture("chrome-volunteer-confirmation"), linkedMapOf(
            "Shift" to "Saturday, November 14, 9:00 AM to 12:00 PM",
            "Preferred volunteer role" to "Setup crew", "Email address" to "alex.rivera.test@example.com",
        ))
        assertTrue(r.describe(), r.confirmed)
        assertEquals("VOL-730060", r.number)
        assertTrue(r.missing.toString(), r.missing.isEmpty())
    }

    @Test
    fun valuesThatWereNotSubmittedAreReportedMissing() {
        val r = ConfirmationCheck.check(fixture("chrome-library-confirmation"), linkedMapOf(
            "Session" to "Thursday, November 12, 10:00 AM to 11:30 AM",
        ))
        assertFalse(r.missing.isEmpty())
    }
}
