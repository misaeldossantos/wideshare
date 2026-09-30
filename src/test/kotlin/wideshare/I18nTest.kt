package wideshare

import wideshare.core.I18n
import wideshare.core.Language
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class I18nTest {
    private fun table(lang: Language) = Properties().also { p ->
        I18n::class.java.getResourceAsStream("/i18n/${lang.code}.properties")!!.reader(Charsets.UTF_8).use(p::load)
    }

    private fun placeholders(text: String) = Regex("""\{\d+}""").findAll(text).map { it.value }.toSortedSet()

    @Test
    fun everyLanguageHasTheSameKeysAndPlaceholders() {
        val en = table(Language.EN)
        for (lang in Language.entries) {
            val other = table(lang)
            assertEquals(en.stringPropertyNames(), other.stringPropertyNames(), "keys of ${lang.code}")
            en.stringPropertyNames().forEach { key ->
                assertEquals(placeholders(en.getProperty(key)), placeholders(other.getProperty(key)), "${lang.code}: $key")
            }
        }
    }

    @Test
    fun lookupFollowsTheCurrentLanguageAndFillsPlaceholders() {
        val saved = I18n.language
        try {
            I18n.language = Language.PT
            assertEquals("Conectado a PC", I18n.t("status.connected", "PC"))
            I18n.language = Language.ES
            assertEquals("Conectado a PC", I18n.t("status.connected", "PC"))
            I18n.language = Language.EN
            assertEquals("Connected to PC", I18n.t("status.connected", "PC"))
            assertTrue(I18n.t("missing.key") == "missing.key")
        } finally {
            I18n.language = saved
        }
    }
}
