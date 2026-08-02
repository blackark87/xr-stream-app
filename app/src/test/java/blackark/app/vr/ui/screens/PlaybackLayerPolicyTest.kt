package blackark.app.vr.ui.screens

import androidx.xr.scenecore.InputEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackLayerPolicyTest {

    @Test
    fun `spatial release reveals controls without requiring a hit position`() {
        assertTrue(isSpatialRevealClick(InputEvent.Action.UP))
        assertFalse(isSpatialRevealClick(InputEvent.Action.DOWN))
    }

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
    fun `visible controls expose only the controls layer`() {
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
        val lockedVisiblePolicy = resolvePlaybackLayerPolicy(
            isSurfaceReady = true,
            showControls = true,
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
        assertFalse(lockedPolicy.enableSurfaceRevealInput)
        assertFalse(lockedVisiblePolicy.enableSurfaceRevealInput)
        assertFalse(unreadyPolicy.showRevealInputLayer)
        assertFalse(unreadyPolicy.enableSurfaceRevealInput)
    }
}
