package blackark.app.vr.utils

import android.graphics.Typeface
import android.graphics.fonts.FontStyle
import android.graphics.fonts.SystemFonts
import java.io.File

data class SubtitleFontOption(
    val id: String,
    val displayName: String,
    val filePath: String? = null,
    val ttcIndex: Int = 0,
    val weight: Int = 400,
    val italic: Boolean = false,
)

object SubtitleFontCatalog {
    const val DEFAULT_FONT_ID = "family:sans-serif"
    const val SERIF_FONT_ID = "family:serif"
    const val MONOSPACE_FONT_ID = "family:monospace"

    private val familyOptions = listOf(
        SubtitleFontOption(
            id = DEFAULT_FONT_ID,
            displayName = "System Sans",
        ),
        SubtitleFontOption(
            id = SERIF_FONT_ID,
            displayName = "System Serif",
        ),
        SubtitleFontOption(
            id = MONOSPACE_FONT_ID,
            displayName = "System Monospace",
        ),
    )

    fun availableFonts(): List<SubtitleFontOption> {
        val systemOptions = runCatching { SystemFonts.getAvailableFonts() }.getOrDefault(emptySet())
            .mapNotNull { font ->
                val file = font.file ?: return@mapNotNull null
                val locales = font.localeList
                if (!supportsKoreanLanguageCodes(List(locales.size()) { locales[it].language })) {
                    return@mapNotNull null
                }
                val style = font.style
                val italic = style.slant == FontStyle.FONT_SLANT_ITALIC
                SubtitleFontOption(
                    id = buildFileFontId(file, font.ttcIndex),
                    displayName = buildDisplayName(
                        file = file,
                        weight = style.weight,
                        italic = italic,
                        ttcIndex = font.ttcIndex,
                    ),
                    filePath = file.absolutePath,
                    ttcIndex = font.ttcIndex,
                    weight = style.weight,
                    italic = italic,
                )
            }
            .distinctBy { it.id }
            .sortedWith(
                compareBy<SubtitleFontOption> { it.displayName.lowercase() }
                    .thenBy { it.weight }
                    .thenBy { it.italic }
                    .thenBy { it.ttcIndex }
            )

        return familyOptions + systemOptions
    }

    internal fun supportsKoreanLanguageCodes(
        languageCodes: Iterable<String>,
    ): Boolean = languageCodes.any { it.equals("ko", ignoreCase = true) }

    fun normalizePersistedId(value: String?): String = when (value) {
        null,
        "",
        "SansSerif" -> DEFAULT_FONT_ID

        "Serif" -> SERIF_FONT_ID
        "Monospace" -> MONOSPACE_FONT_ID
        else -> value
    }

    fun resolveOption(
        fontId: String?,
        availableFonts: List<SubtitleFontOption> = availableFonts(),
    ): SubtitleFontOption {
        val normalizedId = normalizePersistedId(fontId)
        return availableFonts.firstOrNull { it.id == normalizedId }
            ?: availableFonts.firstOrNull { it.id == DEFAULT_FONT_ID }
            ?: familyOptions.first()
    }

    fun resolveTypeface(option: SubtitleFontOption): Typeface {
        val familyName = option.id.removePrefix("family:")
        if (option.id.startsWith("family:")) {
            return Typeface.create(familyName, Typeface.NORMAL)
        }

        val filePath = option.filePath ?: return Typeface.SANS_SERIF
        return runCatching {
            Typeface.Builder(File(filePath))
                .setTtcIndex(option.ttcIndex)
                .setWeight(option.weight)
                .setItalic(option.italic)
                .setFallback("sans-serif")
                .build()
        }.getOrNull() ?: Typeface.SANS_SERIF
    }

    private fun buildFileFontId(file: File, ttcIndex: Int): String =
        "file:${file.absolutePath}#$ttcIndex"

    private fun buildDisplayName(
        file: File,
        weight: Int,
        italic: Boolean,
        ttcIndex: Int,
    ): String {
        val faceName = file.name.substringBeforeLast('.', file.name)
        val styleName = buildList {
            add(weightLabel(weight))
            if (italic) add("Italic")
            if (ttcIndex > 0) add("Face $ttcIndex")
        }.joinToString(" · ")
        return "$faceName · $styleName"
    }

    private fun weightLabel(weight: Int): String = when {
        weight <= 200 -> "Thin"
        weight <= 300 -> "Light"
        weight <= 500 -> "Regular"
        weight <= 600 -> "Medium"
        weight <= 700 -> "Bold"
        else -> "Black"
    }
}
