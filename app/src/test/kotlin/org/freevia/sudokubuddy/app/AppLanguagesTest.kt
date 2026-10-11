package org.freevia.sudokubuddy.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppLanguagesTest {
    @Test
    fun `regional device tags resolve to the matching bundled language`() {
        assertEquals("de", AppLanguages.matchingTag("de-AT"))
        assertEquals("uk", AppLanguages.matchingTag("uk-UA"))
        assertEquals("fr", AppLanguages.matchingTag("fr-CA"))
    }

    @Test
    fun `device language has no explicit selection`() {
        assertNull(AppLanguages.matchingTag(""))
        assertNull(AppLanguages.matchingTag("zz"))
    }

    @Test
    fun `Chinese scripts and regions select the correct written variant`() {
        assertEquals("zh-TW", AppLanguages.matchingTag("zh-Hant"))
        assertEquals("zh-TW", AppLanguages.matchingTag("zh-HK"))
        assertEquals("zh-CN", AppLanguages.matchingTag("zh-Hans-SG"))
    }

    @Test
    fun `Portuguese regions and Norwegian alias keep the intended language`() {
        assertEquals("pt-BR", AppLanguages.matchingTag("pt-BR"))
        assertEquals("pt", AppLanguages.matchingTag("pt-PT"))
        assertEquals("nb", AppLanguages.matchingTag("no-NO"))
    }

    @Test
    fun `device preference list uses the first supported language`() {
        assertEquals("fr", AppLanguages.matchingTag("zz,fr-CA,de-DE"))
        assertEquals("en", AppLanguages.matchingTag("en-US,fr-CA"))
    }

    @Test
    fun `legacy Android language codes resolve to modern picker codes`() {
        assertEquals("he", AppLanguages.matchingTag("iw-IL"))
        assertEquals("id", AppLanguages.matchingTag("in-ID"))
    }
}
