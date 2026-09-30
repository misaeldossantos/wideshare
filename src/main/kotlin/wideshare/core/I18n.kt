package wideshare.core

import java.util.Locale
import java.util.Properties

/** Languages the interface is translated into; [code] names the file in `resources/i18n`. */
enum class Language(val code: String, val label: String) {
    PT("pt", "Português"),
    EN("en", "English"),
    ES("es", "Español");

    companion object {
        fun fromCode(code: String?): Language? = entries.firstOrNull { it.code == code }

        /** The system language when it is supported, English otherwise. */
        fun detect(): Language = fromCode(Locale.getDefault().language) ?: EN
    }
}

/** Looks up user-facing text in the current [language]; a missing key falls back to English, then to the key itself. */
object I18n {
    @Volatile var language: Language = Language.detect()

    private val tables = Language.entries.associateWith { load(it) }

    private fun load(lang: Language): Properties {
        val p = Properties()
        I18n::class.java.getResourceAsStream("/i18n/${lang.code}.properties")?.reader(Charsets.UTF_8)?.use(p::load)
        return p
    }

    /** Text for [key]; each `{0}`, `{1}`… placeholder is replaced by the matching entry of [args]. */
    fun t(key: String, vararg args: Any?): String {
        var text = tables.getValue(language).getProperty(key) ?: tables.getValue(Language.EN).getProperty(key) ?: key
        args.forEachIndexed { i, a -> text = text.replace("{$i}", a.toString()) }
        return text
    }
}

fun tr(key: String, vararg args: Any?): String = I18n.t(key, *args)
