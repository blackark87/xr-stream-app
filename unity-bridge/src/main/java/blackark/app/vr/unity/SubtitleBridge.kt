@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package blackark.app.vr.unity

import android.content.Context
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.extractor.text.SubtitleParser
import blackark.app.vr.network.SMBConfig
import blackark.app.vr.player.AssColorSubtitleParserFactory
import org.json.JSONArray
import org.json.JSONObject

internal fun readSubtitle(context: Context, path: String, config: SMBConfig?): JSONObject {
    val data = (if (config == null) localMediaReader(context, path) else smbMediaReader(config, path)).use { reader ->
        require(reader.size in 1..4L * 1024 * 1024) { "Subtitle must be between 1 byte and 4 MiB." }
        val bytes = ByteArray(reader.size.toInt())
        val buffer = ByteArray(64 * 1024)
        var offset = 0
        while (offset < bytes.size) {
            val count = reader.read(buffer, minOf(buffer.size, bytes.size - offset))
            check(count > 0) { "Subtitle stream ended unexpectedly." }
            buffer.copyInto(bytes, offset, 0, count)
            offset += count
        }
        bytes
    }
    val mime = when {
        path.endsWith(".ass", true) || path.endsWith(".ssa", true) -> MimeTypes.TEXT_SSA
        path.endsWith(".srt", true) -> MimeTypes.APPLICATION_SUBRIP
        else -> error("Unsupported external subtitle format.")
    }
    val parser = AssColorSubtitleParserFactory().create(Format.Builder().setSampleMimeType(mime).build())
    val frames = mutableListOf<JSONObject>()
    parser.parse(data, 0, data.size, SubtitleParser.OutputOptions.allCues()) { timing ->
        if (timing.cues.isEmpty() || timing.startTimeUs < 0 || timing.durationUs <= 0) return@parse
        check(frames.size < 20000) { "Subtitle has too many intervals." }
        val lines = JSONArray()
        for (cue in timing.cues) {
            val text = cue.text ?: continue
            check(text.length <= 8192) { "Subtitle line is too long." }
            val colors = JSONArray()
            if (text is Spanned) for (span in text.getSpans(0, text.length, ForegroundColorSpan::class.java)) {
                colors.put(JSONObject().put("start", text.getSpanStart(span)).put("end", text.getSpanEnd(span))
                    .put("argb", span.foregroundColor.toLong() and 0xffffffffL))
            }
            lines.put(JSONObject().put("text", text.toString()).put("colors", colors))
        }
        if (lines.length() != 0) frames += JSONObject().put("startMs", timing.startTimeUs / 1000)
            .put("endMs", timing.endTimeUs / 1000).put("lines", lines)
    }
    parser.reset()
    return JSONObject().put("subtitleFrames", JSONArray(frames.sortedBy { it.getLong("startMs") }))
}
