package blackark.app.vr.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssColorMetadataParserTest {

    @Test
    fun `declared style primary color is restored without inventing a speaker color`() {
        val document = AssColorMetadataParser.parse(
            ass(
                styles = listOf(
                    "Style: SpeakerA,Arial,48,&H0000FF00,&H000000FF,&H00000000,&H64000000"
                ),
                dialogue = "Dialogue: 0,0:00:01.00,0:00:03.00,SpeakerA,,0,0,0,,원본 스타일",
            )
        )

        assertEquals(0, document.directiveCount)
        assertEquals("원본 스타일", document.dialogues.single().text)
        assertEquals(
            listOf(AssColorRun(start = 0, end = 6, colorArgb = 0xFF00FF00.toInt())),
            document.dialogues.single().colorRuns,
        )
    }

    @Test
    fun `inline primary color is restored only for its original text range`() {
        val document = AssColorMetadataParser.parse(
            ass(
                dialogue =
                    "Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,," +
                            "기본 {\\1c&H0000FF&}빨강{\\1c} 기본",
            )
        )

        val dialogue = document.dialogues.single()
        assertEquals("기본 빨강 기본", dialogue.text)
        assertEquals(
            listOf(
                AssColorRun(start = 0, end = 3, colorArgb = 0xFFFFFFFF.toInt()),
                AssColorRun(start = 3, end = 5, colorArgb = 0xFFFF0000.toInt()),
                AssColorRun(start = 5, end = 8, colorArgb = 0xFFFFFFFF.toInt()),
            ),
            dialogue.colorRuns,
        )
    }

    @Test
    fun `inline alpha preserves ASS inverse alpha semantics`() {
        val document = AssColorMetadataParser.parse(
            ass(
                dialogue =
                    "Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,," +
                            "{\\1c&HFF0000&\\1a&H80&}파랑",
            )
        )

        assertEquals(
            listOf(AssColorRun(start = 0, end = 2, colorArgb = 0x7F0000FF)),
            document.dialogues.single().colorRuns,
        )
    }

    @Test
    fun `named style reset uses only the color declared by that style`() {
        val document = AssColorMetadataParser.parse(
            ass(
                styles = listOf(
                    "Style: SpeakerB,Arial,48,&H0000FF00,&H000000FF,&H00000000,&H64000000"
                ),
                dialogue =
                    "Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,," +
                            "A{\\rSpeakerB}B{\\r}C",
            )
        )

        val dialogue = document.dialogues.single()
        assertEquals("ABC", dialogue.text)
        assertEquals(
            listOf(
                AssColorRun(start = 0, end = 1, colorArgb = 0xFFFFFFFF.toInt()),
                AssColorRun(start = 1, end = 2, colorArgb = 0xFF00FF00.toInt()),
                AssColorRun(start = 2, end = 3, colorArgb = 0xFFFFFFFF.toInt()),
            ),
            dialogue.colorRuns,
        )
    }

    @Test
    fun `dialogue without declared colors never receives generated speaker colors`() {
        val document = AssColorMetadataParser.parse(
            ass(
                dialogue = "Dialogue: 0,0:00:01.00,0:00:03.00,UnknownSpeaker,,0,0,0,,색상 없음",
            )
        )

        assertEquals(0, document.directiveCount)
        assertTrue(document.dialogues.single().colorRuns.isEmpty())
    }

    @Test
    fun `unclosed override block remains visible instead of crashing the parser`() {
        val document = AssColorMetadataParser.parse(
            ass(
                dialogue =
                    "Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,," +
                            "표시 {\\1c&H0000FF& 그대로",
            )
        )

        assertEquals(
            "표시 {\\1c&H0000FF& 그대로",
            document.dialogues.single().text,
        )
    }

    private fun ass(
        styles: List<String> = emptyList(),
        dialogue: String,
    ): String = buildString {
        appendLine("[V4+ Styles]")
        appendLine(
            "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, " +
                    "OutlineColour, BackColour"
        )
        appendLine(
            "Style: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H64000000"
        )
        styles.forEach(::appendLine)
        appendLine("[Events]")
        appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")
        appendLine(dialogue)
    }
}
