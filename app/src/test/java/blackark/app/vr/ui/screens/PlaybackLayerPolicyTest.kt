package blackark.app.vr.ui.screens

import androidx.compose.ui.unit.dp
import androidx.xr.runtime.math.Vector3
import androidx.xr.scenecore.InputEvent
import blackark.app.vr.ui.viewmodel.PlaybackMenu
import blackark.app.vr.ui.viewmodel.VideoFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackLayerPolicyTest {

    @Test
    fun `head-follow panel front faces back toward the viewer`() {
        val viewForward = Vector3(x = 0.6f, y = 0f, z = -0.8f)

        val rotation = resolveHeadFollowPanelRotation(viewForward)
        val panelFront = rotation * Vector3.Backward

        assertEquals(-viewForward.x, panelFront.x, 0.0001f)
        assertEquals(-viewForward.y, panelFront.y, 0.0001f)
        assertEquals(-viewForward.z, panelFront.z, 0.0001f)
    }

    @Test
    fun `spatial release reveals controls without requiring a hit position`() {
        assertTrue(isSpatialRevealClick(InputEvent.Action.UP))
        assertFalse(isSpatialRevealClick(InputEvent.Action.DOWN))
    }

    @Test
    fun `hidden controls enable surface toggle and fallback reveal`() {
        val policy = resolvePlaybackLayerPolicy(
            isSurfaceReady = true,
            isImmersive = true,
            showControls = false,
            controlsInputLocked = false,
            seekPreviewActive = false,
        )

        assertTrue(policy.retainFallbackRevealLayer)
        assertTrue(policy.showFallbackRevealLayer)
        assertTrue(policy.enableSurfaceToggleInput)
        assertFalse(policy.showControlsLayer)
        assertFalse(policy.subtitleAcceptsInput)
    }

    @Test
    fun `visible controls keep surface toggle behind controls`() {
        val policy = resolvePlaybackLayerPolicy(
            isSurfaceReady = true,
            isImmersive = true,
            showControls = true,
            controlsInputLocked = false,
            seekPreviewActive = false,
        )

        assertTrue(policy.retainFallbackRevealLayer)
        assertFalse(policy.showFallbackRevealLayer)
        assertTrue(policy.enableSurfaceToggleInput)
        assertTrue(policy.showControlsLayer)
        assertFalse(policy.subtitleAcceptsInput)
    }

    @Test
    fun `locked or unready playback does not expose reveal input`() {
        val lockedPolicy = resolvePlaybackLayerPolicy(
            isSurfaceReady = true,
            isImmersive = true,
            showControls = false,
            controlsInputLocked = true,
            seekPreviewActive = false,
        )
        val lockedVisiblePolicy = resolvePlaybackLayerPolicy(
            isSurfaceReady = true,
            isImmersive = true,
            showControls = true,
            controlsInputLocked = true,
            seekPreviewActive = false,
        )
        val unreadyPolicy = resolvePlaybackLayerPolicy(
            isSurfaceReady = false,
            isImmersive = true,
            showControls = false,
            controlsInputLocked = false,
            seekPreviewActive = false,
        )
        val seekPreviewPolicy = resolvePlaybackLayerPolicy(
            isSurfaceReady = true,
            isImmersive = true,
            showControls = true,
            controlsInputLocked = false,
            seekPreviewActive = true,
        )

        assertFalse(lockedPolicy.showFallbackRevealLayer)
        assertTrue(lockedPolicy.retainFallbackRevealLayer)
        assertFalse(lockedPolicy.enableSurfaceToggleInput)
        assertFalse(lockedVisiblePolicy.enableSurfaceToggleInput)
        assertFalse(unreadyPolicy.showFallbackRevealLayer)
        assertFalse(unreadyPolicy.retainFallbackRevealLayer)
        assertFalse(unreadyPolicy.enableSurfaceToggleInput)
        assertFalse(seekPreviewPolicy.showFallbackRevealLayer)
        assertFalse(seekPreviewPolicy.enableSurfaceToggleInput)
        assertTrue(seekPreviewPolicy.showControlsLayer)
    }

    @Test
    fun `2D uses only the compose overlay for background toggles`() {
        val hiddenPolicy = resolvePlaybackLayerPolicy(
            isSurfaceReady = true,
            isImmersive = false,
            showControls = false,
            controlsInputLocked = false,
            seekPreviewActive = false,
        )
        val visiblePolicy = resolvePlaybackLayerPolicy(
            isSurfaceReady = true,
            isImmersive = false,
            showControls = true,
            controlsInputLocked = false,
            seekPreviewActive = false,
        )

        assertFalse(hiddenPolicy.showFallbackRevealLayer)
        assertFalse(hiddenPolicy.retainFallbackRevealLayer)
        assertFalse(hiddenPolicy.enableSurfaceToggleInput)
        assertFalse(visiblePolicy.enableSurfaceToggleInput)
        assertTrue(visiblePolicy.showControlsLayer)
    }

    @Test
    fun `immersive subtitle entity is retained while controls take input`() {
        assertTrue(shouldRetainImmersiveSubtitlePanel(subtitlesPresent = true))
        assertTrue(
            shouldShowImmersiveSubtitlePanel(
                subtitlesPresent = true,
                showControls = false,
            )
        )
        assertFalse(
            shouldShowImmersiveSubtitlePanel(
                subtitlesPresent = true,
                showControls = true,
            )
        )
        assertFalse(shouldRetainImmersiveSubtitlePanel(subtitlesPresent = false))
    }

    @Test
    fun `immersive display panel uses fixed scroll viewport height`() {
        assertTrue(
            resolveImmersivePlaybackMenuPanelHeight(
                activeMenu = PlaybackMenu.Display,
                videoFormat = VideoFormat.Format180,
            ) == 620.dp
        )
    }

    @Test
    fun `immersive display panel right edge aligns with playback controls`() {
        assertTrue(
            resolveImmersivePlaybackMenuRightOffset(
                activeMenu = PlaybackMenu.Display,
                controlsPanelWidth = 1460.dp,
                menuPanelWidth = 420.dp,
            ) == 520.dp
        )
        assertTrue(
            resolveImmersivePlaybackMenuRightOffset(
                activeMenu = PlaybackMenu.Speed,
                controlsPanelWidth = 1460.dp,
                menuPanelWidth = 372.dp,
            ) == 0.dp
        )
    }

    @Test
    fun `visible immersive controls enable head follow`() {
        assertTrue(
            shouldEnablePlaybackUiHeadFollow(
                isTwoDimensional = false,
                isImmersive = true,
                showControlsLayer = true,
            )
        )
        assertFalse(
            shouldEnablePlaybackUiHeadFollow(
                isTwoDimensional = false,
                isImmersive = true,
                showControlsLayer = false,
            )
        )
        assertTrue(
            shouldEnablePlaybackUiHeadFollow(
                isTwoDimensional = true,
                isImmersive = false,
                showControlsLayer = true,
            )
        )
    }
}
