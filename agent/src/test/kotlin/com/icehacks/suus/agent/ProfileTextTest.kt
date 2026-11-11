package com.icehacks.suus.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileTextTest {

    @Test
    fun spokenEmails() {
        assertEquals("alex.rivera@gmail.com", ProfileText.email("Alex dot Rivera at gmail dot com"))
        assertEquals("guru_b@outlook.com", ProfileText.email("my email is guru underscore b at outlook dot com."))
        assertEquals("sam@example.org", ProfileText.email("sam@example.org"))
        assertNull(ProfileText.email("I don't know"))
    }

    @Test
    fun spokenPhones() {
        assertEquals("5550142", ProfileText.phone("555 0142"))
        assertEquals("5550142", ProfileText.phone("five five five zero one four two"))
        assertEquals("+14155550199", ProfileText.phone("+1 415 555 0199"))
        assertNull(ProfileText.phone("call me later"))
    }

    @Test
    fun spokenNames() {
        assertEquals("Alex Rivera", ProfileText.name("my name is alex rivera"))
        assertEquals("Guru", ProfileText.name("Guru."))
        assertNull(ProfileText.name("well I think it is probably something like this one"))
    }

    @Test
    fun questionsAboutDetails() {
        assertEquals(ProfileDetail.EMAIL, ProfileText.detailIn("What's your email address?"))
        assertEquals(ProfileDetail.PHONE, ProfileText.detailIn("What mobile number should I use?"))
        assertEquals(ProfileDetail.NAME, ProfileText.detailIn("What is your full name?"))
        assertNull(ProfileText.detailIn("Which volunteer role would you like?"))
    }

    @Test
    fun blankDetailsAreAskedFor() {
        val prompt = AgentPrompt.system(Profile(fullName = "Guru", email = "", phone = ""))
        assert(prompt.contains("name \"Guru\""))
        assert(prompt.contains("email unknown (ask the user)"))
    }

    @Test
    fun configPrefersTheSavedProfile() {
        val saved = object : ProfileStore {
            var p = Profile("Guru", "guru@example.com", "5551234")
            override fun load() = p
            override fun save(profile: Profile) { p = profile }
        }
        assertEquals("guru@example.com", AgentConfig(profileStore = saved).currentProfile().email)
        assertEquals("alex.rivera.test@example.com", AgentConfig().currentProfile().email)
    }
}
