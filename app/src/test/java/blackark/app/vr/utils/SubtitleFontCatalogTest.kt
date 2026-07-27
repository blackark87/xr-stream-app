package blackark.app.vr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleFontCatalogTest {

    @Test
    fun `legacy font family values migrate to stable system family ids`() {
        assertEquals(
            SubtitleFontCatalog.DEFAULT_FONT_ID,
            SubtitleFontCatalog.normalizePersistedId("SansSerif"),
        )
        assertEquals(
            SubtitleFontCatalog.SERIF_FONT_ID,
            SubtitleFontCatalog.normalizePersistedId("Serif"),
        )
        assertEquals(
            SubtitleFontCatalog.MONOSPACE_FONT_ID,
            SubtitleFontCatalog.normalizePersistedId("Monospace"),
        )
    }

    @Test
    fun `missing persisted font falls back to system sans`() {
        val options = listOf(
            SubtitleFontOption(
                id = SubtitleFontCatalog.DEFAULT_FONT_ID,
                displayName = "System Sans",
            ),
            SubtitleFontOption(
                id = "file:/system/fonts/Example.ttf#0",
                displayName = "Example",
            ),
        )

        val resolved = SubtitleFontCatalog.resolveOption(
            fontId = "file:/system/fonts/Removed.ttf#0",
            availableFonts = options,
        )

        assertEquals(SubtitleFontCatalog.DEFAULT_FONT_ID, resolved.id)
    }

    @Test
    fun `only Korean language metadata is accepted for system fonts`() {
        assertTrue(
            SubtitleFontCatalog.supportsKoreanLanguageCodes(
                listOf("en", "ko", "ja"),
            ),
        )
        assertFalse(
            SubtitleFontCatalog.supportsKoreanLanguageCodes(
                listOf("en", "ja", "zh"),
            ),
        )
    }
}
