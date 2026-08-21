package blackark.app.vr.ui.screens

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionVideoPreviewPolicyTest {

    @Test
    fun `paused preview resumes without restarting before its limit`() {
        assertFalse(
            shouldRestartMotionPreview(
                playbackState = Player.STATE_READY,
                currentPositionMs = 12_000L,
                playbackLimitMs = 60_000L,
            )
        )
    }

    @Test
    fun `completed preview restarts from the beginning`() {
        assertTrue(
            shouldRestartMotionPreview(
                playbackState = Player.STATE_ENDED,
                currentPositionMs = 30_000L,
                playbackLimitMs = null,
            )
        )
        assertTrue(
            shouldRestartMotionPreview(
                playbackState = Player.STATE_READY,
                currentPositionMs = 60_000L,
                playbackLimitMs = 60_000L,
            )
        )
    }

    @Test
    fun `playback limit pauses only before natural completion`() {
        assertTrue(
            hasMotionPreviewReachedLimit(
                playbackState = Player.STATE_READY,
                currentPositionMs = 60_000L,
                playbackLimitMs = 60_000L,
            )
        )
        assertFalse(
            hasMotionPreviewReachedLimit(
                playbackState = Player.STATE_ENDED,
                currentPositionMs = 60_000L,
                playbackLimitMs = 60_000L,
            )
        )
    }
}
