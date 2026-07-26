package blackark.app.vr.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackLayerPolicyTest {

    @Test
    fun `hidden controls expose only reveal input layers`() {
        val policy = resolvePlaybackLayerPolicy(
            isSurfaceReady = true,
            showControls = false,
            controlsInputLocked = false,
            seekPreviewActive = false,
        )

        assertTrue(policy.showRevealInputLayer)
        assertTrue(policy.enableSurfaceRevealInput)
        assertFalse(policy.showControlsLayer)
        assertFalse(policy.subtitleAcceptsInput)
    }

    @Test
    fun `visible controls remove full screen reveal layers`() {
        val policy = resolvePlaybackLayerPolicy(
            isSurfaceReady = true,
            showControls = true,
            controlsInputLocked = false,
            seekPreviewActive = false,
        )

        assertFalse(policy.showRevealInputLayer)
        assertFalse(policy.enableSurfaceRevealInput)
        assertTrue(policy.showControlsLayer)
        assertFalse(policy.subtitleAcceptsInput)
    }

    @Test
    fun `locked or unready playback does not expose reveal input`() {
        val lockedPolicy = resolvePlaybackLayerPolicy(
            isSurfaceReady = true,
            showControls = false,
            controlsInputLocked = true,
            seekPreviewActive = false,
        )
        val unreadyPolicy = resolvePlaybackLayerPolicy(
            isSurfaceReady = false,
            showControls = false,
            controlsInputLocked = false,
            seekPreviewActive = false,
        )

        assertFalse(lockedPolicy.showRevealInputLayer)
        assertFalse(unreadyPolicy.showRevealInputLayer)
    }
}
