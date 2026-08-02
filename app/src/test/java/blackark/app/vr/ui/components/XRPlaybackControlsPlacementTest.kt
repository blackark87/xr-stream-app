package blackark.app.vr.ui.components

import androidx.compose.ui.unit.dp
import blackark.app.vr.ui.viewmodel.PlaybackMenu
import blackark.app.vr.ui.viewmodel.VideoFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class XRPlaybackControlsPlacementTest {

    @Test
    fun `2D display menu bottom stays directly above its button`() {
        val offset = calculatePlaybackMenuOffset(
            controlsWidthPx = 1080,
            anchorCenterXPx = 860,
            anchorTopYPx = 48,
            menuWidthPx = 360,
            menuHeightPx = 170,
            gapPx = 10,
        )

        assertEquals(680, offset.x)
        assertEquals(-132, offset.y)
        assertEquals(38, offset.y + 170)
    }

    @Test
    fun `immersive display menu keeps the full content height estimate`() {
        assertEquals(
            420.dp,
            PlaybackMenu.Display.playbackMenuWidth(VideoFormat.Format180),
        )
        assertEquals(
            640.dp,
            PlaybackMenu.Display.playbackMenuEstimatedHeight(VideoFormat.Format180),
        )
    }

    @Test
    fun `menu horizontal position remains inside controls width`() {
        val leftOffset = calculatePlaybackMenuOffset(
            controlsWidthPx = 800,
            anchorCenterXPx = 20,
            anchorTopYPx = 40,
            menuWidthPx = 360,
            menuHeightPx = 170,
            gapPx = 10,
        )
        val rightOffset = calculatePlaybackMenuOffset(
            controlsWidthPx = 800,
            anchorCenterXPx = 790,
            anchorTopYPx = 40,
            menuWidthPx = 360,
            menuHeightPx = 170,
            gapPx = 10,
        )

        assertEquals(0, leftOffset.x)
        assertEquals(440, rightOffset.x)
    }
}
