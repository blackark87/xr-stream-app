package blackark.app.vr.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ImmersiveSubtitleSettingsTest {

    @Test
    fun `invalid and out of range distances are normalized`() {
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
        val placement = resolveImmersiveSubtitlePlacement(4.0f)

        assertEquals(4.0f, placement.distanceMeters, 0.0001f)
        assertEquals(2.25f, placement.forwardOffsetMeters, 0.0001f)
        assertEquals(4.0f / 1.75f, placement.scale, 0.0001f)
    }
}
