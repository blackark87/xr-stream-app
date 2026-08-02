package blackark.app.vr.utils

import androidx.xr.runtime.math.Vector3
import org.junit.Assert.assertEquals
import org.junit.Test

class ImmersiveSubtitleSettingsTest {

    @Test
    fun `invalid and out of range distances are normalized`() {
        assertEquals(124, IMMERSIVE_SUBTITLE_DISTANCE_SLIDER_STEPS)
        assertEquals(
            0.01f,
            (MAX_IMMERSIVE_SUBTITLE_DISTANCE_METERS -
                    MIN_IMMERSIVE_SUBTITLE_DISTANCE_METERS) /
                    (IMMERSIVE_SUBTITLE_DISTANCE_SLIDER_STEPS + 1),
            0.0001f,
        )
        assertEquals(
            DEFAULT_IMMERSIVE_SUBTITLE_DISTANCE_METERS,
            normalizeImmersiveSubtitleDistanceMeters(Float.NaN),
            0.0001f,
        )
        assertEquals(
            MIN_IMMERSIVE_SUBTITLE_DISTANCE_METERS,
            normalizeImmersiveSubtitleDistanceMeters(0.5f),
            0.0001f,
        )
        assertEquals(
            MAX_IMMERSIVE_SUBTITLE_DISTANCE_METERS,
            normalizeImmersiveSubtitleDistanceMeters(8.0f),
            0.0001f,
        )
    }

    @Test
    fun `reference distance keeps the original depth and scale`() {
        val placement =
            resolveImmersiveSubtitlePlacement(IMMERSIVE_SUBTITLE_REFERENCE_DISTANCE_METERS)

        assertEquals(IMMERSIVE_SUBTITLE_REFERENCE_DISTANCE_METERS, placement.distanceMeters, 0.0001f)
        assertEquals(0.0f, placement.forwardOffsetMeters, 0.0001f)
        assertEquals(1.0f, placement.scale, 0.0001f)
    }

    @Test
    fun `farther subtitles preserve angular size with proportional scaling`() {
        val placement = resolveImmersiveSubtitlePlacement(2.0f)

        assertEquals(2.0f, placement.distanceMeters, 0.0001f)
        assertEquals(0.25f, placement.forwardOffsetMeters, 0.0001f)
        assertEquals(2.0f / 1.75f, placement.scale, 0.0001f)
    }

    @Test
    fun `panel pose stays in pixels while metric depth and horizontal offsets are applied`() {
        val placement = resolveImmersiveSubtitlePlacement(2.0f)
        val offset =
            resolveImmersiveSubtitlePanelOffsetDp(
                baseTranslationPixels = Vector3(230.4f, -115.2f, -460.8f),
                worldForward = Vector3.Forward,
                worldRight = Vector3.Right,
                worldUp = Vector3.Up,
                placement = placement,
                horizontalOffsetMeters = -0.10f,
                verticalOffsetMeters = 0.15f,
                pixelsPerDp = 2.0f,
                dpPerMeter = 1152.0f,
            )

        assertEquals(0.0f, offset.x, 0.0001f)
        assertEquals(115.2f, offset.y, 0.0001f)
        assertEquals(-518.4f, offset.z, 0.0001f)
    }

    @Test
    fun `horizontal offset defaults left and is clamped`() {
        assertEquals(
            DEFAULT_IMMERSIVE_UI_HORIZONTAL_OFFSET_METERS,
            normalizeImmersiveUiHorizontalOffsetMeters(Float.NaN),
            0.0001f,
        )
        assertEquals(
            MIN_IMMERSIVE_UI_HORIZONTAL_OFFSET_METERS,
            normalizeImmersiveUiHorizontalOffsetMeters(-1.0f),
            0.0001f,
        )
        assertEquals(
            MAX_IMMERSIVE_UI_HORIZONTAL_OFFSET_METERS,
            normalizeImmersiveUiHorizontalOffsetMeters(1.0f),
            0.0001f,
        )
    }

    @Test
    fun `subtitle vertical offset defaults upward and is clamped`() {
        assertEquals(29, IMMERSIVE_SUBTITLE_VERTICAL_OFFSET_SLIDER_STEPS)
        assertEquals(
            0.01f,
            (MAX_IMMERSIVE_SUBTITLE_VERTICAL_OFFSET_METERS -
                    MIN_IMMERSIVE_SUBTITLE_VERTICAL_OFFSET_METERS) /
                    (IMMERSIVE_SUBTITLE_VERTICAL_OFFSET_SLIDER_STEPS + 1),
            0.0001f,
        )
        assertEquals(
            DEFAULT_IMMERSIVE_SUBTITLE_VERTICAL_OFFSET_METERS,
            normalizeImmersiveSubtitleVerticalOffsetMeters(Float.NaN),
            0.0001f,
        )
        assertEquals(
            MIN_IMMERSIVE_SUBTITLE_VERTICAL_OFFSET_METERS,
            normalizeImmersiveSubtitleVerticalOffsetMeters(-1.0f),
            0.0001f,
        )
        assertEquals(
            MAX_IMMERSIVE_SUBTITLE_VERTICAL_OFFSET_METERS,
            normalizeImmersiveSubtitleVerticalOffsetMeters(1.0f),
            0.0001f,
        )
    }

}
