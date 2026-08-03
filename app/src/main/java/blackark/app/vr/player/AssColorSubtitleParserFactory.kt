@file:androidx.annotation.OptIn(
    markerClass = [androidx.media3.common.util.UnstableApi::class],
)

package blackark.app.vr.player

import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.text.Cue
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import kotlin.math.roundToLong

private const val ASS_COLOR_LOG_TAG = "AssColorSubtitleParser"
private const val SUBTITLE_LOG_PREFIX = "[SubtitleDebug]"

@UnstableApi
internal class AssColorSubtitleParserFactory(
    private val delegateFactory: SubtitleParser.Factory = DefaultSubtitleParserFactory(),
) : SubtitleParser.Factory {

    override fun supportsFormat(format: Format): Boolean =
        delegateFactory.supportsFormat(format)

    override fun getCueReplacementBehavior(format: Format): Int =
        delegateFactory.getCueReplacementBehavior(format)

    override fun create(format: Format): SubtitleParser {
        val delegate = delegateFactory.create(format)
        return if (format.sampleMimeType == MimeTypes.TEXT_SSA) {
            AssColorSubtitleParser(delegate)
        } else {
            delegate
        }
    }
}

@UnstableApi
private class AssColorSubtitleParser(
    private val delegate: SubtitleParser,
) : SubtitleParser {

    override fun parse(
        data: ByteArray,
        offset: Int,
        length: Int,
        outputOptions: SubtitleParser.OutputOptions,
        output: Consumer<CuesWithTiming>,
    ) {
        val colorDocument = AssColorMetadataParser.parse(data, offset, length)
        var restoredSpanCount = 0

        delegate.parse(
            data,
            offset,
            length,
            outputOptions,
            Consumer { cuesWithTiming ->
                val restoredCues = cuesWithTiming.cues.map { cue ->
                    val restored = colorDocument.restoreCue(
                        cue = cue,
                        presentationTimeUs = cuesWithTiming.startTimeUs,
                    )
                    restoredSpanCount += restored.second
                    restored.first
                }
                output.accept(
                    CuesWithTiming(
                        restoredCues,
                        cuesWithTiming.startTimeUs,
                        cuesWithTiming.durationUs,
                    )
                )
            },
        )

        if (colorDocument.directiveCount > 0 || restoredSpanCount > 0) {
            Log.i(
                ASS_COLOR_LOG_TAG,
                "$SUBTITLE_LOG_PREFIX ass originalColorDirectives=" +
                        "${colorDocument.directiveCount} restoredColorSpans=$restoredSpanCount",
            )
        }
    }

    override fun getCueReplacementBehavior(): Int = delegate.cueReplacementBehavior

    override fun reset() {
        delegate.reset()
    }
}

internal data class AssColorRun(
    val start: Int,
    val end: Int,
    val colorArgb: Int,
)

internal data class AssDialogueColorMetadata(
    val startTimeUs: Long,
    val endTimeUs: Long,
    val text: String,
    val colorRuns: List<AssColorRun>,
)

internal data class AssColorDocument(
    val dialogues: List<AssDialogueColorMetadata>,
    val directiveCount: Int,
) {
    fun restoreCue(
        cue: Cue,
        presentationTimeUs: Long,
    ): Pair<Cue, Int> {
        val cueText = cue.text ?: return cue to 0
        val dialogue = dialogues.firstOrNull { candidate ->
            presentationTimeUs >= candidate.startTimeUs &&
                    presentationTimeUs < candidate.endTimeUs &&
                    candidate.text == cueText.toString() &&
                    candidate.colorRuns.isNotEmpty()
        } ?: return cue to 0

        val styledText = SpannableString(cueText)
        var appliedCount = 0
        dialogue.colorRuns.forEach { run ->
            val start = run.start.coerceIn(0, styledText.length)
            val end = run.end.coerceIn(start, styledText.length)
            if (start == end) return@forEach

            styledText.setSpan(
                ForegroundColorSpan(run.colorArgb),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            appliedCount += 1
        }
        if (appliedCount == 0) return cue to 0

        return cue.buildUpon()
            .setText(styledText)
            .build() to appliedCount
    }
}

internal object AssColorMetadataParser {
    private val colorDirectivePattern =
        Regex("""\\(1c|c|1a|alpha|r)([^\\]*)""", RegexOption.IGNORE_CASE)

    fun parse(
        data: ByteArray,
        offset: Int = 0,
        length: Int = data.size - offset,
    ): AssColorDocument {
        val text = decodeText(data, offset, length)
        return parse(text)
    }

    fun parse(text: String): AssColorDocument {
        var section = ""
        var styleFormat: List<String> = emptyList()
        var eventFormat: List<String> = emptyList()
        val styleColors = linkedMapOf<String, Int>()
        val rawDialogues = mutableListOf<RawAssDialogue>()

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd('\r')
            when {
                line.startsWith("[") && line.endsWith("]") -> {
                    section = line.lowercase()
                }

                section in setOf("[v4+ styles]", "[v4 styles]") &&
                        line.startsWith("Format:", ignoreCase = true) -> {
                    styleFormat = parseFormat(line.substringAfter(':'))
                }

                section in setOf("[v4+ styles]", "[v4 styles]") &&
                        line.startsWith("Style:", ignoreCase = true) -> {
                    parseStyleColor(
                        payload = line.substringAfter(':'),
                        format = styleFormat,
                    )?.let { (name, color) ->
                        styleColors[name.lowercase()] = color
                    }
                }

                section == "[events]" &&
                        line.startsWith("Format:", ignoreCase = true) -> {
                    eventFormat = parseFormat(line.substringAfter(':'))
                }

                section == "[events]" &&
                        line.startsWith("Dialogue:", ignoreCase = true) -> {
                    parseDialogue(
                        payload = line.substringAfter(':'),
                        format = eventFormat,
                    )?.let(rawDialogues::add)
                }
            }
        }

        var directiveCount = 0
        val dialogues = rawDialogues.map { dialogue ->
            val parsed = parseDialogueText(dialogue, styleColors)
            directiveCount += parsed.second
            AssDialogueColorMetadata(
                startTimeUs = dialogue.startTimeUs,
                endTimeUs = dialogue.endTimeUs,
                text = parsed.first.first,
                colorRuns = parsed.first.second,
            )
        }
        return AssColorDocument(
            dialogues = dialogues,
            directiveCount = directiveCount,
        )
    }

    internal fun parseAssColor(value: String): Int? {
        val cleaned = value.trim()
            .removePrefix("&H")
            .removePrefix("&h")
            .removeSuffix("&")
            .trim()
        if (cleaned.isEmpty()) return null

        val encoded = runCatching {
            if (value.contains("&H", ignoreCase = true)) {
                cleaned.toLong(16)
            } else {
                cleaned.toLong()
            }
        }.getOrNull() ?: return null

        val normalized = encoded and 0xFFFFFFFFL
        val red = (normalized and 0xFF).toInt()
        val green = ((normalized shr 8) and 0xFF).toInt()
        val blue = ((normalized shr 16) and 0xFF).toInt()
        val assAlpha = if (normalized > 0xFFFFFF) {
            ((normalized shr 24) and 0xFF).toInt()
        } else {
            0
        }
        val androidAlpha = 255 - assAlpha
        return argb(androidAlpha, red, green, blue)
    }

    private fun parseDialogueText(
        dialogue: RawAssDialogue,
        styleColors: Map<String, Int>,
    ): Pair<Pair<String, List<AssColorRun>>, Int> {
        val originalStyleName = dialogue.styleName.lowercase()
        var activeStyleName = originalStyleName
        var colorOverride: Int? = null
        var alphaOverride: Int? = null
        var directiveCount = 0
        var cursor = 0
        val visibleText = StringBuilder()
        val colorRuns = mutableListOf<AssColorRun>()

        fun activeBaseColor(): Int? = styleColors[activeStyleName]

        fun effectiveColor(): Int? {
            val baseColor = colorOverride ?: activeBaseColor() ?: return null
            val alpha = alphaOverride ?: alphaOf(baseColor)
            return withAlpha(baseColor, alpha)
        }

        fun hasDeclaredColor(): Boolean =
            activeBaseColor() != null ||
                    colorOverride != null ||
                    alphaOverride != null ||
                    activeStyleName != originalStyleName

        fun appendVisibleSegment(rawSegment: String) {
            if (rawSegment.isEmpty()) return
            val normalized = normalizeDialogueText(rawSegment)
            if (normalized.isEmpty()) return

            val start = visibleText.length
            visibleText.append(normalized)
            val end = visibleText.length
            if (hasDeclaredColor()) {
                effectiveColor()?.let { color ->
                    addOrMergeColorRun(colorRuns, AssColorRun(start, end, color))
                }
            }
        }

        findOverrideBlocks(dialogue.rawText).forEach { block ->
            appendVisibleSegment(dialogue.rawText.substring(cursor, block.start))
            colorDirectivePattern.findAll(block.contents).forEach { directive ->
                val name = directive.groupValues[1].lowercase()
                val argument = directive.groupValues[2].trim()
                when (name) {
                    "c", "1c" -> {
                        if (argument.isEmpty()) {
                            colorOverride = null
                        } else {
                            parseAssColor(argument)?.let { parsedColor ->
                                colorOverride = withAlpha(
                                    parsedColor,
                                    alphaOverride
                                        ?: activeBaseColor()?.let(::alphaOf)
                                        ?: 255,
                                )
                            }
                        }
                        directiveCount += 1
                    }

                    "alpha", "1a" -> {
                        alphaOverride =
                            if (argument.isEmpty()) null else parseAssAlpha(argument)
                        directiveCount += 1
                    }

                    "r" -> {
                        activeStyleName =
                            argument.takeIf { it.isNotBlank() }?.lowercase()
                                ?: originalStyleName
                        colorOverride = null
                        alphaOverride = null
                        directiveCount += 1
                    }
                }
            }
            cursor = block.endExclusive
        }
        appendVisibleSegment(dialogue.rawText.substring(cursor))

        return (visibleText.toString() to colorRuns) to directiveCount
    }

    /**
     * Avoids Android ICU regex differences for literal closing braces in ASS override blocks.
     */
    private fun findOverrideBlocks(value: String): List<AssOverrideBlock> {
        val blocks = mutableListOf<AssOverrideBlock>()
        var searchFrom = 0
        while (searchFrom < value.length) {
            val start = value.indexOf('{', startIndex = searchFrom)
            if (start < 0) break
            val end = value.indexOf('}', startIndex = start + 1)
            if (end < 0) break

            blocks += AssOverrideBlock(
                start = start,
                endExclusive = end + 1,
                contents = value.substring(start + 1, end),
            )
            searchFrom = end + 1
        }
        return blocks
    }

    private fun parseFormat(payload: String): List<String> =
        payload.split(',').map { it.trim().lowercase() }

    private fun parseStyleColor(
        payload: String,
        format: List<String>,
    ): Pair<String, Int>? {
        if (format.isEmpty()) return null
        val fields = payload.split(',', limit = format.size)
        if (fields.size < format.size) return null
        val nameIndex = format.indexOf("name")
        val colorIndex = format.indexOf("primarycolour")
        if (nameIndex < 0 || colorIndex < 0) return null

        val name = fields[nameIndex].trim()
        val color = parseAssColor(fields[colorIndex]) ?: return null
        return name to color
    }

    private fun parseDialogue(
        payload: String,
        format: List<String>,
    ): RawAssDialogue? {
        if (format.isEmpty()) return null
        val fields = payload.split(',', limit = format.size)
        if (fields.size < format.size) return null

        val startIndex = format.indexOf("start")
        val endIndex = format.indexOf("end")
        val styleIndex = format.indexOf("style")
        val textIndex = format.indexOf("text")
        if (startIndex < 0 || endIndex < 0 || styleIndex < 0 || textIndex < 0) return null

        val startTimeUs = parseTimecodeUs(fields[startIndex]) ?: return null
        val endTimeUs = parseTimecodeUs(fields[endIndex]) ?: return null
        if (endTimeUs <= startTimeUs) return null

        return RawAssDialogue(
            startTimeUs = startTimeUs,
            endTimeUs = endTimeUs,
            styleName = fields[styleIndex].trim(),
            rawText = fields[textIndex],
        )
    }

    private fun parseTimecodeUs(value: String): Long? {
        val parts = value.trim().split(':')
        if (parts.size != 3) return null
        val hours = parts[0].toLongOrNull() ?: return null
        val minutes = parts[1].toLongOrNull() ?: return null
        val seconds = parts[2].toDoubleOrNull() ?: return null
        return (
                hours * 3_600_000_000L +
                        minutes * 60_000_000L +
                        seconds * 1_000_000.0
                ).roundToLong()
    }

    private fun parseAssAlpha(value: String): Int? {
        val cleaned = value.trim()
            .removePrefix("&H")
            .removePrefix("&h")
            .removeSuffix("&")
            .trim()
        if (cleaned.isEmpty()) return null
        val assAlpha = runCatching { cleaned.toInt(16) }.getOrNull() ?: return null
        return 255 - assAlpha.coerceIn(0, 255)
    }

    private fun normalizeDialogueText(value: String): String =
        value
            .replace("\\N", "\n")
            .replace("\\n", "\n")
            .replace("\\h", "\u00A0")

    private fun decodeText(
        data: ByteArray,
        offset: Int,
        length: Int,
    ): String {
        val safeOffset = offset.coerceIn(0, data.size)
        val safeLength = length.coerceIn(0, data.size - safeOffset)
        val charsetAndBom = detectCharset(data, safeOffset, safeLength)
        val contentOffset = safeOffset + charsetAndBom.second
        val contentLength = (safeLength - charsetAndBom.second).coerceAtLeast(0)
        return String(data, contentOffset, contentLength, charsetAndBom.first)
    }

    private fun detectCharset(
        data: ByteArray,
        offset: Int,
        length: Int,
    ): Pair<Charset, Int> {
        fun byte(index: Int): Int = data[offset + index].toInt() and 0xFF

        return when {
            length >= 3 && byte(0) == 0xEF && byte(1) == 0xBB && byte(2) == 0xBF ->
                StandardCharsets.UTF_8 to 3

            length >= 2 && byte(0) == 0xFF && byte(1) == 0xFE ->
                StandardCharsets.UTF_16LE to 2

            length >= 2 && byte(0) == 0xFE && byte(1) == 0xFF ->
                StandardCharsets.UTF_16BE to 2

            else -> StandardCharsets.UTF_8 to 0
        }
    }

    private fun addOrMergeColorRun(
        runs: MutableList<AssColorRun>,
        next: AssColorRun,
    ) {
        val previous = runs.lastOrNull()
        if (previous != null &&
            previous.end == next.start &&
            previous.colorArgb == next.colorArgb
        ) {
            runs[runs.lastIndex] = previous.copy(end = next.end)
        } else {
            runs += next
        }
    }

    private fun alphaOf(color: Int): Int = (color ushr 24) and 0xFF

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or ((alpha.coerceIn(0, 255) and 0xFF) shl 24)

    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        ((alpha and 0xFF) shl 24) or
                ((red and 0xFF) shl 16) or
                ((green and 0xFF) shl 8) or
                (blue and 0xFF)

    private data class RawAssDialogue(
        val startTimeUs: Long,
        val endTimeUs: Long,
        val styleName: String,
        val rawText: String,
    )

    private data class AssOverrideBlock(
        val start: Int,
        val endExclusive: Int,
        val contents: String,
    )
}
