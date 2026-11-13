package com.icehacks.suus.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserSettingsTest {

    private val profile = Profile("Alex Rivera", "alex.rivera.test@example.com", "555-0142")

    @Test
    fun defaultsMatchTheOldBehaviour() {
        val s = AgentConfig().currentSettings()
        assertEquals(Tone.FRIENDLY, s.tone)
        assertEquals(Detail.MAIN_STEPS, s.detail)
        assertTrue(AgentPrompt.system(profile).contains("warm, upbeat helper"))
    }

    @Test
    fun toneAndDetailReachThePrompt() {
        val calm = AgentPrompt.system(profile, SuusSettings(tone = Tone.CALM, detail = Detail.EVERY_STEP))
        assertTrue(calm.contains("calm and plain"))
        assertTrue(calm.contains("every caption is read aloud"))
        assertFalse(calm.contains("warm, upbeat"))
        val brief = AgentPrompt.system(profile, SuusSettings(tone = Tone.BRIEF, detail = Detail.QUIET))
        assertTrue(brief.contains("as brief as possible"))
        assertTrue(brief.contains("little talk"))
    }

    @Test
    fun configReadsTheSavedChoicesEachTime() {
        val store = object : SettingsStore {
            var s = SuusSettings()
            override fun load() = s
            override fun save(settings: SuusSettings) { s = settings }
        }
        val config = AgentConfig(settingsStore = store)
        assertEquals(Tone.FRIENDLY, config.currentSettings().tone)
        store.save(SuusSettings(tone = Tone.BRIEF))
        assertEquals(Tone.BRIEF, config.currentSettings().tone) // no restart needed
    }

    @Test
    fun acknowledgementsFollowTheTone() {
        assertTrue(SuusVoiceLines.acks(Tone.BRIEF).all { it.length <= 6 })
        assertTrue(SuusVoiceLines.acks(Tone.CALM).none { it.contains("!") })
        assertTrue(SuusVoiceLines.acks(Tone.FRIENDLY).any { it.contains("!") })
    }
}
