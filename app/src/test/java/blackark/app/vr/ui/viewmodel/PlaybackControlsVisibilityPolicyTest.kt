package blackark.app.vr.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackControlsVisibilityPolicyTest {

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
}
