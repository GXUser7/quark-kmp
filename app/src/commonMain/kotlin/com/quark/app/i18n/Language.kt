package com.quark.app.i18n

/**
 * The interface languages, as the Flutter build shipped them
 * (`lib/l10n/app_*.arb`, plus Japanese and Korean, which it only had as
 * generated Dart). [nativeName] is what the language picker shows, so a
 * user who cannot read the current language can still find their own.
 */
enum class Language(val code: String, val nativeName: String, val strings: () -> Strings) {
    English("en", "English", ::Strings),
    Russian("ru", "Русский", ::RussianStrings),
    German("de", "Deutsch", ::GermanStrings),
    Spanish("es", "Español", ::SpanishStrings),
    French("fr", "Français", ::FrenchStrings),
    Italian("it", "Italiano", ::ItalianStrings),
    Japanese("ja", "日本語", ::JapaneseStrings),
    Korean("ko", "한국어", ::KoreanStrings),
    Polish("pl", "Polski", ::PolishStrings),
    Portuguese("pt", "Português", ::PortugueseStrings),
    Turkish("tr", "Türkçe", ::TurkishStrings),
    Chinese("zh", "中文", ::ChineseStrings);

    companion object {
        fun of(code: String?): Language? = entries.firstOrNull { it.code == code?.lowercase()?.take(2) }

        /** The language set in the settings, or the system's if that is null or not one of ours. */
        fun resolve(setting: String?): Language = of(setting) ?: of(systemLanguageCode()) ?: English
    }
}

/** The two-letter code of the language the operating system is set to. */
expect fun systemLanguageCode(): String
