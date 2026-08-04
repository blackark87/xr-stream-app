package blackark.app.vr.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import blackark.app.vr.ui.viewmodel.StereoMode
import blackark.app.vr.ui.viewmodel.VideoFormat
import kotlin.math.PI
import kotlin.math.roundToInt

/** Produces a mono 16:9 view from a VR frame without touching the paused player surface. */
object VrSeekPreviewRenderer {
    private const val OUTPUT_EYE_WIDTH = 320
    private const val OUTPUT_HEIGHT = 180
    private const val MAX_CACHE_ENTRIES = 8
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val cache = object : LinkedHashMap<String, Bitmap>(MAX_CACHE_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>): Boolean {
            val remove = size > MAX_CACHE_ENTRIES
            if (remove && !eldest.value.isRecycled) eldest.value.recycle()
            return remove
        }
    }

    @Synchronized
    fun render(
        sourcePath: String,
        videoFormat: VideoFormat,
        stereoMode: StereoMode,
        relativeYawRadians: Float,
        relativePitchRadians: Float,
    ): Bitmap? {
        val yawBucket = (relativeYawRadians * 40f).roundToInt()
        val pitchBucket = (relativePitchRadians * 40f).roundToInt()
        val cacheKey = "$sourcePath:${videoFormat.name}:${stereoMode.name}:$yawBucket:$pitchBucket"
        cache[cacheKey]?.takeUnless(Bitmap::isRecycled)?.let { return it }

        val source = BitmapFactory.decodeFile(sourcePath) ?: return null
        return try {
            val output = Bitmap.createBitmap(
                OUTPUT_EYE_WIDTH,
                OUTPUT_HEIGHT,
                Bitmap.Config.ARGB_8888,
            )
            val canvas = Canvas(output)
            drawEye(
                canvas = canvas,
                source = source,
                sourceEye = resolveSourceEye(source, stereoMode, leftEye = true),
                destination = Rect(0, 0, OUTPUT_EYE_WIDTH, OUTPUT_HEIGHT),
                videoFormat = videoFormat,
                yaw = relativeYawRadians,
                pitch = relativePitchRadians,
            )
            cache[cacheKey] = output
            output
        } finally {
            if (!source.isRecycled) source.recycle()
        }
    }

    @Synchronized
    fun clear() {
        cache.values.forEach { if (!it.isRecycled) it.recycle() }
        cache.clear()
    }

    private fun resolveSourceEye(source: Bitmap, stereoMode: StereoMode, leftEye: Boolean): Rect =
        when (stereoMode) {
            StereoMode.SideBySide -> {
                val half = source.width / 2
                if (leftEye) Rect(0, 0, half, source.height)
                else Rect(half, 0, source.width, source.height)
            }
            StereoMode.TopBottom -> {
                val half = source.height / 2
                if (leftEye) Rect(0, 0, source.width, half)
                else Rect(0, half, source.width, source.height)
            }
            StereoMode.Mono -> Rect(0, 0, source.width, source.height)
        }

    private fun drawEye(
        canvas: Canvas,
        source: Bitmap,
        sourceEye: Rect,
        destination: Rect,
        videoFormat: VideoFormat,
        yaw: Float,
        pitch: Float,
    ) {
        val projectionDegrees = if (videoFormat == VideoFormat.Format360) 360f else 180f
        val horizontalFov = if (videoFormat == VideoFormat.Format360) 82f else 72f
        val verticalFov = 48f
        val eyeWidth = sourceEye.width().coerceAtLeast(1)
        val eyeHeight = sourceEye.height().coerceAtLeast(1)
        val cropWidth = (eyeWidth * horizontalFov / projectionDegrees).roundToInt()
            .coerceIn(1, eyeWidth)
        val cropHeight = (eyeHeight * verticalFov / 180f).roundToInt()
            .coerceIn(1, eyeHeight)
        val normalizedYaw = yaw / (2f * PI.toFloat())
        val centerX = eyeWidth * (0.5f + normalizedYaw * (360f / projectionDegrees))
        val centerY = eyeHeight * (0.5f - pitch / PI.toFloat())
        val top = (centerY - cropHeight / 2f).roundToInt().coerceIn(0, eyeHeight - cropHeight)
        val requestedLeft = (centerX - cropWidth / 2f).roundToInt()

        if (videoFormat != VideoFormat.Format360) {
            val left = requestedLeft.coerceIn(0, eyeWidth - cropWidth)
            canvas.drawBitmap(
                source,
                Rect(
                    sourceEye.left + left,
                    sourceEye.top + top,
                    sourceEye.left + left + cropWidth,
                    sourceEye.top + top + cropHeight,
                ),
                destination,
                paint,
            )
            return
        }

        val wrappedLeft = ((requestedLeft % eyeWidth) + eyeWidth) % eyeWidth
        val firstWidth = minOf(cropWidth, eyeWidth - wrappedLeft)
        val firstDestinationWidth = destination.width() * firstWidth / cropWidth
        canvas.drawBitmap(
            source,
            Rect(
                sourceEye.left + wrappedLeft,
                sourceEye.top + top,
                sourceEye.left + wrappedLeft + firstWidth,
                sourceEye.top + top + cropHeight,
            ),
            Rect(
                destination.left,
                destination.top,
                destination.left + firstDestinationWidth,
                destination.bottom,
            ),
            paint,
        )
        val remaining = cropWidth - firstWidth
        if (remaining > 0) {
            canvas.drawBitmap(
                source,
                Rect(
                    sourceEye.left,
                    sourceEye.top + top,
                    sourceEye.left + remaining,
                    sourceEye.top + top + cropHeight,
                ),
                Rect(
                    destination.left + firstDestinationWidth,
                    destination.top,
                    destination.right,
                    destination.bottom,
                ),
                paint,
            )
        }
    }
}
