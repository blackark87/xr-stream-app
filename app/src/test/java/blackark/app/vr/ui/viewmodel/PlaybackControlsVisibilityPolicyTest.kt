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
}
