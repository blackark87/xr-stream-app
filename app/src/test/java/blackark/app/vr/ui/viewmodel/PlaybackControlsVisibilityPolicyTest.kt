package blackark.app.vr.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackControlsVisibilityPolicyTest {

    @Test
    fun `VR seek thumbnail preserves the full projection frame`() {
        assertFalse(shouldPreserveFullSeekPreviewFrame(VideoFormat.Format2D))
        assertTrue(shouldPreserveFullSeekPreviewFrame(VideoFormat.Format180))
        assertTrue(shouldPreserveFullSeekPreviewFrame(VideoFormat.Format360))
    }

    @Test
    fun `hide request is rejected during input lock or seek preview`() {
        assertEquals(
            ControlsVisibilityBlockReason.InputLock,
            resolveControlsVisibilityBlockReason(
                targetVisible = false,
                controlsInputLocked = true,
                seekPreviewActive = false,
                recentInputSuppressed = false,
            ),
        )
        assertEquals(
            ControlsVisibilityBlockReason.SeekPreview,
            resolveControlsVisibilityBlockReason(
                targetVisible = false,
                controlsInputLocked = false,
                seekPreviewActive = true,
                recentInputSuppressed = false,
            ),
        )
    }

    @Test
    fun `show request remains allowed while input is locked`() {
        assertNull(
            resolveControlsVisibilityBlockReason(
                targetVisible = true,
                controlsInputLocked = true,
                seekPreviewActive = true,
                recentInputSuppressed = true,
            )
        )
    }

    @Test
    fun `navigation exit rejects both show and hide requests`() {
        listOf(false, true).forEach { targetVisible ->
            assertEquals(
                ControlsVisibilityBlockReason.NavigationExit,
                resolveControlsVisibilityBlockReason(
                    targetVisible = targetVisible,
                    controlsInputLocked = false,
                    seekPreviewActive = false,
                    recentInputSuppressed = false,
                    navigationExitPending = true,
                ),
            )
        }
    }

    @Test
    fun `seek preview only applies the latest active request`() {
        assertTrue(
            shouldApplySeekPreviewFrame(
                requestGeneration = 4L,
                currentGeneration = 4L,
                seekPreviewActive = true,
                requestedTargetPositionMs = 20_000L,
                latestTargetPositionMs = 20_000L,
                frameAvailable = true,
            )
        )
        assertFalse(
            shouldApplySeekPreviewFrame(
                requestGeneration = 3L,
                currentGeneration = 4L,
                seekPreviewActive = true,
                requestedTargetPositionMs = 20_000L,
                latestTargetPositionMs = 20_000L,
                frameAvailable = true,
            )
        )
        assertFalse(
            shouldApplySeekPreviewFrame(
                requestGeneration = 4L,
                currentGeneration = 4L,
                seekPreviewActive = false,
                requestedTargetPositionMs = 20_000L,
                latestTargetPositionMs = 22_000L,
                frameAvailable = true,
            )
        )
    }

    @Test
    fun `first engaged thumbstick input reveals controls and is handled`() {
        assertEquals(
            ControllerAxisInputGate.RevealAndHandle,
            resolveControllerAxisInputGate(
                showControls = false,
                x = 0.8f,
                y = 0f,
                engageThreshold = 0.45f,
            ),
        )
        assertEquals(
            ControllerAxisInputGate.Ignore,
            resolveControllerAxisInputGate(
                showControls = false,
                x = 0.1f,
                y = 0.1f,
                engageThreshold = 0.45f,
            ),
        )
    }

    @Test
    fun `vertical controller axis elevates immersive view anchor but keeps 2D volume`() {
        assertEquals(
            ControllerVerticalAxisTarget.Volume,
            resolveControllerVerticalAxisTarget(VideoFormat.Format2D),
        )
        assertEquals(
            ControllerVerticalAxisTarget.ImmersiveViewAnchorElevation,
            resolveControllerVerticalAxisTarget(VideoFormat.Format180),
        )
        assertEquals(
            ControllerVerticalAxisTarget.ImmersiveViewAnchorElevation,
            resolveControllerVerticalAxisTarget(VideoFormat.Format360),
        )
    }

    @Test
    fun `immersive view anchor elevation steps in both directions and remains clamped`() {
        assertEquals(2.5f, stepImmersiveViewAnchorElevationDegrees(0f, direction = 1), 0f)
        assertEquals(-2.5f, stepImmersiveViewAnchorElevationDegrees(0f, direction = -1), 0f)
        assertEquals(
            MAX_IMMERSIVE_VIEW_ANCHOR_ELEVATION_DEGREES,
            stepImmersiveViewAnchorElevationDegrees(
                MAX_IMMERSIVE_VIEW_ANCHOR_ELEVATION_DEGREES,
                direction = 1,
            ),
            0f,
        )
        assertEquals(
            MIN_IMMERSIVE_VIEW_ANCHOR_ELEVATION_DEGREES,
            stepImmersiveViewAnchorElevationDegrees(
                MIN_IMMERSIVE_VIEW_ANCHOR_ELEVATION_DEGREES,
                direction = -1,
            ),
            0f,
        )
    }

    @Test
    fun `menus input locks and navigation block controller playback axes`() {
        assertTrue(
            shouldBlockControllerAxisPlaybackInput(
                controlsInputLocked = false,
                navigationExitPending = false,
                activePlaybackMenu = PlaybackMenu.Display,
            )
        )
        assertTrue(
            shouldBlockControllerAxisPlaybackInput(
                controlsInputLocked = true,
                navigationExitPending = false,
                activePlaybackMenu = PlaybackMenu.None,
            )
        )
        assertTrue(
            shouldBlockControllerAxisPlaybackInput(
                controlsInputLocked = false,
                navigationExitPending = true,
                activePlaybackMenu = PlaybackMenu.None,
            )
        )
        assertFalse(
            shouldBlockControllerAxisPlaybackInput(
                controlsInputLocked = false,
                navigationExitPending = false,
                activePlaybackMenu = PlaybackMenu.None,
            )
        )
    }
}
