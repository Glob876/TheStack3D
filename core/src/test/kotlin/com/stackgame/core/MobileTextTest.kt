package com.stackgame.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MobileTextTest {
    @Test fun missingOrUnknownSavedLanguageDefaultsToRussian() {
        assertEquals(GameLanguage.RUSSIAN, GameLanguage.fromCode(""))
        assertEquals(GameLanguage.RUSSIAN, GameLanguage.fromCode("unknown"))
        assertEquals(GameLanguage.ENGLISH, GameLanguage.fromCode("en"))
    }

    @Test fun bitmapFontCoversEveryLocalizedScreenAndGuide() {
        val atlas = File("../android/src/main/assets/fonts/stack-sans.fnt").readText()
        val supported = Regex("char id=(\\d+)").findAll(atlas).map { it.groupValues[1].toInt().toChar() }.toSet()
        val texts = UiText.entries.flatMap { listOf(it.ru, it.en) } +
            GlobGuide.pages.flatMap { listOf(it.ru, it.en) } + listOf("РУССКИЙ", "ENGLISH", "ЯЗЫК / LANGUAGE", "0123456789 / x.")
        val missing = texts.joinToString("").toSet() - supported
        assertTrue(missing.isEmpty(), "Font is missing glyphs: $missing")
    }
}
