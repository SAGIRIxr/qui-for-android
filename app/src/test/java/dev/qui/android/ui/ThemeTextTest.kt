package dev.qui.android.ui

import dev.qui.android.ui.theme.QuiThemes
import dev.qui.android.ui.theme.themeText
import org.junit.Assert.*
import org.junit.Test

class ThemeTextTest {
    @Test fun `every shipped theme has localized name and description resources`() {
        val texts = QuiThemes.map { theme ->
            requireNotNull(themeText(theme.id)) { "Missing theme translations: ${theme.id}" }
        }
        assertEquals(QuiThemes.size, texts.map { it.name }.toSet().size)
        assertEquals(QuiThemes.size, texts.map { it.description }.toSet().size)
        texts.forEach {
            assertTrue(it.name != 0)
            assertTrue(it.description != 0)
        }
    }

    @Test fun `unknown upstream themes can still fall back to their supplied metadata`() {
        assertNull(themeText("future-upstream-theme"))
    }
}
