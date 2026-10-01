package com.quark.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class StringsTest {

    @Test
    fun russian_counts_take_the_right_form() {
        val ru = RussianStrings()
        assertEquals("1 трек", ru.tracks(1))
        assertEquals("2 трека", ru.tracks(2))
        assertEquals("5 треков", ru.tracks(5))
        assertEquals("11 треков", ru.tracks(11))
        assertEquals("14 треков", ru.tracks(14))
        assertEquals("21 трек", ru.tracks(21))
        assertEquals("22 трека", ru.tracks(22))
        assertEquals("111 треков", ru.tracks(111))
        assertEquals("0 треков", ru.tracks(0))
    }

    @Test
    fun polish_counts_take_the_right_form() {
        assertEquals("1 utwór", PolishStrings().tracks(1))
        assertEquals("3 utwory", PolishStrings().tracks(3))
        assertEquals("5 utworów", PolishStrings().tracks(5))
        assertEquals("12 utworów", PolishStrings().tracks(12))
        assertEquals("22 utwory", PolishStrings().tracks(22))
    }

    @Test
    fun every_language_builds_and_is_its_own() {
        Language.entries.forEach { language ->
            val strings = language.strings()
            if (language != Language.English) {
                assertNotEquals(Strings().appTitle, strings.appTitle, "${language.code} kept the English title")
            }
        }
    }

    @Test
    fun the_setting_wins_over_the_system_and_unknown_codes_fall_back() {
        assertEquals(Language.Russian, Language.resolve("ru"))
        assertEquals(Language.German, Language.resolve("de-AT"))
        assertEquals(Language.of(systemLanguageCode()) ?: Language.English, Language.resolve("xx"))
    }
}
