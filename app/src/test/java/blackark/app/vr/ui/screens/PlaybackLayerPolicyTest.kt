package blackark.app.vr.ui.screens

import androidx.compose.ui.unit.dp
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
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
    fun `playback center removes pitch while preserving horizontal direction`() {
        val levelForward = resolveLevelPlaybackForward(
            Vector3(x = 0.3f, y = 0.8f, z = -0.4f),
        )
        val rotation = resolveHeadFollowPanelRotation(levelForward)
        val panelUp = rotation * Vector3.Up

        assertEquals(0f, levelForward.y, 0.0001f)
        assertEquals(1f, levelForward.length, 0.0001f)
        assertEquals(0f, panelUp.x, 0.0001f)
        assertEquals(1f, panelUp.y, 0.0001f)
        assertEquals(0f, panelUp.z, 0.0001f)
    }

    @Test
    fun `spatial release toggles controls without requiring a hit position`() {
        assertTrue(isSpatialToggleClick(InputEvent.Action.UP))
        assertFalse(isSpatialToggleClick(InputEvent.Action.DOWN))
    }

    @Test
    fun `hidden controls enable retained background input`() {
        val policy = resolvePlaybackLayerPolicy(
            isSurfaceReady = true,
            isImmersive = true,
            showControls = false,
            controlsInputLocked = false,
            seekPreviewActive = false,
        )

        assertTrue(policy.retainBackgroundInputLayer)
        assertTrue(policy.showHiddenControlsInputOverlay)
        assertTrue(policy.enableBackgroundToggleInput)
        assertFalse(policy.showControlsLayer)
    }

    @Test
    fun `visible controls retain background toggle input behind the controls`() {
        val policy = resolvePlaybackLayerPolicy(
            isSurfaceReady = true,
            isImmersive = true,
            showControls = true,
            controlsInputLocked = false,
            seekPreviewActive = false,
        )

        assertTrue(policy.retainBackgroundInputLayer)
        assertFalse(policy.showHiddenControlsInputOverlay)
        assertTrue(policy.enableBackgroundToggleInput)
        assertTrue(policy.showControlsLayer)
    }

    @Test
    fun `pending navigation keeps layers but disables new background input`() {
        val policy = resolvePlaybackLayerPolicy(
            isSurfaceReady = true,
            isImmersive = true,
            showControls = true,
            controlsInputLocked = false,
            seekPreviewActive = false,
            navigationExitPending = true,
        )

        assertTrue(policy.retainBackgroundInputLayer)
        assertFalse(policy.showHiddenControlsInputOverlay)
        assertFalse(policy.enableBackgroundToggleInput)
        assertTrue(policy.showControlsLayer)
    }

    @Test
    fun `locked or unready playback retains layer without exposing background input`() {
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

        assertFalse(lockedPolicy.showHiddenControlsInputOverlay)
        assertTrue(lockedPolicy.retainBackgroundInputLayer)
        assertFalse(lockedPolicy.enableBackgroundToggleInput)
        assertFalse(lockedVisiblePolicy.enableBackgroundToggleInput)
        assertFalse(unreadyPolicy.showHiddenControlsInputOverlay)
        assertTrue(unreadyPolicy.retainBackgroundInputLayer)
        assertFalse(unreadyPolicy.enableBackgroundToggleInput)
        assertFalse(seekPreviewPolicy.showHiddenControlsInputOverlay)
        assertFalse(seekPreviewPolicy.enableBackgroundToggleInput)
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

        assertFalse(hiddenPolicy.showHiddenControlsInputOverlay)
        assertFalse(hiddenPolicy.retainBackgroundInputLayer)
        assertFalse(hiddenPolicy.enableBackgroundToggleInput)
        assertFalse(visiblePolicy.enableBackgroundToggleInput)
        assertTrue(visiblePolicy.showControlsLayer)
    }

    @Test
    fun `immersive subtitle entity stays retained but content is hidden behind controls`() {
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
        assertFalse(
            shouldShowImmersiveSubtitlePanel(
                subtitlesPresent = false,
                showControls = false,
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
    fun `immersive elevation raises and lowers the centered view anchor`() {
        val raisedForward = resolveImmersiveVideoRotation(
            videoFormat = VideoFormat.Format180,
            anchorRotation = null,
            elevationDegrees = 15f,
        ) * Vector3.Forward
        val loweredForward = resolveImmersiveVideoRotation(
            videoFormat = VideoFormat.Format360,
            anchorRotation = Quaternion.Identity,
            elevationDegrees = -15f,
        ) * Vector3.Forward

        assertTrue(raisedForward.y > 0f)
        assertTrue(loweredForward.y < 0f)
    }

    @Test
    fun `immersive elevation moves playback UI around the viewer without offsetting its radius`() {
        val anchor = Quaternion.fromAxisAngle(Vector3.Up, 35f)
        val basePose = Pose(
            translation = (anchor * Vector3.Forward) * 1_000f,
            rotation = anchor,
        )

        val elevatedPose = resolveElevatedPlaybackPose(basePose, elevationDegrees = 15f)

        assertTrue(elevatedPose.translation.y > basePose.translation.y)
        assertEquals(
            basePose.translation.lengthSquared,
            elevatedPose.translation.lengthSquared,
            0.1f,
        )
    }

    @Test
    fun `zero immersive elevation leaves the view anchor unchanged`() {
        val forward = resolveImmersiveVideoRotation(
            videoFormat = VideoFormat.Format360,
            anchorRotation = Quaternion.Identity,
            elevationDegrees = 0f,
        ) * Vector3.Forward

        assertEquals(Vector3.Forward.x, forward.x, 0f)
        assertEquals(Vector3.Forward.y, forward.y, 0f)
        assertEquals(Vector3.Forward.z, forward.z, 0f)
    }

}
